package com.processpuzzle.orgadmin.usecases.outbound;

/**
 * A realm role the tenant declares.
 *
 * @param platformManaged true for {@code org-admin} and {@code org-member}, the two created with the
 *                        realm and the only two ProcessPuzzle itself interprets. A client should not
 *                        offer to delete these; everything else is the tenant's own.
 */
public record DirectoryRole(String name, String description, boolean platformManaged) {
}
