package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the definition module: describes an entity type at runtime — this is what
 * replaces the previously-static TypeScript entity definitions.
 * <p>
 * {@code componentParents} + {@code isEmbedded} mirror the frontend's own
 * {@code BaseEntityDescriptorOptions} ({@code componentParent}, {@code isEmbedded}) exactly, so
 * the same invariant holds: a definition may not declare {@code isEmbedded = true} without at
 * least one component parent. Enforced by {@link EntityDefinitionValidator}, not here, so it
 * produces a 422 through the usual usecase/adapter path rather than failing at persistence time.
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper = false, of = "id")
@lombok.ToString(exclude = "attributes")
@Entity
@Table(name = "base_entity_definition", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "code"))
public class BaseEntityDefinition extends Auditable {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    /** Stable, immutable-after-creation identifier used across the API and as the FK target from EntityObject in the instances module. */
    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @jakarta.persistence.Version
    private Long version;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EntityDefinitionStatus status = EntityDefinitionStatus.DRAFT;

    /** Entity definition code(s) this one may be nested under. Empty for a stand-alone entity. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> componentParents = new ArrayList<>();

    /**
     * True: this definition's payload is carried inside its parent's payload (no EntityObject
     * rows of its own). False: persisted independently, linked back to its parent via a
     * FOREIGN_KEY attribute value.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean isEmbedded = false;

    private UUID organizationId;

    @jakarta.persistence.OneToMany(mappedBy = "entityDefinition", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.OrderBy("displayOrder ASC")
    @Builder.Default
    private List<BaseEntityAttribute> attributes = new ArrayList<>();

    public boolean isComponent() {
        return !componentParents.isEmpty();
    }

    public void addAttribute(BaseEntityAttribute attribute) {
        attribute.setEntityDefinition(this);
        this.attributes.add(attribute);
    }
}
