package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.StepDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.execution.domain.StepResult;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationPort;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a task's steps in order at completion time. This is deliberately simple: it is not a
 * workflow-within-a-workflow — steps have no branching or retry policy, they just fire in
 * sequence when their owning task is completed.
 *
 * <p><b>Mapping expressions:</b> {@code StepDefinition.inputMapping}/{@code outputMapping} are
 * documented in base-workflow-api.yaml as PPCL expressions / JSONPath, but base-workflow doesn't
 * own an expression engine (PPCL lives in processpuzzle-core / base-rule's GraalJS engine — see
 * the ProcessPuzzle architecture notes on the rule engine). Until a shared evaluation entry point
 * is exposed to feature modules, this executor treats every mapping value as a direct top-level
 * key: {@code inputMapping} values are looked up as-is in the process context, and
 * {@code outputMapping} values are looked up as-is in the tool's JSON response body. Nested-path
 * expressions in either direction are not yet supported and are logged as unresolved (become
 * {@code null}) rather than failing the step outright.
 */
@Component
public class ToolStepExecutor {

    private final ToolDefinitionRepository toolDefinitionRepository;
    private final ToolInvocationPort toolInvocationPort;

    public ToolStepExecutor(ToolDefinitionRepository toolDefinitionRepository, ToolInvocationPort toolInvocationPort) {
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.toolInvocationPort = toolInvocationPort;
    }

    /**
     * Runs every step of {@code steps} that has a {@code toolId} set, mutating {@code context} in
     * place with each step's output mapping. Steps without a {@code toolId} are purely informal
     * guidance and produce a {@link StepResult} with no tool response.
     */
    public List<StepResult> execute(String orgKey, List<StepDefinition> steps, Map<String, Object> context) {
        return steps.stream().map(step -> executeStep(orgKey, step, context)).toList();
    }

    private StepResult executeStep(String orgKey, StepDefinition step, Map<String, Object> context) {
        if (step.getToolId() == null) {
            return StepResult.builder().stepId(step.getId()).completedAt(Instant.now()).build();
        }

        ToolDefinition tool = toolDefinitionRepository.findByOrgKeyAndId(orgKey, step.getToolId()).orElse(null);
        if (tool == null) {
            return StepResult.builder().stepId(step.getId()).completedAt(Instant.now())
                    .error("Tool '" + step.getToolId() + "' not found").build();
        }
        ToolOperation operation = tool.findOperation(step.getToolOperation()).orElse(null);
        if (operation == null) {
            return StepResult.builder().stepId(step.getId()).completedAt(Instant.now())
                    .error("Operation '" + step.getToolOperation() + "' not found on tool '" + step.getToolId() + "'").build();
        }

        Map<String, Object> resolvedPayload = resolveInputs(step.getInputMapping(), context);
        ToolInvocationResult result = toolInvocationPort.invoke(tool, operation, resolvedPayload);

        if (result.success()) {
            applyOutputs(step.getOutputMapping(), result.body(), context);
            return StepResult.builder().stepId(step.getId()).completedAt(Instant.now()).toolResponse(result.body()).build();
        }
        return StepResult.builder().stepId(step.getId()).completedAt(Instant.now()).error(result.error()).build();
    }

    private Map<String, Object> resolveInputs(Map<String, String> inputMapping, Map<String, Object> context) {
        Map<String, Object> resolved = new HashMap<>();
        if (inputMapping != null) {
            inputMapping.forEach((toolParam, contextVar) -> resolved.put(toolParam, context.get(contextVar)));
        }
        return resolved;
    }

    private void applyOutputs(Map<String, String> outputMapping, Map<String, Object> responseBody, Map<String, Object> context) {
        if (outputMapping == null || responseBody == null) {
            return;
        }
        outputMapping.forEach((contextVar, responseKey) -> context.put(contextVar, responseBody.get(responseKey)));
    }
}
