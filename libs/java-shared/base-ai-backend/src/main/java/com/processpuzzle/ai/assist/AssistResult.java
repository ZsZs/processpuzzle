package com.processpuzzle.ai.assist;

import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.List;

/**
 * The outcome of one assist: the best candidate, what the model says about it, and what is still wrong with it. A
 * candidate with {@link AuthoringIssue.Severity#ERROR} issues is the last one the model produced when the repair rounds
 * ran out, handed over so the user can see it and fix it by hand.
 *
 * @param candidate   the artifact, or empty when the model never produced a readable one
 * @param explanation what the candidate does, in the user's terms
 * @param assumptions what the model had to assume where the description was silent
 * @param issues      the candidate's remaining problems, none when it is clean
 * @param rounds      how many model calls it took
 * @param model       the model that answered
 */
public record AssistResult(String candidate, String explanation, List<String> assumptions, List<AuthoringIssue> issues,
                           int rounds, String model) {
}
