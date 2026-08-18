/**
 * Ports the execution use cases depend on but don't implement themselves: rule evaluation (base-rule),
 * role membership (base-entity, via the host application — see {@code RoleMembershipPort}'s
 * Javadoc for why there's no adapter for it in this module), and tool invocation (plain HTTP).
 */
package com.processpuzzle.workflow.execution.usecases.outbound;
