package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;

import java.util.List;

/**
 * The caller's roles and the ones open to self-selection.
 *
 * @param assigned   names of every realm role the caller holds
 * @param selectable the roles the caller may choose for themselves
 */
public record MyRoles(List<String> assigned, List<DirectoryRole> selectable) {
}
