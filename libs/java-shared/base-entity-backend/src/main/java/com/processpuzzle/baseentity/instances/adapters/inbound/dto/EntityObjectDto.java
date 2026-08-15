package com.processpuzzle.baseentity.instances.adapters.inbound.dto;

import java.time.Instant;
import java.util.Map;
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
public class EntityObjectDto {
    private UUID id;
    private String entityDefinitionCode;
    private Long version;
    private Map<String, Object> payload;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
}
