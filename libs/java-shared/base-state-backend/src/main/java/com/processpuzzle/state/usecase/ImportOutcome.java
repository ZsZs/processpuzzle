package com.processpuzzle.state.usecase;

import java.util.List;

public record ImportOutcome(int created, int updated, List<String> errors) {
}
