package com.processpuzzle.state.usecase.port;

/**
 * The outcome of one {@link TransitionGuard#evaluate(TransitionContext)} call.
 *
 * @param isAllowed whether the transition may proceed as far as this guard is concerned
 * @param reason    populated when {@code isAllowed} is {@code false} — surfaced verbatim as {@code
 *                  TransitionResult.rejectionReason} over the API, so it should be meaningful to
 *                  whoever is looking at the "Approve" button that stayed disabled
 */
public record GuardResult(boolean isAllowed, String reason) {

    public static GuardResult allowed() {
        return new GuardResult(true, null);
    }

    public static GuardResult rejected(String reason) {
        return new GuardResult(false, reason);
    }
}
