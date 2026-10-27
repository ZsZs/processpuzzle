package com.processpuzzle.ai.assistant;

/**
 * What the assistant learns about the caller from the application's security context. The application implements
 * it; the assistant never takes the organization or the role from the request body.
 */
public interface AssistantAccess {

    /** The organization the caller acts for. */
    String currentOrganization();

    /** Whether the caller holds the {@code definition-designer} role. Checked before every draft operation. */
    boolean mayDesign();

    /** The caller as a person reads it, for the audit trail. */
    String principal();

    /** The caller's stable identifier, which owns the caller's design sessions. */
    String subject();
}
