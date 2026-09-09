package com.processpuzzle.workflow.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One callable operation of a {@link ToolDefinition}. Stored as a JSONB list on
 * {@link ToolDefinition#getOperations()} — operations have no endpoints of their own, they are
 * only ever addressed through the owning tool.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOperation {
    private String id;
    private HttpMethod method;
    private String path;
    private String description;

    /** JSON template for the request body; supports ${expression} PPCL placeholders. */
    private String payloadTemplate;

    /** HTTP status codes treated as successful. Defaults to [200, 201, 204] when null/empty. */
    private List<Integer> expectedStatusCodes;
}
