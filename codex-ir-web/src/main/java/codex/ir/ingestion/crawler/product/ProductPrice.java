package codex.ir.ingestion.crawler.product;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed price with amount and optional currency symbol.
 */
public record ProductPrice(BigDecimal amount, Optional<String> currencySymbol) {

    public ProductPrice {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currencySymbol, "currencySymbol must not be null");
        if (currencySymbol.isPresent() && currencySymbol.get().isBlank()) {
            currencySymbol = Optional.empty();
        }
    }

    public ProductPrice(final BigDecimal amount) {
        this(amount, Optional.empty());
    }

    public ProductPrice(final BigDecimal amount, final String currencySymbol) {
        this(amount, Optional.ofNullable(currencySymbol));
    }
}
