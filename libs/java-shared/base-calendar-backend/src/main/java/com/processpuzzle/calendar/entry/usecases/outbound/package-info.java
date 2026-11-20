/**
 * What base-calendar needs from outside itself: the host application implements these and names the
 * implementations in its composition root. Exposed as {@code calendar :: port} for that reason.
 */
@NamedInterface("port")
package com.processpuzzle.calendar.entry.usecases.outbound;

import org.springframework.modulith.NamedInterface;
