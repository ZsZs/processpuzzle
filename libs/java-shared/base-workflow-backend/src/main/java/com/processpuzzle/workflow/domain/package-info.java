/**
 * Domain model of the ProcessPuzzle base workflow engine.
 *
 * <p>Exposed as the {@code domain} named interface: a process definition and its instances are what
 * other features observe when a workflow assigns a task or acts on an entity.
 */
@NamedInterface("domain")
package com.processpuzzle.workflow.domain;

import org.springframework.modulith.NamedInterface;
