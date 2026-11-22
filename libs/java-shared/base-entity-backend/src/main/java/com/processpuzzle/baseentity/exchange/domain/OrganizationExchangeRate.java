package com.processpuzzle.baseentity.exchange.domain;

import com.processpuzzle.baseentity.common.Auditable;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.quantity.UnitCatalog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What one unit of {@code fromCurrency} is worth in {@code toCurrency}, from {@code validFrom} until the pair's next rate
 * starts. A pair has one rate per date; the inverse and cross rates are derived by {@code ExchangeRates}, so a pair is entered
 * once, in whichever direction the organization quotes it.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "base_entity_exchange_rate",
    uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "from_currency", "to_currency", "valid_from"}))
public class OrganizationExchangeRate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false, precision = 38, scale = 20)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Version
    private Long version;

    /** What is wrong with the rate as submitted; empty when it is well formed. */
    public List<Violation> violations() {
        UnitCatalog catalog = UnitCatalog.standard();
        List<Violation> found = new ArrayList<>();
        if (fromCurrency == null || catalog.findCurrency(fromCurrency).isEmpty()) {
            found.add(new Violation("from", "'%s' is not a currency of the catalog".formatted(fromCurrency)));
        }
        if (toCurrency == null || catalog.findCurrency(toCurrency).isEmpty()) {
            found.add(new Violation("to", "'%s' is not a currency of the catalog".formatted(toCurrency)));
        }
        if (fromCurrency != null && fromCurrency.equals(toCurrency)) {
            found.add(new Violation("to", "a rate is between two different currencies"));
        }
        if (rate == null || rate.signum() <= 0) {
            found.add(new Violation("rate", "rate must be a positive decimal"));
        }
        if (validFrom == null) {
            found.add(new Violation("validFrom", "validFrom is required"));
        }
        return found;
    }
}
