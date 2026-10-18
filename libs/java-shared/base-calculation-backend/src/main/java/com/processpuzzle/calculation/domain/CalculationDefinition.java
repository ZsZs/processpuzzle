package com.processpuzzle.calculation.domain;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A calculation of one organization, on one {@link Channel}. The YAML {@link #source} is canonical — it keeps the
 * author's comments and layout; {@link #model} is the same document as JSON, stored next to it for queries, and
 * never read back in its place. {@link #definitionVersion} moves on with every save that changes the source, and is
 * what a run records.
 *
 * <p>The source is {@code LONG32VARCHAR} rather than {@code @Lob}: on PostgreSQL a {@code @Lob String} becomes an
 * {@code oid} column — see base-state's {@code StateMachineDefinition}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"orgKey", "id", "channel"})
@ToString(exclude = {"source", "model", "parsed"})
@Entity
@Table(name = "calculation_definition")
@IdClass(CalculationDefinitionKey.class)
public class CalculationDefinition {

    @Id
    @Column(name = "org_key", nullable = false)
    private String orgKey;

    @Id
    @Column(nullable = false)
    private String id;

    /** The draft a designer edits, or the released row triggers run (see core's {@code docs/definition-release.md}). */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 16, nullable = false)
    @Builder.Default
    private Channel channel = Channel.DRAFT;

    private String name;

    @Column(length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> model = new LinkedHashMap<>();

    /** The proposal's "version": moves on with every save of a changed source. */
    @Column(name = "definition_version", nullable = false)
    @Builder.Default
    private int definitionVersion = 1;

    /** The entity the output writes, denormalized for {@code ManagedAttributesPort}'s per-request question. */
    @Column(name = "output_entity")
    private String outputEntity;

    @Version
    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    @Transient
    private CalculationModel parsed;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** The source, parsed; a stored source always parses, since only a valid one is saved. */
    public CalculationModel parsedModel() {
        if (parsed == null) {
            CalculationParser.Parsed result = CalculationParser.parse(source);
            if (!result.isParsed()) {
                throw new IllegalStateException("Stored calculation '%s' does not parse: %s".formatted(id, result.error()));
            }
            parsed = result.model();
        }
        return parsed;
    }

    /**
     * Takes {@code newSource} and what follows from it — name, description, output entity, the JSON model — and
     * moves the definition version on when the source changed.
     */
    public void applySource(String newSource, CalculationModel newModel) {
        if (source != null && !source.equals(newSource)) {
            definitionVersion++;
        }
        source = newSource;
        parsed = newModel;
        name = newModel.name();
        description = newModel.description();
        outputEntity = newModel.output() == null ? null : newModel.output().entity();
        model = CalculationParser.asMap(newSource);
    }

    /** A new released row with the draft's content: what a calculation's first release creates. */
    public static CalculationDefinition releasedCopyOf(CalculationDefinition draft) {
        CalculationDefinition released = CalculationDefinition.builder().orgKey(draft.orgKey).id(draft.id)
                .channel(Channel.RELEASED).build();
        released.replaceWith(draft);
        return released;
    }

    /** Copies everything an author edits from {@code source}, the definition version included. */
    public void replaceWith(CalculationDefinition other) {
        this.source = other.source;
        this.parsed = null;
        this.name = other.name;
        this.description = other.description;
        this.outputEntity = other.outputEntity;
        this.model = new LinkedHashMap<>(other.model == null ? Map.of() : other.model);
        this.definitionVersion = other.definitionVersion;
    }
}
