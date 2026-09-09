package com.processpuzzle.state.adapter.inbound.dto;

import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.Transition;

import java.util.List;

/**
 * One state machine as it appears in an import/export YAML file. {@code entityName} carries no
 * {@code orgKey} — that is what makes an export from one organization importable into another,
 * the same discipline {@code RuleYamlEntry} follows. {@link State} and {@link Transition} are
 * reused directly rather than mirrored field-by-field: both are already plain, JSON-friendly
 * value records with no persistence concerns of their own, unlike {@code RuleDefinition}, whose
 * YAML entry exists precisely to avoid exposing JPA-entity setters to Jackson.
 */
public record StateMachineYamlEntry(
        String entityName,
        String name,
        String description,
        String stateAttributeKey,
        String initialStateKey,
        List<State> states,
        List<Transition> transitions
) {
}
