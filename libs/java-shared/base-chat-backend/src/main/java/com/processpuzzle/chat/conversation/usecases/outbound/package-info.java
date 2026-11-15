/**
 * What base-chat needs from outside itself: the host application implements these and names the
 * implementations in its composition root. Exposed as {@code chat :: port} for that reason.
 */
@NamedInterface("port")
package com.processpuzzle.chat.conversation.usecases.outbound;

import org.springframework.modulith.NamedInterface;
