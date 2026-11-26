package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.assist.AuthoringAssistParticipant;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContextDirectory;
import com.processpuzzle.rule.usecase.assist.ContextDescription;
import com.processpuzzle.rule.usecase.assist.ExpressionChecker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Writes a rule's expression from the user's description (assist task {@code rule.expression}). It takes one parameter,
 * {@code context}: the name of the entity the rule is for, whose attributes the model may refer to and the candidate is
 * checked against. Without an entity it can only check the expression's syntax. Whoever may author metadata in the
 * organization may use it.
 */
@Component
public class RuleExpressionAssist implements AuthoringAssistParticipant {

    public static final String TASK = "rule.expression";
    static final String CONTEXT_PARAMETER = "context";
    private static final String GUIDE = "assist/rule/expression-guide.md";

    private final ObjectProvider<OrganizationGuard> guard;
    private final ObjectProvider<RuleContextDirectory> directory;
    private final ExpressionChecker checker;
    private volatile String guide;

    public RuleExpressionAssist(ObjectProvider<OrganizationGuard> guard, ObjectProvider<RuleContextDirectory> directory,
                                ExpressionChecker checker) {
        this.guard = guard;
        this.directory = directory;
        this.checker = checker;
    }

    @Override
    public String task() {
        return TASK;
    }

    @Override
    public String guide() {
        if (guide == null) {
            try (var in = new ClassPathResource(GUIDE).getInputStream()) {
                guide = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("The rule expression guide is missing from the classpath", e);
            }
        }
        return guide;
    }

    @Override
    public void checkAccess(String orgKey) {
        guard.getObject().requireDesign(orgKey);
    }

    /** @throws IllegalArgumentException for a parameter this task does not take */
    @Override
    public String context(String orgKey, AssistRequest request) {
        return ContextDescription.of(contextOf(orgKey, request));
    }

    @Override
    public List<AuthoringIssue> validate(String orgKey, AssistRequest request, String candidate) {
        return checker.review(orgKey, directory(), request.parameters().get(CONTEXT_PARAMETER), candidate);
    }

    private RuleContextDirectory directory() {
        return directory.getIfAvailable(() -> RuleContextDirectory.UNKNOWN);
    }

    private Optional<RuleContext> contextOf(String orgKey, AssistRequest request) {
        request.parameters().keySet().stream().filter(key -> !CONTEXT_PARAMETER.equals(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("The task '%s' takes no parameter '%s'".formatted(TASK, key));
        });
        String named = request.parameters().get(CONTEXT_PARAMETER);
        if (named == null || named.isBlank()) {
            return Optional.empty();
        }
        return directory().find(orgKey, named);
    }
}
