package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.quantity.UnitCatalog.UnitDefinition;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules a definition must keep for its QUANTITY, MONEY and DIMENSIONS attributes: the shape they share with the
 * other structured controls (valueKind REFERENCE, single-valued, no linked type), that each option is one its control
 * uses, and that the units, currencies and counts it names exist in the standard catalog and agree with each other.
 */
final class MeasureAttributeValidator {

    private static final String LENGTH = "LENGTH";
    private static final int MAX_SCALE = 20;

    private final UnitCatalog catalog = UnitCatalog.standard();
    private final BaseEntityAttribute attribute;
    private final List<Violation> violations;

    private MeasureAttributeValidator(BaseEntityAttribute attribute, List<Violation> violations) {
        this.attribute = attribute;
        this.violations = violations;
    }

    static void validate(BaseEntityAttribute attribute, List<Violation> violations) {
        new MeasureAttributeValidator(attribute, violations).validate();
    }

    private void validate() {
        FormControlType type = attribute.getFormControlType();
        List<String> options = optionsSet();
        if (type != FormControlType.QUANTITY && type != FormControlType.MONEY && type != FormControlType.DIMENSIONS) {
            if (!options.isEmpty()) {
                report("%s only mean something on QUANTITY, MONEY and DIMENSIONS attributes".formatted(String.join(", ", options)));
            }
            return;
        }
        requireStructuredShape(type);
        rejectOptionsOtherControlsUse(type, options);
        switch (type) {
            case QUANTITY -> validateUnits(attribute.getQuantityKind());
            case DIMENSIONS -> {
                validateUnits(LENGTH);
                validateDimensionCount();
            }
            default -> validateCurrencies();
        }
    }

    private void requireStructuredShape(FormControlType type) {
        if (attribute.getValueKind() != ValueKind.REFERENCE || attribute.isMultiValued()
            || (attribute.getLinkedEntityType() != null && !attribute.getLinkedEntityType().isBlank())) {
            report("%s attributes must declare valueKind=REFERENCE, a single value and no linkedEntityType".formatted(type));
        }
    }

    private void rejectOptionsOtherControlsUse(FormControlType type, List<String> options) {
        List<String> own = switch (type) {
            case QUANTITY -> List.of("quantityKind", "allowedUnits", "defaultUnit", "scale");
            case DIMENSIONS -> List.of("allowedUnits", "defaultUnit", "scale", "dimensionCount");
            default -> List.of("allowedCurrencies", "defaultCurrency");
        };
        List<String> foreign = options.stream().filter(option -> !own.contains(option)).toList();
        if (!foreign.isEmpty()) {
            report("%s attributes cannot set %s".formatted(type, String.join(", ", foreign)));
        }
    }

    /** {@code kind} is the kind every unit must measure — LENGTH for dimensions, the attribute's own for a quantity, or null for any. */
    private void validateUnits(String requiredKind) {
        boolean knownKind = requiredKind == null || catalog.kinds().stream().anyMatch(known -> known.code().equals(requiredKind));
        if (!knownKind) {
            report("quantityKind '%s' is not a quantity kind of the standard catalog".formatted(requiredKind));
        }
        String kind = knownKind ? requiredKind : null;
        List<String> allowed = attribute.getAllowedUnits() == null ? List.of() : attribute.getAllowedUnits();
        for (String code : allowed) {
            checkUnit("allowedUnits", code, kind);
        }
        String defaultUnit = attribute.getDefaultUnit();
        if (defaultUnit != null) {
            checkUnit("defaultUnit", defaultUnit, kind);
            if (!allowed.isEmpty() && !allowed.contains(defaultUnit)) {
                report("defaultUnit '%s' is not one of the allowedUnits".formatted(defaultUnit));
            }
        }
        Integer scale = attribute.getScale();
        if (scale != null && (scale < 0 || scale > MAX_SCALE)) {
            report("scale must be between 0 and %d".formatted(MAX_SCALE));
        }
    }

    private void checkUnit(String option, String code, String kind) {
        UnitDefinition unit = catalog.findUnit(code).orElse(null);
        if (unit == null) {
            report("%s names unknown unit '%s'".formatted(option, code));
        } else if (kind != null && !unit.kind().equals(kind)) {
            report("%s '%s' measures %s, not %s".formatted(option, code, unit.kind(), kind));
        }
    }

    private void validateDimensionCount() {
        Integer count = attribute.getDimensionCount();
        if (count != null && count != 2 && count != 3) {
            report("dimensionCount must be 2 or 3");
        }
    }

    private void validateCurrencies() {
        List<String> allowed = attribute.getAllowedCurrencies() == null ? List.of() : attribute.getAllowedCurrencies();
        for (String code : allowed) {
            if (catalog.findCurrency(code).isEmpty()) {
                report("allowedCurrencies names unknown currency '%s'".formatted(code));
            }
        }
        String defaultCurrency = attribute.getDefaultCurrency();
        if (defaultCurrency == null) {
            return;
        }
        if (catalog.findCurrency(defaultCurrency).isEmpty()) {
            report("defaultCurrency names unknown currency '%s'".formatted(defaultCurrency));
        } else if (!allowed.isEmpty() && !allowed.contains(defaultCurrency)) {
            report("defaultCurrency '%s' is not one of the allowedCurrencies".formatted(defaultCurrency));
        }
    }

    /** The names of the measure options this attribute sets. */
    private List<String> optionsSet() {
        List<String> set = new ArrayList<>();
        if (attribute.getQuantityKind() != null) {
            set.add("quantityKind");
        }
        if (attribute.getAllowedUnits() != null && !attribute.getAllowedUnits().isEmpty()) {
            set.add("allowedUnits");
        }
        if (attribute.getDefaultUnit() != null) {
            set.add("defaultUnit");
        }
        if (attribute.getScale() != null) {
            set.add("scale");
        }
        if (attribute.getAllowedCurrencies() != null && !attribute.getAllowedCurrencies().isEmpty()) {
            set.add("allowedCurrencies");
        }
        if (attribute.getDefaultCurrency() != null) {
            set.add("defaultCurrency");
        }
        if (attribute.getDimensionCount() != null) {
            set.add("dimensionCount");
        }
        return set;
    }

    private void report(String message) {
        violations.add(new Violation(attribute.getCode(), message));
    }
}
