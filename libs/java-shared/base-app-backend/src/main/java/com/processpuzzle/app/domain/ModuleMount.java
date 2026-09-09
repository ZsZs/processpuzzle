package com.processpuzzle.app.domain;

/**
 * Mounts a {@link ModuleDefinition}'s routes into an application under {@code basePath}.
 *
 * <p>The one place route structure composes, and it composes exactly one level deep, because a
 * module cannot mount modules. That bound is what keeps an application's routing a short flat list
 * plus a handful of mounts rather than an arbitrarily deep tree.
 *
 * @param moduleKey {@code ModuleDefinition.key} within this organization; a key naming no existing
 *                  module is a validation warning, not an error — modules are loosely coupled, so
 *                  an app may reference one not yet authored
 * @param basePath path prefix every route of the module is registered under
 */
public record ModuleMount(String moduleKey, String basePath) {
}
