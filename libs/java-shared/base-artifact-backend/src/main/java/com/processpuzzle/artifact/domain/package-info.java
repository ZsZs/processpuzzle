/**
 * Domain model of ProcessPuzzle base artifact management.
 *
 * <p>Exposed as the {@code domain} named interface — other features attach artifacts to the objects
 * they own, so they need to see what an artifact is without reaching into this module's internals.
 */
@NamedInterface("domain")
package com.processpuzzle.artifact.domain;

import org.springframework.modulith.NamedInterface;
