/**
 * What base-workflow lets another feature ask about a workflow instance without reaching into its execution
 * packages: whether one exists and in which status. Published as {@code workflow :: access}; the host application
 * wires the callers (base-chat's subject adapters) to it, so no feature names another's internals.
 */
@NamedInterface("access")
package com.processpuzzle.workflow.access;

import org.springframework.modulith.NamedInterface;
