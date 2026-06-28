package com.processpuzzle.rule.usecase;

import java.util.List;

public record ImportOutcome(int created, int updated, List<String> errors) {
}
