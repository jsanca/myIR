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
 * Parses price text into {@link ProductPrice}.
 *
 * <p>Supports Costa Rican colón ({@code ₡}) Latin American number format
 * (dot as thousands separator, comma as decimal) in addition to the
 * US/European comma-thousands dot-decimal format.</p>
 */
public final class ProductPriceParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductPriceParser.class);
    private static final Pattern COLON_NUMBER = Pattern.compile("₡\\s*([\\d.,]+)");
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d[\\d,]*[\\d,]?(?:\\.[\\d]+)?");
    private static final Pattern SKU_LIKE = Pattern.compile("[A-Za-z].*\\d|\\d.*[A-Za-z]");

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

        return parseText(text, currencySymbol);
    }

    /**
     * Parses raw price text such as {@code "₡63,590"} or {@code "$49.99"}.
     *
     * <p>When an explicit currency symbol is present ({@code ₡}), Latin American
     * number formatting rules apply. Otherwise, US/European formatting is assumed
     * (comma = thousands, dot = decimal).</p>
     *
     * @param rawText the price text to parse
     * @return a parsed {@link ProductPrice}, or empty if the text does not
     * represent a recognizable price
     */
    public Optional<ProductPrice> parse(final String rawText) {
        Objects.requireNonNull(rawText, "rawText must not be null");
        final String text = rawText.trim();
        if (text.isBlank()) {
            return Optional.empty();
        }
        return parseText(text, null);
    }

    private Optional<ProductPrice> parseText(final String text, final String currencyHint) {
        if (text.contains("₡")) {
            return parseColon(text, currencyHint != null ? currencyHint : "₡");
        }

        final boolean hasCurrencySymbol = text.contains("$") || text.contains("€")
                || text.contains("£") || text.contains("¥");
        final boolean looksLikeSku = SKU_LIKE.matcher(text).find();

        if (!hasCurrencySymbol && looksLikeSku) {
            return Optional.empty();
        }

        final String numbersOnly = text.replaceAll("[^\\d,.]", "").trim();
        if (numbersOnly.isBlank()) {
            return Optional.empty();
        }

        final Matcher matcher = PRICE_PATTERN.matcher(numbersOnly);
        if (matcher.find()) {
            final String numeric = matcher.group().replace(",", "");
            try {
                return Optional.of(new ProductPrice(new BigDecimal(numeric), currencyHint));
            } catch (final NumberFormatException exception) {
                LOGGER.debug("Could not parse price from: {}", text, exception);
            }
        }

        return Optional.empty();
    }

    private Optional<ProductPrice> parseColon(final String text, final String currencySymbol) {
        final Matcher matcher = COLON_NUMBER.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        final String numberPart = matcher.group(1);
        if (numberPart.isBlank()) {
            return Optional.empty();
        }

        try {
            final BigDecimal amount = parseLatinAmericanNumber(numberPart);
            return Optional.of(new ProductPrice(amount, currencySymbol));
        } catch (final NumberFormatException exception) {
            LOGGER.debug("Could not parse colón price from: {}", text, exception);
            return Optional.empty();
        }
    }

    /**
     * Parses a Latin American formatted number string.
     *
     * <p>Determines which separator acts as decimal by checking trailing
     * character count after the last separator. When a separator has exactly
     * two trailing digits, it is treated as the decimal point. Otherwise,
     * all separators are treated as thousands separators.</p>
     */
    BigDecimal parseLatinAmericanNumber(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new NumberFormatException("empty number string");
        }

        final int lastComma = raw.lastIndexOf(',');
        final int lastDot = raw.lastIndexOf('.');

        if (lastComma >= 0) {
            final int trailing = raw.length() - lastComma - 1;
            if (trailing == 2) {
                final String cleaned = raw.replace(".", "").replace(',', '.');
                return new BigDecimal(cleaned);
            }
        }

        if (lastDot >= 0) {
            final int trailing = raw.length() - lastDot - 1;
            if (trailing == 2) {
                final String cleaned = raw.replace(",", "");
                return new BigDecimal(cleaned);
            }
        }

        final String cleaned = raw.replace(",", "").replace(".", "");
        return new BigDecimal(cleaned);
    }
}
