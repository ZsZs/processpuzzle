package com.processpuzzle.workflow.definition.domain;

import org.springframework.stereotype.Component;

/**
 * Validates an {@code extendsWorkflowId} link before it is persisted. Mirrors
 * {@code RuleExtendsValidator} in base-rule-backend exactly: the whole chain is resolved within
 * one organization, since a workflow cannot extend another organization's workflow.
 */
@Component
public class WorkflowExtendsValidator {

    private static final int MAX_EXTENDS_CHAIN_DEPTH = 100;

    private final WorkflowRepository repository;

    public WorkflowExtendsValidator(WorkflowRepository repository) {
        this.repository = repository;
    }

    public void validate(String orgKey, String ownId, String extendsWorkflowId) {
        if (extendsWorkflowId == null) {
            return;
        }
        if (extendsWorkflowId.equals(ownId)) {
            throw new IllegalArgumentException("Workflow cannot extend itself: " + ownId);
        }

        Workflow parent = repository.findByOrgKeyAndId(orgKey, extendsWorkflowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Workflow '" + ownId + "' extends unknown workflow '" + extendsWorkflowId + "'"));

        String cursor = parent.getExtendsWorkflowId();
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(ownId)) {
                throw new IllegalArgumentException(
                        "Setting '" + ownId + "' to extend '" + extendsWorkflowId
                                + "' would create a cycle");
            }
            if (++depth > MAX_EXTENDS_CHAIN_DEPTH) {
                throw new IllegalStateException(
                        "Extends chain too deep (or already cyclic) starting at '" + cursor + "'");
            }
            cursor = repository.findByOrgKeyAndId(orgKey, cursor)
                    .map(Workflow::getExtendsWorkflowId)
                    .orElse(null);
        }
    }
}
