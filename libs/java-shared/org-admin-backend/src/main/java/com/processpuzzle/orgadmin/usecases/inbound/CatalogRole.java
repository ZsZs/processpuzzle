package com.processpuzzle.orgadmin.usecases.inbound;

/** A role of the organization's catalog together with who manages it. */
public record CatalogRole(String name, String description, boolean platformManaged, RoleOrigin origin) {
}
