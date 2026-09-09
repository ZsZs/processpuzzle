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

    @Builder.Default
    private boolean isMultiValued = false;

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

    /**
     * Entity definition code this attribute points at. Required when formControlType is
     * FOREIGN_KEY (parent reference for a non-embedded component) or EMBEDDED_COMPONENTS
     * (child definition carried inline in the payload).
     */
    private String linkedEntityType;

    /** Marks the attribute used as this entity's display title (frontend componentIdentification()). */
    @Builder.Default
    private boolean isLinkToDetails = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Object validationRules;
}
