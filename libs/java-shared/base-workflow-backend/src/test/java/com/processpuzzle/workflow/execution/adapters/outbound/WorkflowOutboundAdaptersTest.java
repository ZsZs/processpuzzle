package com.processpuzzle.workflow.execution.adapters.outbound;

import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import com.processpuzzle.workflow.definition.domain.AuthType;
import com.processpuzzle.workflow.definition.domain.HttpMethod;
import com.processpuzzle.workflow.definition.domain.ToolAuthConfig;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolOperation;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.outbound.PermitAllRoleMembershipPort;
import com.processpuzzle.workflow.execution.usecases.outbound.RuleCheckResult;
import com.processpuzzle.workflow.execution.usecases.outbound.ToolInvocationResult;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowOutboundAdaptersTest {

    private static final String ORG = "org-1";

    @Test
    void activeWorkflowInstanceExistenceAdapter_checksNonTerminalInstances() {
        WorkflowInstanceRepository repo = mock(WorkflowInstanceRepository.class);
        ActiveWorkflowInstanceExistenceAdapter adapter = new ActiveWorkflowInstanceExistenceAdapter(repo);

        EnumSet<WorkflowInstanceStatus> nonTerminal = EnumSet.of(WorkflowInstanceStatus.ACTIVE, WorkflowInstanceStatus.SUSPENDED);

        when(repo.existsByOrgKeyAndWorkflowIdAndStatusIn(ORG, "proc-1", nonTerminal)).thenReturn(true);
        when(repo.countByOrgKeyAndWorkflowIdAndStatusIn(ORG, "proc-1", nonTerminal)).thenReturn(5L);

        assertThat(adapter.existsActiveInstanceOf(ORG, "proc-1")).isTrue();
        assertThat(adapter.countActiveInstancesOf(ORG, "proc-1")).isEqualTo(5L);
    }

    @Test
    void baseRuleEvaluationAdapter_allBranches() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EvaluateObject> provider = mock(ObjectProvider.class);
        EvaluateObject evaluator = mock(EvaluateObject.class);

        BaseRuleEvaluationAdapter adapter = new BaseRuleEvaluationAdapter(provider);

        // Null / blank rule ID
        assertThat(adapter.evaluate(ORG, null, Map.of())).isEqualTo(RuleCheckResult.ALWAYS_PASSES);
        assertThat(adapter.evaluate(ORG, "   ", Map.of())).isEqualTo(RuleCheckResult.ALWAYS_PASSES);

        // Evaluator not available
        when(provider.getIfAvailable()).thenReturn(null);
        assertThat(adapter.evaluate(ORG, "rule-1", Map.of())).isEqualTo(RuleCheckResult.ALWAYS_PASSES);

        // Evaluator available, rule passes
        when(provider.getIfAvailable()).thenReturn(evaluator);
        when(evaluator.execute(eq(ORG), eq("rule-1"), any()))
                .thenReturn(new EvaluationOutcome(true, List.of()));
        assertThat(adapter.evaluate(ORG, "rule-1", null)).isEqualTo(RuleCheckResult.ALWAYS_PASSES);

        // Evaluator available, rule fails with violations
        RuleViolation violation = mock(RuleViolation.class);
        when(violation.message()).thenReturn("Age must be positive");
        when(evaluator.execute(eq(ORG), eq("rule-1"), any()))
                .thenReturn(new EvaluationOutcome(false, List.of(violation)));

        RuleCheckResult result = adapter.evaluate(ORG, "rule-1", Map.of("age", -5));
        assertThat(result.passed()).isFalse();
        assertThat(result.detail()).isEqualTo("Age must be positive");
    }

    @Test
    void permitAllRoleMembershipPort_alwaysReturnsTrue() {
        PermitAllRoleMembershipPort port = new PermitAllRoleMembershipPort();
        assertThat(port.isMember("org-1", "user-1", "role-admin")).isTrue();
    }

    @Test
    void restToolInvocationAdapter_handlesErrorsAndAuth() {
        Environment env = mock(Environment.class);
        when(env.getProperty("BEARER_SECRET")).thenReturn("my-bearer-token");
        when(env.getProperty("API_SECRET")).thenReturn("my-api-key");
        when(env.getProperty("BASIC_SECRET")).thenReturn("admin:password123");

        RestClient.Builder builder = RestClient.builder();
        RestToolInvocationAdapter adapter = new RestToolInvocationAdapter(builder, env);

        ToolAuthConfig bearerAuth = ToolAuthConfig.builder().type(AuthType.BEARER_TOKEN).secretRef("BEARER_SECRET").build();
        ToolOperation op = ToolOperation.builder()
                .id("op1")
                .method(HttpMethod.POST)
                .path("/api/test")
                .payloadTemplate("{\"name\":\"${name}\",\"missing\":\"${absent}\"}")
                .expectedStatusCodes(List.of(200, 201))
                .build();

        ToolDefinition tool = ToolDefinition.builder()
                .baseUrl("http://localhost:19876") // Invalid/unreachable target to test exception catching
                .auth(bearerAuth)
                .build();

        ToolInvocationResult result = adapter.invoke(tool, op, Map.of("name", "testName"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Tool call failed");

        // Test with other auth types
        ToolAuthConfig apiAuth = ToolAuthConfig.builder().type(AuthType.API_KEY).secretRef("API_SECRET").build();
        tool.setAuth(apiAuth);
        adapter.invoke(tool, op, null);

        ToolAuthConfig basicAuth = ToolAuthConfig.builder().type(AuthType.BASIC).secretRef("BASIC_SECRET").build();
        tool.setAuth(basicAuth);
        adapter.invoke(tool, op, null);

        ToolAuthConfig noSecretAuth = ToolAuthConfig.builder().type(AuthType.BEARER_TOKEN).secretRef("UNSET_SECRET").build();
        tool.setAuth(noSecretAuth);
        adapter.invoke(tool, op, null);

        tool.setAuth(null);
        adapter.invoke(tool, op, null);
    }
}
