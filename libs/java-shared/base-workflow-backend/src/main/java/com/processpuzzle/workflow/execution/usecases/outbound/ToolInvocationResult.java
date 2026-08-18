package com.processpuzzle.workflow.execution.usecases.outbound;

import java.util.Map;

public record ToolInvocationResult(boolean success, int statusCode, Map<String, Object> body, String error) {
}
