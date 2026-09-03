/**
 * Ports the execution use cases depend on but don't implement themselves: rule evaluation (base-rule),
 * role membership (via the host application — see {@code RoleMembershipPort}'s Javadoc for why
 * there's no adapter for it in this module), and tool invocation (plain HTTP).
 *
 * <p>Exposed as the {@code port} named interface, which it had to become the first time a host
 * application actually implemented one of these. {@code RoleMembershipPort}'s Javadoc has always said
 * processpuzzle-testbed-backend is expected to supply the bean, but the package was not published — so the
 * moment the application did supply it, Spring Modulith reported it as reaching into a non-exposed
 * type. Publishing the package is the fix, and it matches how base-app and base-state publish the
 * ports they ask a host to implement.
 */
@NamedInterface("port")
package com.processpuzzle.workflow.execution.usecases.outbound;

import org.springframework.modulith.NamedInterface;
