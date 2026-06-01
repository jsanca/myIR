package codex.ir.ingestion.crawler.internal.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HtmlTextDecoderTest {

    @Test
    void shouldDecodeDoubleEncodedAmpersand() {
        assertEquals("Menú S&J", HtmlTextDecoder.decode("Menú S&amp;amp;J"));
    }

    @Test
    void shouldDecodeDoubleEncodedGreaterThanWithPrefix() {
        assertEquals("Corporativo > Ejecutivo",
                HtmlTextDecoder.decode("Corporativo &amp;gt; Ejecutivo"));
    }

    @Test
    void shouldDecodeSingleEncodedAmpersand() {
        assertEquals("Bolso & Carteras", HtmlTextDecoder.decode("Bolso &amp; Carteras"));
    }

    @Test
    void shouldKeepNaiveAmpersandUnchanged() {
        assertEquals("S&J", HtmlTextDecoder.decode("S&J"));
    }

    @Test
    void shouldKeepNormalTextUnchanged() {
        assertEquals("Texto normal", HtmlTextDecoder.decode("Texto normal"));
    }

    @Test
    void shouldTrimWhitespaceAfterDecoding() {
        assertEquals("Producto", HtmlTextDecoder.decode("  Producto  "));
    }

    @Test
    void shouldReturnNullForNull() {
        assertNull(HtmlTextDecoder.decode(null));
    }

    @Test
    void shouldReturnBlankForBlankOrWhitespace() {
        assertEquals("", HtmlTextDecoder.decode(""));
        assertEquals("", HtmlTextDecoder.decode("   "));
    }

    @Test
    void shouldDecodeCommonHtmlEntities() {
        assertEquals("a < b > c & d", HtmlTextDecoder.decode("a &lt; b &gt; c &amp; d"));
    }

    @Test
    void shouldDecodeDoubleEncodedLessThan() {
        assertEquals("<", HtmlTextDecoder.decode("&amp;lt;"));
    }

    @Test
    void shouldDecodeDoubleEncodedGreaterThan() {
        assertEquals(">", HtmlTextDecoder.decode("&amp;gt;"));
    }

    @Test
    void shouldPreserveAccents() {
        assertEquals("Menú", HtmlTextDecoder.decode("Menú"));
    }

    @Test
    void shouldPreservePunctuation() {
        assertEquals("Producto: ¡Categoría!", HtmlTextDecoder.decode("Producto: ¡Categoría!"));
    }

    @Test
    void shouldNotModifyAlreadyDecodedText() {
        assertEquals("Normal & text > nothing", HtmlTextDecoder.decode("Normal & text > nothing"));
    }
}
