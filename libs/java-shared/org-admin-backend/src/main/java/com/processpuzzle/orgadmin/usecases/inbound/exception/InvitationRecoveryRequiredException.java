package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * Creation or compensation could not be confirmed. Reconcile with the identity provider before
 * retrying; the operation id correlates the response with the server's invitation log.
 */
public class InvitationRecoveryRequiredException extends RuntimeException {

    private final String operationId;
    private final String userId;

    public InvitationRecoveryRequiredException(String operationId, String userId, Throwable cause) {
        super("Invitation outcome is unknown. Contact an administrator with operation '" + operationId
                + "' to reconcile the identity provider before retrying.", cause);
        this.operationId = operationId;
        this.userId = userId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getUserId() {
        return userId;
    }
}
