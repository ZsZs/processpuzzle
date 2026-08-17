package com.processpuzzle.baseentity.instances.domain;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregate root for the instances module. Deliberately NOT normalized into per-attribute-value
 * rows — see RsqlToJsonbSpecificationAdapter for how RSQL filtering works directly against the
 * JSONB payload instead.
 * <p>
 * {@code entityDefinitionCode} is a plain column, not a JPA @ManyToOne — definition metadata is
 * reached only through EntityDefinitionLookupPort, never navigated as a direct JPA association
 * across the module boundary.
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper = false, of = "id")
@lombok.ToString
@Entity
@Table(
    name = "entity_object",
    indexes = @jakarta.persistence.Index(name = "idx_entity_object_definition_code", columnList = "entity_definition_code")
)
public class EntityObject extends Auditable {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_definition_code", nullable = false, updatable = false)
    private String entityDefinitionCode;

    @jakarta.persistence.Version
    private Long version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();
}
