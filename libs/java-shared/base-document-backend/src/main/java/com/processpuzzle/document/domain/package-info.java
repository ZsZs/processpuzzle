/**
 * Domain model of ProcessPuzzle base document management.
 *
 * <p>Exposed as the {@code domain} named interface — other features attach documents to the objects
 * they own, so they need to see what a document is without reaching into this module's internals.
 */
@NamedInterface("domain")
package com.processpuzzle.document.domain;

import org.springframework.modulith.NamedInterface;
