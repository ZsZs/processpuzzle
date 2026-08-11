/**
 * Domain events this module publishes. Exposed as the {@code event} named interface so another
 * feature can subscribe without being granted access to the rest of base-document's internals —
 * which is the whole point of integrating through events rather than calls.
 */
@NamedInterface("event")
package com.processpuzzle.document.domain.event;

import org.springframework.modulith.NamedInterface;
