package com.processpuzzle.baseentity.unit.domain;

import com.processpuzzle.baseentity.common.Auditable;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.quantity.Decimals;
import com.processpuzzle.core.quantity.FundamentalTypeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A unit an organization added to a quantity kind of the standard catalog — a pallet of 500 kg, a bag of 25 kg. Its
 * {@code kind}, {@code factor} and {@code divisor} are fixed once it exists: stored amounts and their normalized
 * {@code baseAmount} are derived from them, so changing one would silently change what stored values mean. It is
 * retired, never deleted, for the same reason.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "base_entity_unit", uniqueConstraints = @UniqueConstraint(columnNames = {"org_key", "code"}))
public class OrganizationUnit extends Auditable {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private String kind;

    @Column(nullable = false, updatable = false, precision = 38, scale = 20)
    private BigDecimal factor;

    @Column(nullable = false, updatable = false, precision = 38, scale = 20)
    private BigDecimal divisor = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UnitStatus status = UnitStatus.ACTIVE;

    @Version
    private Long version;

    /** What is wrong with the unit as submitted, apart from the catalog it joins; empty when it is well formed. */
    public List<Violation> violations() {
        List<Violation> found = new ArrayList<>();
        if (code == null || !CODE.matcher(code).matches()) {
            found.add(new Violation("code", "code must start with a letter and use letters, digits, '_' and '-'"));
        }
        if (symbol == null || symbol.isBlank()) {
            found.add(new Violation("symbol", "symbol is required"));
        }
        if (name == null || name.isBlank()) {
            found.add(new Violation("name", "name is required"));
        }
        if (kind == null || kind.isBlank()) {
            found.add(new Violation("kind", "kind is required"));
        }
        if (factor == null || factor.signum() <= 0) {
            found.add(new Violation("factor", "factor must be a positive decimal"));
        }
        if (divisor == null || divisor.signum() <= 0) {
            found.add(new Violation("divisor", "divisor must be a positive decimal"));
        }
        return found;
    }

    /** Decimal text read as the contract defines it; null when it is not one, for {@link #violations()} to report. */
    public static BigDecimal decimalOrNull(String text) {
        try {
            return Decimals.parse(text);
        } catch (FundamentalTypeException e) {
            return null;
        }
    }
}
