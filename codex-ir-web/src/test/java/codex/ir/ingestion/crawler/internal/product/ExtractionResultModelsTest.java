package codex.ir.ingestion.crawler.internal.product;

import codex.ir.ingestion.crawler.classifier.ClassifiedUrl;
import codex.ir.ingestion.crawler.classifier.PageClassification;
import codex.ir.ingestion.crawler.classifier.UrlType;
import codex.ir.ingestion.crawler.product.ProductCard;
import codex.ir.ingestion.crawler.product.ProductDetail;
import codex.ir.ingestion.crawler.product.ProductImage;
import codex.ir.ingestion.crawler.product.ProductPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionResultModelsTest {

    private static final CanonicalProductKey KEY = new CanonicalProductKey("/product/test-item");
    private static final URI TEST_URI = URI.create("https://example.com/product/test-item/");
    private static final ProductPrice PRICE = new ProductPrice(new BigDecimal("19.99"));
    private static final ProductImage IMAGE = new ProductImage(URI.create("https://example.com/img.jpg"), "Test Image", 0);

    // ── ProductDetailExtract ──

    @Test
    void productDetailExtractShouldRejectNullCanonicalKey() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(null, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullUrl() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, null, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullName() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, null, Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "   ", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullSku() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", null, Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullBrand() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), null,
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullRegularPrice() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        null, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullSalePrice() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), null, Optional.empty(), Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullAvailability() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), null, Optional.empty(), List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullShortDescription() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), null, List.of(), List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullImages() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), null, List.of()));
    }

    @Test
    void productDetailExtractShouldRejectNullWarnings() {
        assertThrows(NullPointerException.class, () ->
                new ProductDetailExtract(KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), null));
    }

    @Test
    void productDetailExtractShouldDefensivelyCopyImages() {
        final List<ProductImage> mutableImages = new ArrayList<>();
        mutableImages.add(IMAGE);

        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), mutableImages, List.of());

        mutableImages.add(new ProductImage(URI.create("https://example.com/img2.jpg"), "Extra", 1));

        assertEquals(1, extract.images().size());
    }

    @Test
    void productDetailExtractShouldDefensivelyCopyWarnings() {
        final List<ExtractionWarning> mutableWarnings = new ArrayList<>();
        mutableWarnings.add(ExtractionWarning.MISSING_SKU);

        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), mutableWarnings);

        mutableWarnings.add(ExtractionWarning.MISSING_IMAGE);

        assertEquals(1, extract.warnings().size());
    }

    @Test
    void productDetailExtractShouldTrimName() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "  Item Name  ", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());

        assertEquals("Item Name", extract.name());
    }

    @Test
    void productDetailExtractShouldIncludeCanonicalKey() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());

        assertEquals(KEY, extract.canonicalKey());
    }

    @Test
    void productDetailExtractShouldAcceptAllFieldsSet() {
        final List<ProductImage> images = List.of(
                new ProductImage(URI.create("https://example.com/img1.jpg"), "Front", 0),
                new ProductImage(URI.create("https://example.com/img2.jpg"), "Back", 1));

        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Leather Wallet",
                Optional.of("SKU-123"),
                Optional.of("BrandCo"),
                Optional.of(PRICE),
                Optional.of(new ProductPrice(new BigDecimal("14.99"))),
                Optional.of("InStock"),
                Optional.of("A beautiful leather wallet."),
                images,
                List.of());

        assertEquals(KEY, extract.canonicalKey());
        assertEquals(TEST_URI, extract.url());
        assertEquals("Leather Wallet", extract.name());
        assertEquals("SKU-123", extract.sku().get());
        assertEquals("BrandCo", extract.brand().get());
        assertTrue(extract.regularPrice().isPresent());
        assertTrue(extract.salePrice().isPresent());
        assertEquals("InStock", extract.availability().get());
        assertEquals("A beautiful leather wallet.", extract.shortDescription().get());
        assertEquals(2, extract.images().size());
    }

    @Test
    void fromProductDetailShouldMapCorrectly() {
        final List<ProductImage> images = List.of(
                new ProductImage(URI.create("https://example.com/img1.jpg"), "Photo", 0));
        final ProductDetail detail = new ProductDetail(
                TEST_URI, "Leather Wallet",
                Optional.of("SKU-001"),
                Optional.of(PRICE),
                Optional.empty(),
                Optional.of("A wallet"),
                images,
                Optional.of("BrandCo"),
                Optional.of("InStock"));

        final ProductDetailExtract extract = ProductDetailExtract.fromProductDetail(detail);

        assertEquals(CanonicalProductKey.fromUrl(detail.url()), extract.canonicalKey());
        assertEquals(detail.url(), extract.url());
        assertEquals(detail.name(), extract.name());
        assertEquals(detail.sku(), extract.sku());
        assertEquals(detail.brand(), extract.brand());
        assertEquals(detail.regularPrice(), extract.regularPrice());
        assertEquals(detail.salePrice(), extract.salePrice());
        assertEquals(detail.availability(), extract.availability());
        assertEquals(detail.shortDescription(), extract.shortDescription());
        assertEquals(detail.images(), extract.images());
        assertTrue(extract.warnings().isEmpty());
    }

    @Test
    void fromProductDetailShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> ProductDetailExtract.fromProductDetail(null));
    }

    @Test
    void productDetailExtractWithWarningsShouldPreserveFields() {
        final ProductDetailExtract original = new ProductDetailExtract(
                KEY, TEST_URI, "Detail", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(), Optional.of("desc"),
                List.of(IMAGE), List.of());

        final ProductDetailExtract withWarnings = original.withWarnings(
                List.of(ExtractionWarning.MISSING_DESCRIPTION));

        assertEquals(original.canonicalKey(), withWarnings.canonicalKey());
        assertEquals(original.url(), withWarnings.url());
        assertEquals(original.name(), withWarnings.name());
        assertEquals(original.sku(), withWarnings.sku());
        assertEquals(original.images(), withWarnings.images());
        assertEquals(1, withWarnings.warnings().size());
        assertEquals(ExtractionWarning.MISSING_DESCRIPTION, withWarnings.warnings().get(0));
    }

    // ── ProductCardExtract ──

    @Test
    void productCardExtractShouldRejectNullCanonicalKey() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(null, TEST_URI, "Name", Optional.empty(),
                        Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullUrl() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, null, "Name", Optional.empty(),
                        Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullName() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, null, Optional.empty(),
                        Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, "", Optional.empty(),
                        Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullRegularPrice() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, "Name", null,
                        Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullSalePrice() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, "Name", Optional.empty(),
                        null, Optional.empty(), ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullThumbnail() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, "Name", Optional.empty(),
                        Optional.empty(), null, ExtractedCardType.PRODUCT, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullCardType() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, "Name", Optional.empty(),
                        Optional.empty(), Optional.empty(), null, List.of()));
    }

    @Test
    void productCardExtractShouldRejectNullWarnings() {
        assertThrows(NullPointerException.class, () ->
                new ProductCardExtract(KEY, TEST_URI, "Name", Optional.empty(),
                        Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, null));
    }

    @Test
    void productCardExtractShouldIncludeCanonicalKeyAndCardType() {
        final ProductCardExtract extract = new ProductCardExtract(
                KEY, TEST_URI, "Product Name", Optional.empty(),
                Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        assertEquals(KEY, extract.canonicalKey());
        assertEquals(ExtractedCardType.PRODUCT, extract.cardType());
    }

    @Test
    void productCardExtractShouldDefensivelyCopyWarnings() {
        final List<ExtractionWarning> mutableWarnings = new ArrayList<>();
        mutableWarnings.add(ExtractionWarning.MISSING_IMAGE);

        final ProductCardExtract extract = new ProductCardExtract(
                KEY, TEST_URI, "Name", Optional.empty(),
                Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, mutableWarnings);

        mutableWarnings.add(ExtractionWarning.UNKNOWN_CARD_TYPE);

        assertEquals(1, extract.warnings().size());
    }

    @Test
    void fromProductCardShouldSetProductType() {
        final ProductCard card = new ProductCard(
                TEST_URI, "Card Product",
                Optional.of(PRICE),
                Optional.empty(),
                Optional.empty());

        final ProductCardExtract extract = ProductCardExtract.fromProductCard(card);

        assertEquals(CanonicalProductKey.fromUrl(card.url()), extract.canonicalKey());
        assertEquals(ExtractedCardType.PRODUCT, extract.cardType());
        assertEquals(card.url(), extract.url());
        assertEquals(card.name(), extract.name());
        assertEquals(card.regularPrice(), extract.regularPrice());
        assertTrue(extract.warnings().isEmpty());
    }

    @Test
    void fromProductCardShouldRejectNull() {
        assertThrows(NullPointerException.class, () -> ProductCardExtract.fromProductCard(null));
    }

    @Test
    void productCardExtractWithWarningsShouldPreserveFields() {
        final ProductCardExtract original = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.of(PRICE), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final ProductCardExtract withWarnings = original.withWarnings(
                List.of(ExtractionWarning.MISSING_IMAGE));

        assertEquals(original.canonicalKey(), withWarnings.canonicalKey());
        assertEquals(1, withWarnings.warnings().size());
        assertEquals(ExtractionWarning.MISSING_IMAGE, withWarnings.warnings().get(0));
    }

    // ── CategoryExtract ──

    @Test
    void categoryExtractShouldRejectNullUrl() {
        assertThrows(NullPointerException.class, () ->
                new CategoryExtract(null, "Name", ExtractedCardType.CATEGORY, List.of()));
    }

    @Test
    void categoryExtractShouldRejectNullName() {
        assertThrows(NullPointerException.class, () ->
                new CategoryExtract(TEST_URI, null, ExtractedCardType.CATEGORY, List.of()));
    }

    @Test
    void categoryExtractShouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new CategoryExtract(TEST_URI, "   ", ExtractedCardType.CATEGORY, List.of()));
    }

    @Test
    void categoryExtractShouldRejectNullCardType() {
        assertThrows(NullPointerException.class, () ->
                new CategoryExtract(TEST_URI, "Name", null, List.of()));
    }

    @Test
    void categoryExtractShouldRejectNullWarnings() {
        assertThrows(NullPointerException.class, () ->
                new CategoryExtract(TEST_URI, "Name", ExtractedCardType.CATEGORY, null));
    }

    @Test
    void categoryExtractShouldRepresentCategoryCard() {
        final URI catUrl = URI.create("https://example.com/product-category/bags/");
        final CategoryExtract extract = new CategoryExtract(catUrl, "Bags", ExtractedCardType.CATEGORY, List.of());

        assertEquals(catUrl, extract.url());
        assertEquals("Bags", extract.name());
        assertEquals(ExtractedCardType.CATEGORY, extract.cardType());
    }

    @Test
    void categoryExtractCanRepresentNavigationCard() {
        final CategoryExtract extract = new CategoryExtract(
                URI.create("https://example.com/shop/#categories"),
                "Categor\u00EDas", ExtractedCardType.NAVIGATION, List.of());

        assertEquals(ExtractedCardType.NAVIGATION, extract.cardType());
    }

    @Test
    void categoryExtractIsSemanticallySeparateFromProductCardExtract() {
        final ProductCardExtract productExtract = new ProductCardExtract(
                KEY, TEST_URI, "Product", Optional.empty(),
                Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final CategoryExtract categoryExtract = new CategoryExtract(
                URI.create("https://example.com/product-category/bags/"),
                "Bags", ExtractedCardType.CATEGORY, List.of());

        assertEquals(ExtractedCardType.PRODUCT, productExtract.cardType());
        assertEquals(ExtractedCardType.CATEGORY, categoryExtract.cardType());
    }

    @Test
    void categoryExtractShouldDefensivelyCopyWarnings() {
        final List<ExtractionWarning> mutableWarnings = new ArrayList<>();
        mutableWarnings.add(ExtractionWarning.CARD_CLASSIFIED_AS_NAVIGATION);

        final CategoryExtract extract = new CategoryExtract(
                TEST_URI, "Nav", ExtractedCardType.NAVIGATION, mutableWarnings);

        mutableWarnings.add(ExtractionWarning.UNKNOWN_CARD_TYPE);

        assertEquals(1, extract.warnings().size());
    }

    @Test
    void categoryExtractWithWarningsShouldPreserveFields() {
        final CategoryExtract original = new CategoryExtract(
                TEST_URI, "Cat", ExtractedCardType.CATEGORY, List.of());

        final CategoryExtract withWarnings = original.withWarnings(
                List.of(ExtractionWarning.UNKNOWN_CARD_TYPE));

        assertEquals(original.url(), withWarnings.url());
        assertEquals(original.name(), withWarnings.name());
        assertEquals(original.cardType(), withWarnings.cardType());
        assertEquals(1, withWarnings.warnings().size());
    }

    // ── PageExtractionResult ──

    @Test
    void pageExtractionResultShouldRejectNullPageUri() {
        final PageClassification classification = classification(TEST_URI);
        assertThrows(NullPointerException.class, () ->
                new PageExtractionResult(null, classification, Optional.empty(), List.of(), List.of()));
    }

    @Test
    void pageExtractionResultShouldRejectNullPageClassification() {
        assertThrows(NullPointerException.class, () ->
                new PageExtractionResult(TEST_URI, null, Optional.empty(), List.of(), List.of()));
    }

    @Test
    void pageExtractionResultShouldRejectNullProductDetail() {
        final PageClassification classification = classification(TEST_URI);
        assertThrows(NullPointerException.class, () ->
                new PageExtractionResult(TEST_URI, classification, null, List.of(), List.of()));
    }

    @Test
    void pageExtractionResultShouldRejectNullProductCards() {
        final PageClassification classification = classification(TEST_URI);
        assertThrows(NullPointerException.class, () ->
                new PageExtractionResult(TEST_URI, classification, Optional.empty(), null, List.of()));
    }

    @Test
    void pageExtractionResultShouldRejectNullCategories() {
        final PageClassification classification = classification(TEST_URI);
        assertThrows(NullPointerException.class, () ->
                new PageExtractionResult(TEST_URI, classification, Optional.empty(), List.of(), null));
    }

    @Test
    void pageExtractionResultShouldDefensivelyCopyProductCards() {
        final PageClassification classification = classification(TEST_URI);
        final List<ProductCardExtract> mutableCards = new ArrayList<>();
        mutableCards.add(new ProductCardExtract(KEY, TEST_URI, "Card", Optional.empty(),
                Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));

        final PageExtractionResult result = new PageExtractionResult(
                TEST_URI, classification, Optional.empty(), mutableCards, List.of());

        mutableCards.add(new ProductCardExtract(
                CanonicalProductKey.fromUrl(URI.create("https://example.com/product/extra/")),
                URI.create("https://example.com/product/extra/"),
                "Extra", Optional.empty(), Optional.empty(), Optional.empty(), ExtractedCardType.PRODUCT, List.of()));

        assertEquals(1, result.productCards().size());
    }

    @Test
    void pageExtractionResultShouldDefensivelyCopyCategories() {
        final PageClassification classification = classification(TEST_URI);
        final List<CategoryExtract> mutableCategories = new ArrayList<>();
        mutableCategories.add(new CategoryExtract(
                URI.create("https://example.com/product-category/bags/"),
                "Bags", ExtractedCardType.CATEGORY, List.of()));

        final PageExtractionResult result = new PageExtractionResult(
                TEST_URI, classification, Optional.empty(), List.of(), mutableCategories);

        mutableCategories.add(new CategoryExtract(
                URI.create("https://example.com/product-category/shoes/"),
                "Shoes", ExtractedCardType.CATEGORY, List.of()));

        assertEquals(1, result.categories().size());
    }

    @Test
    void pageExtractionResultEmptyShouldHaveNoExtracts() {
        final PageClassification classification = classification(TEST_URI);
        final PageExtractionResult result = PageExtractionResult.empty(TEST_URI, classification);

        assertEquals(TEST_URI, result.pageUri());
        assertEquals(classification, result.pageClassification());
        assertTrue(result.productDetail().isEmpty());
        assertTrue(result.productCards().isEmpty());
        assertTrue(result.categories().isEmpty());
    }

    @Test
    void pageExtractionResultEmptyShouldRejectNullPageUri() {
        final PageClassification classification = classification(TEST_URI);
        assertThrows(NullPointerException.class, () -> PageExtractionResult.empty(null, classification));
    }

    @Test
    void pageExtractionResultEmptyShouldRejectNullClassification() {
        assertThrows(NullPointerException.class, () -> PageExtractionResult.empty(TEST_URI, null));
    }

    @Test
    void pageExtractionResultBuilderShouldProduceValidResult() {
        final PageClassification classification = classification(TEST_URI);
        final ProductDetailExtract detail = new ProductDetailExtract(
                KEY, TEST_URI, "Item", Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());
        final ProductCardExtract card = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());
        final CategoryExtract category = new CategoryExtract(
                URI.create("https://example.com/product-category/bags/"),
                "Bags", ExtractedCardType.CATEGORY, List.of());

        final PageExtractionResult result = PageExtractionResult.builder(TEST_URI, classification)
                .productDetail(detail)
                .productCards(List.of(card))
                .categories(List.of(category))
                .build();

        assertEquals(TEST_URI, result.pageUri());
        assertEquals(classification, result.pageClassification());
        assertTrue(result.productDetail().isPresent());
        assertEquals(detail, result.productDetail().get());
        assertEquals(1, result.productCards().size());
        assertEquals(card, result.productCards().get(0));
        assertEquals(1, result.categories().size());
        assertEquals(category, result.categories().get(0));
    }

    @Test
    void pageExtractionResultBuilderWithoutProductDetailShouldHaveEmptyOptional() {
        final PageClassification classification = classification(TEST_URI);
        final PageExtractionResult result = PageExtractionResult.builder(TEST_URI, classification).build();

        assertEquals(TEST_URI, result.pageUri());
        assertTrue(result.productDetail().isEmpty());
        assertTrue(result.productCards().isEmpty());
        assertTrue(result.categories().isEmpty());
    }

    @Test
    void pageExtractionResultBuilderShouldRejectNullProductDetail() {
        final PageClassification classification = classification(TEST_URI);
        final PageExtractionResult.Builder builder = PageExtractionResult.builder(TEST_URI, classification);
        assertThrows(NullPointerException.class, () -> builder.productDetail(null));
    }

    @Test
    void pageExtractionResultBuilderShouldRejectNullProductCards() {
        final PageClassification classification = classification(TEST_URI);
        final PageExtractionResult.Builder builder = PageExtractionResult.builder(TEST_URI, classification);
        assertThrows(NullPointerException.class, () -> builder.productCards(null));
    }

    @Test
    void pageExtractionResultBuilderShouldRejectNullCategories() {
        final PageClassification classification = classification(TEST_URI);
        final PageExtractionResult.Builder builder = PageExtractionResult.builder(TEST_URI, classification);
        assertThrows(NullPointerException.class, () -> builder.categories(null));
    }

    @Test
    void pageExtractionResultShouldNotThrowWhenAllRequiredFieldsSet() {
        final PageClassification classification = classification(TEST_URI);
        assertDoesNotThrow(() -> new PageExtractionResult(
                TEST_URI, classification, Optional.empty(), List.of(), List.of()));
    }

    // ── ExtractionWarning rules ──

    @Test
    void detailWithMissingSkuShouldWarn() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.empty(), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("Description"), List.of(IMAGE), List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductDetail(extract);
        assertTrue(warnings.contains(ExtractionWarning.MISSING_SKU));
    }

    @Test
    void detailWithSkuShouldNotWarn() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.of("SKU-001"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("Description"), List.of(IMAGE), List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductDetail(extract);
        assertFalse(warnings.contains(ExtractionWarning.MISSING_SKU));
    }

    @Test
    void detailWithNoImagesShouldWarn() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("Description"), List.of(), List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductDetail(extract);
        assertTrue(warnings.contains(ExtractionWarning.MISSING_IMAGE));
    }

    @Test
    void detailWithImagesShouldNotWarnForImage() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("Description"), List.of(IMAGE), List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductDetail(extract);
        assertFalse(warnings.contains(ExtractionWarning.MISSING_IMAGE));
    }

    @Test
    void detailWithNoDescriptionShouldWarn() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of(IMAGE), List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductDetail(extract);
        assertTrue(warnings.contains(ExtractionWarning.MISSING_DESCRIPTION));
    }

    @Test
    void detailWithSalePriceHigherThanRegularShouldBeSuspicious() {
        final ProductDetailExtract extract = new ProductDetailExtract(
                KEY, TEST_URI, "Name", Optional.of("SKU"), Optional.empty(),
                Optional.of(new ProductPrice(new BigDecimal("10.00"))),
                Optional.of(new ProductPrice(new BigDecimal("20.00"))),
                Optional.empty(),
                Optional.of("Description"), List.of(IMAGE), List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductDetail(extract);
        assertTrue(warnings.contains(ExtractionWarning.PRICE_LOOKS_SUSPICIOUS));
    }

    @Test
    void cardWithNoThumbnailShouldWarn() {
        final ProductCardExtract extract = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductCard(extract);
        assertTrue(warnings.contains(ExtractionWarning.MISSING_IMAGE));
    }

    @Test
    void cardWithUnknownTypeShouldWarn() {
        final ProductCardExtract extract = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.UNKNOWN, List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forProductCard(extract);
        assertTrue(warnings.contains(ExtractionWarning.UNKNOWN_CARD_TYPE));
    }

    @Test
    void categoryWithNavigationTypeShouldWarn() {
        final CategoryExtract extract = new CategoryExtract(
                TEST_URI, "Nav", ExtractedCardType.NAVIGATION, List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forCategory(extract);
        assertTrue(warnings.contains(ExtractionWarning.CARD_CLASSIFIED_AS_NAVIGATION));
    }

    @Test
    void categoryWithUnknownTypeShouldWarn() {
        final CategoryExtract extract = new CategoryExtract(
                TEST_URI, "Unknown", ExtractedCardType.UNKNOWN, List.of());

        final List<ExtractionWarning> warnings = ExtractionWarnings.forCategory(extract);
        assertTrue(warnings.contains(ExtractionWarning.UNKNOWN_CARD_TYPE));
    }

    // ── ExtractionQualitySummary ──

    @Test
    void summaryShouldCountPagesProcessed() {
        final List<PageExtractionResult> results = List.of(
                emptyPage(URI.create("https://example.com/page1/"), UrlType.PRODUCT),
                emptyPage(URI.create("https://example.com/page2/"), UrlType.CATEGORY));

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(results);

        assertEquals(2, summary.pagesProcessed());
    }

    @Test
    void summaryShouldCountProductDetailPages() {
        final ProductDetailExtract detail = new ProductDetailExtract(
                KEY, TEST_URI, "Item", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("desc"), List.of(), List.of());

        final PageExtractionResult page = PageExtractionResult.builder(TEST_URI, classification(TEST_URI, UrlType.PRODUCT))
                .productDetail(detail)
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.productDetailPages());
    }

    @Test
    void summaryShouldCountCategoryPages() {
        final List<PageExtractionResult> results = List.of(
                emptyPage(URI.create("https://example.com/cat1/"), UrlType.CATEGORY),
                emptyPage(URI.create("https://example.com/cat2/"), UrlType.CATEGORY),
                emptyPage(URI.create("https://example.com/prod1/"), UrlType.PRODUCT));

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(results);

        assertEquals(2, summary.categoryPages());
    }

    @Test
    void summaryShouldCountProductCardsFound() {
        final ProductCardExtract card1 = new ProductCardExtract(
                KEY, TEST_URI, "Card1", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());
        final ProductCardExtract card2 = new ProductCardExtract(
                CanonicalProductKey.fromUrl(URI.create("https://example.com/product/item2/")),
                URI.create("https://example.com/product/item2/"),
                "Card2", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final PageExtractionResult page = PageExtractionResult.builder(
                TEST_URI, classification(TEST_URI, UrlType.CATEGORY))
                .productCards(List.of(card1, card2))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(2, summary.productCardsFound());
    }

    @Test
    void summaryShouldCountCategoryCardsFound() {
        final CategoryExtract cat = new CategoryExtract(
                URI.create("https://example.com/category/bags/"),
                "Bags", ExtractedCardType.CATEGORY, List.of());

        final PageExtractionResult page = PageExtractionResult.builder(
                TEST_URI, classification(TEST_URI, UrlType.CATEGORY))
                .categories(List.of(cat))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.categoryCardsFound());
    }

    @Test
    void summaryShouldCountNavigationCardsIgnored() {
        final CategoryExtract nav = new CategoryExtract(
                URI.create("https://example.com/#categories"),
                "Categorias", ExtractedCardType.NAVIGATION, List.of());

        final PageExtractionResult page = PageExtractionResult.builder(
                TEST_URI, classification(TEST_URI, UrlType.CATEGORY))
                .categories(List.of(nav))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.navigationCardsIgnored());
        assertEquals(0, summary.categoryCardsFound());
    }

    @Test
    void summaryShouldCountUniqueProductUrls() {
        final ProductCardExtract card1 = new ProductCardExtract(
                KEY, TEST_URI, "Card1", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());
        final ProductCardExtract card2 = new ProductCardExtract(
                KEY, TEST_URI, "Card2", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final PageExtractionResult page = PageExtractionResult.builder(
                TEST_URI, classification(TEST_URI, UrlType.CATEGORY))
                .productCards(List.of(card1, card2))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.uniqueProductUrls());
    }

    @Test
    void summaryShouldCountProductsWithDetail() {
        final ProductDetailExtract detail = new ProductDetailExtract(
                KEY, TEST_URI, "Item", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("desc"), List.of(), List.of());

        final PageExtractionResult page = PageExtractionResult.builder(TEST_URI, classification(TEST_URI, UrlType.PRODUCT))
                .productDetail(detail)
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.productsWithDetail());
    }

    @Test
    void summaryShouldCountProductsOnlyFromCards() {
        final ProductCardExtract card = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final PageExtractionResult page = PageExtractionResult.builder(
                TEST_URI, classification(TEST_URI, UrlType.CATEGORY))
                .productCards(List.of(card))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.productsOnlyFromCards());
    }

    @Test
    void summaryShouldNotCountProductInCardsIfAlsoInDetail() {
        final ProductDetailExtract detail = new ProductDetailExtract(
                KEY, TEST_URI, "Item", Optional.of("SKU"), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("desc"), List.of(), List.of());
        final ProductCardExtract card = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of());

        final PageExtractionResult page = PageExtractionResult.builder(TEST_URI, classification(TEST_URI, UrlType.PRODUCT))
                .productDetail(detail)
                .productCards(List.of(card))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.productsWithDetail());
        assertEquals(0, summary.productsOnlyFromCards());
    }

    @Test
    void summaryShouldCountMissingImageWarnings() {
        final ProductCardExtract card = new ProductCardExtract(
                KEY, TEST_URI, "Card", Optional.empty(), Optional.empty(),
                Optional.empty(), ExtractedCardType.PRODUCT, List.of(ExtractionWarning.MISSING_IMAGE));

        final PageExtractionResult page = PageExtractionResult.builder(
                TEST_URI, classification(TEST_URI, UrlType.CATEGORY))
                .productCards(List.of(card))
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.missingImageWarnings());
    }

    @Test
    void summaryShouldCountMissingSkuWarnings() {
        final ProductDetailExtract detail = new ProductDetailExtract(
                KEY, TEST_URI, "Item", Optional.empty(), Optional.empty(),
                Optional.of(PRICE), Optional.empty(), Optional.empty(),
                Optional.of("desc"), List.of(), List.of(ExtractionWarning.MISSING_SKU));

        final PageExtractionResult page = PageExtractionResult.builder(TEST_URI, classification(TEST_URI, UrlType.PRODUCT))
                .productDetail(detail)
                .build();

        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of(page));

        assertEquals(1, summary.missingSkuWarnings());
    }

    @Test
    void summaryShouldHandleEmptyInput() {
        final ExtractionQualitySummary summary = ExtractionSummarizer.summarize(List.of());

        assertEquals(0, summary.pagesProcessed());
        assertEquals(0, summary.productDetailPages());
        assertEquals(0, summary.categoryPages());
        assertEquals(0, summary.productCardsFound());
        assertEquals(0, summary.categoryCardsFound());
        assertEquals(0, summary.navigationCardsIgnored());
        assertEquals(0, summary.uniqueProductUrls());
        assertEquals(0, summary.productsWithDetail());
        assertEquals(0, summary.productsOnlyFromCards());
    }

    @Test
    void summaryShouldRejectNullInput() {
        assertThrows(NullPointerException.class, () -> ExtractionSummarizer.summarize(null));
    }

    @Test
    void summaryShouldHaveNegativeFieldValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExtractionQualitySummary(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void summaryEmptyShouldHaveAllZeros() {
        final ExtractionQualitySummary summary = ExtractionQualitySummary.empty();
        assertEquals(0, summary.pagesProcessed());
        assertEquals(0, summary.uniqueProductUrls());
    }

    // ── Helpers ──

    private static PageClassification classification(final URI uri) {
        return classification(uri, UrlType.CATEGORY);
    }

    private static PageClassification classification(final URI uri, final UrlType type) {
        return new PageClassification(uri, type, new ClassifiedUrl(uri, type), false, false);
    }

    private static PageExtractionResult emptyPage(final URI uri, final UrlType type) {
        return new PageExtractionResult(
                uri,
                new PageClassification(uri, type, new ClassifiedUrl(uri, type), false, false),
                Optional.empty(), List.of(), List.of());
    }
}
