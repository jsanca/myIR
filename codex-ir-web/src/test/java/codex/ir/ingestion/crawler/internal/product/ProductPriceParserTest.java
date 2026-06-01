package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.product.ProductPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPriceParserTest {

    private final ProductPriceParser parser = new ProductPriceParser();

    @Test
    void shouldParseColonWithCommaThousands() {
        final Optional<ProductPrice> result = parser.parse("₡63,590");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("63590"), result.get().amount());
        assertTrue(result.get().currencySymbol().isPresent());
        assertEquals("₡", result.get().currencySymbol().get());
    }

    @Test
    void shouldParseColonWithDotThousands() {
        final Optional<ProductPrice> result = parser.parse("₡63.590");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("63590"), result.get().amount());
    }

    @Test
    void shouldParseColonFourDigitWithComma() {
        final Optional<ProductPrice> result = parser.parse("₡5,995");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5995"), result.get().amount());
    }

    @Test
    void shouldParseColonFourDigitWithDot() {
        final Optional<ProductPrice> result = parser.parse("₡5.995");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5995"), result.get().amount());
    }

    @Test
    void shouldParseColonWithDotThousandsAndCommaDecimal() {
        final Optional<ProductPrice> result = parser.parse("₡5.005,80");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5005.80"), result.get().amount());
    }

    @Test
    void shouldParseColonWithCommaThousandsAndDotDecimal() {
        final Optional<ProductPrice> result = parser.parse("₡5,005.80");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5005.80"), result.get().amount());
    }

    @Test
    void shouldParseColonWithSpace() {
        final Optional<ProductPrice> result = parser.parse("₡ 145,490");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("145490"), result.get().amount());
    }

    @Test
    void shouldParseColon144ConcatenatingWithSurroundingSkus() {
        final Optional<ProductPrice> result = parser.parse("BOT-719 ₡81.690");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("81690"), result.get().amount());
    }

    @Test
    void shouldParseColonWithSkuAndNameNearby() {
        final Optional<ProductPrice> result = parser.parse("ZH-9100 Casual Sneaker ₡52.990");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("52990"), result.get().amount());
    }

    @Test
    void shouldParseColonWithCents() {
        final Optional<ProductPrice> result = parser.parse("Crema hidratante ₡5.005,80");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5005.80"), result.get().amount());
    }

    @Test
    void shouldParseGrasaCase() {
        final Optional<ProductPrice> result = parser.parse("GRASA ₡5.005,50");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5005.50"), result.get().amount());
    }

    @Test
    void shouldParseColon145490() {
        final Optional<ProductPrice> result = parser.parse("₡145,490");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("145490"), result.get().amount());
    }

    @Test
    void shouldParseColon5995() {
        final Optional<ProductPrice> result = parser.parse("₡5,995");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("5995"), result.get().amount());
    }

    @Test
    void shouldRejectSkuOnlyBot719() {
        final Optional<ProductPrice> result = parser.parse("BOT-719");
        assertTrue(result.isEmpty(), "SKU-only text should not produce a price");
    }

    @Test
    void shouldRejectSkuOnlyZh9100() {
        final Optional<ProductPrice> result = parser.parse("ZH-9100");
        assertTrue(result.isEmpty(), "SKU-only text should not produce a price");
    }

    @Test
    void shouldRejectProductNameWithNumbers() {
        final Optional<ProductPrice> result = parser.parse("100-annete");
        assertTrue(result.isEmpty(), "product name with numbers should not produce a price");
    }

    @Test
    void shouldRejectCodeOnlyF1708() {
        final Optional<ProductPrice> result = parser.parse("F-17-08");
        assertTrue(result.isEmpty(), "code-only text should not produce a price");
    }

    @Test
    void shouldRejectPlainProductName() {
        final Optional<ProductPrice> result = parser.parse("plain product name with no currency");
        assertTrue(result.isEmpty(), "plain text without currency should not produce a price");
    }

    @Test
    void shouldRejectBlankInput() {
        assertTrue(parser.parse((String) "").isEmpty());
        assertTrue(parser.parse((String) "   ").isEmpty());
    }

    @Test
    void shouldStillParseDollarPrices() {
        final Optional<ProductPrice> result = parser.parse("$49.99");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("49.99"), result.get().amount());
    }

    @Test
    void shouldParsePlainNumbersWithoutCurrency() {
        final Optional<ProductPrice> result = parser.parse("150.00");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("150.00"), result.get().amount());
    }

    @Test
    void shouldParseNumberWithCommaThousands() {
        final Optional<ProductPrice> result = parser.parse("1,250.99");
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("1250.99"), result.get().amount());
    }

    @Test
    void shouldParseLatinAmericanNumberCommaDecimal() {
        assertNotNull(parser.parseLatinAmericanNumber("5.005,80"));
    }

    @Test
    void parseLatinAmericanNumberCommaDecimal() {
        assertEquals(new BigDecimal("5005.80"),
                parser.parseLatinAmericanNumber("5.005,80"));
    }

    @Test
    void parseLatinAmericanNumberDotDecimal() {
        assertEquals(new BigDecimal("5005.80"),
                parser.parseLatinAmericanNumber("5,005.80"));
    }

    @Test
    void parseLatinAmericanNumberCommaThousands() {
        assertEquals(new BigDecimal("63590"),
                parser.parseLatinAmericanNumber("63,590"));
    }

    @Test
    void parseLatinAmericanNumberDotThousands() {
        assertEquals(new BigDecimal("63590"),
                parser.parseLatinAmericanNumber("63.590"));
    }

    @Test
    void parseLatinAmericanNumberFourDigitWithComma() {
        assertEquals(new BigDecimal("5995"),
                parser.parseLatinAmericanNumber("5,995"));
    }

    @Test
    void parseLatinAmericanNumberFourDigitWithDot() {
        assertEquals(new BigDecimal("5995"),
                parser.parseLatinAmericanNumber("5.995"));
    }

    @Test
    void parseLatinAmericanNumberNoSeparators() {
        assertEquals(new BigDecimal("63590"),
                parser.parseLatinAmericanNumber("63590"));
    }
}
