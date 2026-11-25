package com.processpuzzle.baseentity.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "entityDefinition")
@Entity
@Table(
    name = "base_entity_attribute",
    uniqueConstraints = @UniqueConstraint(columnNames = {"entity_definition_id", "code"})
)
public class BaseEntityAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_definition_id", nullable = false)
    private BaseEntityDefinition entityDefinition;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValueKind valueKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormControlType formControlType;

    /** How many values the attribute holds; null means a single value. */
    @Enumerated(EnumType.STRING)
    private Multiplicity multiplicity;

    /** Upper bound of the {@code x} multiplicities; null for the others. */
    private Integer maxOccurs;

    @Builder.Default
    private boolean required = false;

    /**
     * When true, an expression index is generated for this attribute's JSON path so RSQL range
     * queries hit an index instead of a sequential scan. Only meaningful for top-level,
     * non-array attributes today.
     */
    @Builder.Default
    private boolean indexed = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Object defaultValue;

    /** Only meaningful when valueKind = ENUM. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> enumValues;

    /** Only meaningful when valueKind = DATE or DATE_TIME. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private DateFormat dateFormat;

    /**
     * Entity definition code this attribute points at. Required when formControlType is
     * FOREIGN_KEY (parent reference for a non-embedded component) or EMBEDDED_COMPONENTS
     * (child definition carried inline in the payload).
     */
    private String linkedEntityType;

    /**
     * For a REFERENCE to a linked type: the attributes of the referenced record copied, under the key
     * {@code <attribute>$snapshot}, into the referencing record when it is written. Empty means no snapshot.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @jakarta.persistence.Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> snapshotFields = new java.util.ArrayList<>();

    /** Marks the attribute used as this entity's display title (frontend componentIdentification()). */
    @Builder.Default
    private boolean isLinkToDetails = false;

    /**
     * Predefined avatar icon or image URL fallback for AVATAR form control.
     */
    private String defaultAvatar;

    /**
     * The list column hugs its content instead of filling the remaining width. The column default lets
     * {@code ddl-auto: update} add the column to a table that already has rows.
     */
    @Builder.Default
    @Column(columnDefinition = "boolean not null default false")
    private boolean autosizeColumn = false;

    /** ADDRESS only: show the address on a map. The column default lets {@code ddl-auto: update} add it to rows. */
    @Builder.Default
    @Column(columnDefinition = "boolean not null default false")
    private boolean showMap = false;

    /** ADDRESS only: who draws the map; null means OpenStreetMap. */
    @Enumerated(EnumType.STRING)
    private MapProvider mapProvider;

    /** ADDRESS only: the ISO 3166-1 alpha-2 code a new address starts with. */
    private String defaultCountry;

    /** QUANTITY only: the quantity kind whose units are offered when {@link #allowedUnits} names none. */
    private String quantityKind;

    /** QUANTITY and DIMENSIONS only: the unit codes a value may use; empty or null means every unit of the kind. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> allowedUnits;

    /** QUANTITY and DIMENSIONS only: the unit a new value starts in. */
    private String defaultUnit;

    /** QUANTITY and DIMENSIONS only: the most decimal places an amount may have. */
    private Integer scale;

    /** MONEY only: the ISO 4217 codes a value may use; empty or null means every currency. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> allowedCurrencies;

    /** MONEY only: the currency a new amount starts in. */
    private String defaultCurrency;

    /** DIMENSIONS only: 2 or 3 axes; null means 2. */
    private Integer dimensionCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Object validationRules;

    /** True when the upper bound of {@link #multiplicity} exceeds 1. */
    public boolean isMultiValued() {
        return Multiplicity.isMultiValued(multiplicity, maxOccurs);
    }

    /**
     * The same attribute as a new row, not yet attached to a definition: how a release copies a draft's attributes
     * onto the released definition. Every field but the id and the owner — see {@link #copyFrom}, where a new field must be added.
     */
    public BaseEntityAttribute copy() {
        BaseEntityAttribute copy = BaseEntityAttribute.builder().code(code).build();
        copy.copyFrom(this);
        return copy;
    }

    /** Takes every authored field of {@code source}, leaving this row's id and owner as they are. */
    public void copyFrom(BaseEntityAttribute source) {
        this.code = source.code;
        this.name = source.name;
        this.displayOrder = source.displayOrder;
        this.valueKind = source.valueKind;
        this.formControlType = source.formControlType;
        this.multiplicity = source.multiplicity;
        this.maxOccurs = source.maxOccurs;
        this.required = source.required;
        this.indexed = source.indexed;
        this.defaultValue = source.defaultValue;
        this.enumValues = source.enumValues == null ? null : new java.util.ArrayList<>(source.enumValues);
        this.dateFormat = source.dateFormat;
        this.linkedEntityType = source.linkedEntityType;
        this.snapshotFields = source.snapshotFields == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(source.snapshotFields);
        this.isLinkToDetails = source.isLinkToDetails;
        this.defaultAvatar = source.defaultAvatar;
        this.autosizeColumn = source.autosizeColumn;
        this.showMap = source.showMap;
        this.mapProvider = source.mapProvider;
        this.defaultCountry = source.defaultCountry;
        this.quantityKind = source.quantityKind;
        this.allowedUnits = source.allowedUnits == null ? null : new java.util.ArrayList<>(source.allowedUnits);
        this.defaultUnit = source.defaultUnit;
        this.scale = source.scale;
        this.allowedCurrencies = source.allowedCurrencies == null ? null : new java.util.ArrayList<>(source.allowedCurrencies);
        this.defaultCurrency = source.defaultCurrency;
        this.dimensionCount = source.dimensionCount;
        this.validationRules = source.validationRules;
    }
}
