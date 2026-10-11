package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * The user directory could not be reached, or refused the operation. Surfaced as 503.
 *
 * <p>This does not imply that a write was atomic or unapplied. Invitations use this exception after
 * confirmed compensation; an unknown invitation outcome instead raises
 * {@link InvitationRecoveryRequiredException}. Other writes must be reconciled before retrying.
 */
public class DirectoryUnavailableException extends RuntimeException {

    public DirectoryUnavailableException(String message) {
        super(message);
    }

    public DirectoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
