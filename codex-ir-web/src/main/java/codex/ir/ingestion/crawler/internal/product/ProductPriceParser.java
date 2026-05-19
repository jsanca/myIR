package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductPrice;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses WooCommerce price elements into {@link ProductPrice}.
 *
 * <p>Limitations: comma as thousands separator, period as decimal.
 * No international multi-currency support yet.</p>
 */
public final class ProductPriceParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductPriceParser.class);
    private static final Pattern PRICE_PATTERN = Pattern.compile("[\\d,]+(?:\\.\\d+)?");

    public ProductPriceParser() {
    }

    public Optional<ProductPrice> parse(final Element priceElement) {
        Objects.requireNonNull(priceElement, "priceElement must not be null");
        final String text = priceElement.text().trim();
        if (text.isBlank()) {
            return Optional.empty();
        }

        String currencySymbol = null;
        final Element symbolEl = priceElement.selectFirst(".woocommerce-Price-currencySymbol");
        if (symbolEl != null) {
            currencySymbol = symbolEl.text().trim();
        }

        final String numbersOnly = text.replaceAll("[^\\d,.]", "").trim();
        final Matcher matcher = PRICE_PATTERN.matcher(numbersOnly);
        if (matcher.find()) {
            final String numeric = matcher.group().replace(",", "");
            try {
                return Optional.of(new ProductPrice(new BigDecimal(numeric), currencySymbol));
            } catch (final NumberFormatException exception) {
                LOGGER.debug("Could not parse price from: {}", text, exception);
            }
        }

        return Optional.empty();
    }
}
