package com.processpuzzle.ai.assist;

import java.util.List;

/** The reply the model is asked to give, as JSON: the candidate with its explanation and assumptions. */
public record AssistDraft(String candidate, String explanation, List<String> assumptions) {
}
