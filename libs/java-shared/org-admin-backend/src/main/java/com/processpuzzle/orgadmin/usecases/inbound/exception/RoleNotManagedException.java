package com.processpuzzle.orgadmin.usecases.inbound.exception;

/**
 * The role belongs to the platform or to a workflow role definition, so it cannot be changed or deleted
 * here. Surfaced as 403.
 */
public class RoleNotManagedException extends RuntimeException {
    public RoleNotManagedException(String name, String managedBy) {
        super("Role '" + name + "' is managed by " + managedBy + " and cannot be changed here.");
    }
}
