/**
 * What base-rule needs to know about the entities its rules are written for, as outbound ports the host application
 * implements — over base-entity in the testbed, see its {@code composition} package. Published as the {@code port}
 * named interface so an adapter may implement them; this module names no other feature.
 */
@NamedInterface("port")
package com.processpuzzle.rule.port;

import org.springframework.modulith.NamedInterface;
