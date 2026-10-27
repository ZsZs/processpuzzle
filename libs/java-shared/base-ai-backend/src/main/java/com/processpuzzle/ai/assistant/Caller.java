package com.processpuzzle.ai.assistant;

/**
 * Who is running a turn, read once from {@link AssistantAccess} when the turn starts and carried through it. The
 * tools take this rather than the access itself: a streamed turn executes tool calls on whatever thread the model
 * client answers on, where the request's thread-local security context is not available.
 */
public record Caller(String organization, String subject, String principal, boolean mayDesign) {

    static Caller of(AssistantAccess access) {
        return new Caller(access.currentOrganization(), access.subject(), access.principal(), access.mayDesign());
    }
}
