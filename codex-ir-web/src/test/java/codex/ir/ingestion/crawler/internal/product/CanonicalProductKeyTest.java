package codex.ir.ingestion.crawler.internal.product;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalProductKeyTest {

    @Test
    void trailingSlashAndNoSlashShouldProduceSameKey() {
        final var withSlash = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/"));
        final var withoutSlash = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet"));

        assertEquals(withSlash, withoutSlash);
        assertEquals("/producto/sole-wallet", withSlash.value());
    }

    @Test
    void fragmentShouldBeRemoved() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/#categories"));

        assertEquals("/producto/sole-wallet", key.value());
    }

    @Test
    void fragmentAndTrailingSlashShouldProduceSameKey() {
        final var withFragment = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/#categories"));
        final var plain = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet"));

        assertEquals(withFragment, plain);
    }

    @Test
    void queryParametersShouldBeRemoved() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/?foo=bar"));

        assertEquals("/producto/sole-wallet", key.value());
    }

    @Test
    void queryAndFragmentShouldBothBeRemoved() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/?foo=bar#section"));

        assertEquals("/producto/sole-wallet", key.value());
    }

    @Test
    void differentProductSlugsShouldProduceDifferentKeys() {
        final var wallet = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet"));
        final var another = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/another-wallet"));

        assertNotEquals(wallet, another);
        assertEquals("/producto/sole-wallet", wallet.value());
        assertEquals("/producto/another-wallet", another.value());
    }

    @Test
    void allNormalizedVariantsShouldBeEqual() {
        final var a = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/"));
        final var b = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet"));
        final var c = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/?foo=bar"));
        final var d = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/producto/sole-wallet/#categories"));

        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals(a, d);
    }

    @Test
    void shouldPreservePercentEncoding() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://example.com/producto/espa%C3%B1ol"));

        assertEquals("/producto/espa%C3%B1ol", key.value());
    }

    @Test
    void shouldThrowForNullUrl() {
        assertThrows(NullPointerException.class,
                () -> CanonicalProductKey.fromUrl(null));
    }

    @Test
    void shouldThrowForRootPath() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalProductKey.fromUrl(URI.create("https://example.com/")));
    }

    @Test
    void shouldThrowForBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalProductKey.fromUrl(URI.create("https://example.com")));
    }

    @Test
    void deeplyNestedPathShouldBePreserved() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/tienda/hombre/bolsos/cuero"));

        assertEquals("/tienda/hombre/bolsos/cuero", key.value());
    }

    @Test
    void deeplyNestedWithTrailingSlashShouldBeNormalized() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://syjleathers.com/tienda/hombre/bolsos/cuero/"));

        assertEquals("/tienda/hombre/bolsos/cuero", key.value());
    }

    @Test
    void shouldPreservePathCase() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://example.com/Producto/Sole-Wallet"));

        assertEquals("/Producto/Sole-Wallet", key.value());
    }

    @Test
    void singlePathSegmentShouldBePreserved() {
        final var key = CanonicalProductKey.fromUrl(
                URI.create("https://example.com/wallet"));

        assertEquals("/wallet", key.value());
    }
}
