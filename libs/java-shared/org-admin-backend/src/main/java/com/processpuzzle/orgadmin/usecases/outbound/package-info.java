/**
 * The one port this module depends on and does not implement itself: the tenant's user directory.
 *
 * <p>Not a named interface — nothing outside this module implements or calls it. The adapter that
 * does implement it lives in {@code adapters.outbound} of this same module.
 */
package com.processpuzzle.orgadmin.usecases.outbound;
