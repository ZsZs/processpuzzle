package com.processpuzzle.core.assist;

import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.List;

/**
 * One thing the platform's AI can write for a user from a description, such as a rule's expression. The feature that
 * owns the target implements it: it knows the target's language, what a candidate may refer to and how to check one.
 * The AI module owns the loop around it (model, quota, repair rounds, audit) and knows a task by its name only.
 *
 * <p>A task never writes anything. It returns a candidate for the user to accept into a form, which saves through the
 * ordinary validators. Everything a participant returns reaches the model, so {@link #context} carries metadata only,
 * never the organization's object data.
 */
public interface AuthoringAssistParticipant {

    /** The task's name, unique across the platform, such as {@code rule.expression}. */
    String task();

    /** The language reference and worked examples the model reads before every request of this task. */
    String guide();

    /**
     * Refuses the caller before any model call, when they may not edit the target in the organization.
     *
     * @throws RuntimeException the feature's usual access refusal
     */
    void checkAccess(String orgKey);

    /** What the candidate may refer to, as text for the model: the attributes of an entity, units, existing names. */
    String context(String orgKey, AssistRequest request);

    /**
     * Checks one candidate. {@link AuthoringIssue.Severity#ERROR} issues send the candidate back to the model for a
     * repair; warnings are passed on to the user.
     */
    List<AuthoringIssue> validate(String orgKey, AssistRequest request, String candidate);
}
