package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.Auditable;
import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
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
 * <p>
 * Every definition has two rows, one per {@link Channel}: the {@code DRAFT} the designer edits and the
 * {@code RELEASED} one the running application reads, which only a release of the organization replaces —
 * see {@link #releasedCopyOf} and {@link #releaseFrom}. They share the {@code code} and nothing else: each
 * row has its own id, and so do its attributes.
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper = false, of = "id")
@lombok.ToString(exclude = "attributes")
@Entity
@Table(name = "base_entity_definition",
    uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"org_key", "code", "channel"}))
public class BaseEntityDefinition extends Auditable {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    /**
     * The organization this definition belongs to. Codes are unique per organization, not globally,
     * so the same starter can be installed into two organizations side by side.
     */
    @Column(name = "org_key", nullable = false, updatable = false)
    private String orgKey;

    /** Stable, immutable-after-creation identifier used across the API and as the FK target from EntityObject in the instances module. */
    @Column(nullable = false, updatable = false)
    private String code;

    /**
     * Which of the definition's two rows this is. The database default lets {@code ddl-auto} add the column to a
     * table that already has rows; {@code EntityDefinitionChannelBackfill} then gives each a released copy.
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @ColumnDefault("'DRAFT'")
    @Column(name = "channel", length = 16, nullable = false, updatable = false)
    @Builder.Default
    private Channel channel = Channel.DRAFT;

    @Column(nullable = false)
    private String name;

    private String description;

    @jakarta.persistence.Version
    private Long version;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EntityDefinitionStatus status = EntityDefinitionStatus.ACTIVE;

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

    /**
     * True: a user can pick one object of this type as the working "context object"; the other
     * features then address its embedded components without naming the object each time.
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    @Builder.Default
    private boolean canContextObject = false;

    /**
     * Who may write the records of this type, when it is a <em>shared</em> type: one of a common, read by every
     * organization of its starter. Null for a tenant type, the only kind outside a common.
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(length = 16)
    private SharingWrite sharingWrite;

    /** Who may read the records of a shared type; set together with {@link #sharingWrite}. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(length = 16)
    private SharingVisibility sharingVisibility;

    /**
     * The states of the type's state machine in which a record of a shared type is visible to the members that do
     * not own it; empty when it is visible in every state. The owner and the common always see it.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> sharingVisibleWhen = new ArrayList<>();

    /**
     * The REFERENCE attribute whose record this type's records follow, for a shared type whose records belong to
     * another record (a booking to its event): owner and visibility are those of the referenced record, and the
     * attribute cannot change once the record exists. Such a type is stored as owner-written and public; what its
     * records may actually be read and written by is read from the referenced record. Null for any other type.
     */
    @Column(length = 100)
    private String sharingFrom;

    /**
     * Natural keys: each entry names the attributes whose values together identify a record, unique among the
     * type's non-retired records. Empty when the type has none.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<List<String>> identifiers = new ArrayList<>();

    /**
     * Form layout: each row lists the attribute codes shown side by side, rows top to bottom. Attributes no row names
     * follow in rows of their own. Empty: one attribute per row.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<List<String>> layout = new ArrayList<>();

    private UUID organizationId;

    @jakarta.persistence.OneToMany(mappedBy = "entityDefinition", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.OrderBy("displayOrder ASC")
    @Builder.Default
    private List<BaseEntityAttribute> attributes = new ArrayList<>();

    private static List<List<String>> copyOf(List<List<String>> identifiers) {
        List<List<String>> copy = new ArrayList<>();
        if (identifiers != null) {
            identifiers.forEach(key -> copy.add(new ArrayList<>(key)));
        }
        return copy;
    }

    /** True for a type of a common whose records every organization of the starter reads. */
    public boolean isShared() {
        return sharingWrite != null;
    }

    public boolean isComponent() {
        return !componentParents.isEmpty();
    }

    public void addAttribute(BaseEntityAttribute attribute) {
        attribute.setEntityDefinition(this);
        this.attributes.add(attribute);
    }

    /**
     * Stores the retired {@link EntityDefinitionStatus#DRAFT}, and no status at all, as {@code ACTIVE}: a release is
     * what keeps a definition off the running application now.
     */
    @PrePersist
    @PreUpdate
    @SuppressWarnings("deprecation")
    void storeLifecycle() {
        if (status == null || status == EntityDefinitionStatus.DRAFT) {
            status = EntityDefinitionStatus.ACTIVE;
        }
    }

    /** A new released row with the draft's content: what a definition's first release creates. */
    public static BaseEntityDefinition releasedCopyOf(BaseEntityDefinition draft) {
        BaseEntityDefinition released = BaseEntityDefinition.builder()
            .orgKey(draft.orgKey)
            .code(draft.code)
            .channel(Channel.RELEASED)
            .organizationId(draft.organizationId)
            .build();
        released.releaseFrom(draft);
        return released;
    }

    /**
     * Replaces this released row's content with the draft's, keeping its own id and version. Attributes are matched
     * by code and updated in place, never the draft's own rows, which stay with the draft. In place rather than
     * cleared and re-added: Hibernate flushes inserts before orphan deletes, so a re-added attribute would meet the
     * row it replaces on the {@code (entity_definition_id, code)} key.
     */
    public void releaseFrom(BaseEntityDefinition draft) {
        this.name = draft.name;
        this.description = draft.description;
        this.status = draft.status;
        this.componentParents = new ArrayList<>(draft.componentParents);
        this.isEmbedded = draft.isEmbedded;
        this.canContextObject = draft.canContextObject;
        this.sharingWrite = draft.sharingWrite;
        this.sharingVisibility = draft.sharingVisibility;
        this.sharingVisibleWhen = draft.sharingVisibleWhen == null ? new ArrayList<>() : new ArrayList<>(draft.sharingVisibleWhen);
        this.sharingFrom = draft.sharingFrom;
        this.identifiers = copyOf(draft.identifiers);
        this.layout = copyOf(draft.layout);
        java.util.Map<String, BaseEntityAttribute> current = new java.util.HashMap<>();
        this.attributes.forEach(attribute -> current.put(attribute.getCode(), attribute));
        java.util.Set<String> kept = new java.util.HashSet<>();
        draft.attributes.forEach(source -> {
            kept.add(source.getCode());
            BaseEntityAttribute target = current.get(source.getCode());
            if (target == null) {
                addAttribute(source.copy());
            } else {
                target.copyFrom(source);
            }
        });
        this.attributes.removeIf(attribute -> !kept.contains(attribute.getCode()));
    }
}
