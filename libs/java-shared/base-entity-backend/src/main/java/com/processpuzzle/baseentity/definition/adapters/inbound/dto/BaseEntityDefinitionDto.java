package com.processpuzzle.baseentity.definition.adapters.inbound.dto;

import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEntityDefinitionDto {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private Long version;
    private EntityDefinitionStatus status;
    private List<String> componentParents;
    private boolean isEmbedded;
    private UUID organizationId;
    private List<BaseEntityAttributeDto> attributes;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
