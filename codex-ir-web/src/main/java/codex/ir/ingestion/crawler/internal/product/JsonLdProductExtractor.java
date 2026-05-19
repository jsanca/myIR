package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductImage;
import codex.ir.ingestion.crawler.product.ProductPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts product data from JSON-LD {@code script[type="application/ld+json"]}
 * blocks using Jackson.
 *
 * <p>Handles direct Product objects, {@code @graph} arrays, and
 * {@code @type} as string or array. Malformed JSON-LD is ignored.</p>
 */
public final class JsonLdProductExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonLdProductExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonLdProductExtractor() {
    }

    public JsonLdProductData extract(final Document doc, final URI baseUri) {
        Objects.requireNonNull(doc, "doc must not be null");
        Objects.requireNonNull(baseUri, "baseUri must not be null");
        final Elements scripts = doc.select("script[type=\"application/ld+json\"]");
        for (final Element script : scripts) {
            final String json = script.html();
            if (json == null || json.isBlank()) {
                continue;
            }
            final JsonLdProductData data = parseScript(json, baseUri);
            if (data.hasAnyField()) {
                return data;
            }
        }
        return JsonLdProductData.EMPTY;
    }

    private JsonLdProductData parseScript(final String json, final URI baseUri) {
        try {
            final JsonNode root = MAPPER.readTree(json);

            final JsonNode productNode = findProductNode(root);
            if (productNode == null) {
                return JsonLdProductData.EMPTY;
            }

            return extractProduct(productNode, baseUri);
        } catch (final Exception exception) {
            LOGGER.debug("Failed to parse JSON-LD script", exception);
            return JsonLdProductData.EMPTY;
        }
    }

    private JsonNode findProductNode(final JsonNode root) {
        if (root.isArray()) {
            for (final JsonNode node : root) {
                final JsonNode product = findProductNode(node);
                if (product != null) {
                    return product;
                }
            }
            return null;
        }

        if (root.has("@graph") && root.get("@graph").isArray()) {
            for (final JsonNode node : root.get("@graph")) {
                final JsonNode product = findProductNode(node);
                if (product != null) {
                    return product;
                }
            }
            return null;
        }

        if (isProductNode(root)) {
            return root;
        }

        return null;
    }

    private boolean isProductNode(final JsonNode node) {
        if (!node.has("@type")) {
            return false;
        }
        final JsonNode typeNode = node.get("@type");
        if (typeNode.isTextual()) {
            return "Product".equals(typeNode.asText());
        }
        if (typeNode.isArray()) {
            for (final JsonNode t : typeNode) {
                if (t.isTextual() && "Product".equals(t.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonLdProductData extractProduct(final JsonNode product, final URI baseUri) {
        final String name = textValue(product, "name");
        final String sku = textValue(product, "sku");
        final String description = textValue(product, "description");
        final ProductPrice regularPrice = extractOffersPrice(product);
        final List<ProductImage> images = extractImages(product, baseUri);

        return new JsonLdProductData(name, sku, description, regularPrice, images);
    }

    private ProductPrice extractOffersPrice(final JsonNode product) {
        final JsonNode offers = product.get("offers");
        if (offers == null) {
            return null;
        }

        final JsonNode offersNode;
        if (offers.isArray() && !offers.isEmpty()) {
            offersNode = offers.get(0);
        } else if (offers.isObject()) {
            offersNode = offers;
        } else {
            return null;
        }

        final String priceStr = textValue(offersNode, "price");
        if (priceStr == null || priceStr.isBlank()) {
            return null;
        }

        try {
            return new ProductPrice(new BigDecimal(priceStr));
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private List<ProductImage> extractImages(final JsonNode product, final URI baseUri) {
        final List<String> urls = new ArrayList<>();
        final JsonNode imageNode = product.get("image");

        if (imageNode == null) {
            return List.of();
        }
        if (imageNode.isTextual()) {
            urls.add(imageNode.asText());
        } else if (imageNode.isArray()) {
            for (final JsonNode img : imageNode) {
                if (img.isTextual()) {
                    urls.add(img.asText());
                } else if (img.isObject() && img.has("url")) {
                    urls.add(img.get("url").asText());
                }
            }
        } else if (imageNode.isObject() && imageNode.has("url")) {
            urls.add(imageNode.get("url").asText());
        }

        final List<ProductImage> images = new ArrayList<>();
        int order = 0;
        for (final String url : urls) {
            try {
                images.add(new ProductImage(baseUri.resolve(url.trim()), "", order++));
            } catch (final Exception ignored) {
            }
        }
        return images;
    }

    private static String textValue(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        final String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }
}
