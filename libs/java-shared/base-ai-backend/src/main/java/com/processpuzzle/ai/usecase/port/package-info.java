/**
 * The outbound ports of the AI feature: {@link com.processpuzzle.ai.usecase.port.VisionServer},
 * {@link com.processpuzzle.ai.usecase.port.MediaStore} and
 * {@link com.processpuzzle.ai.usecase.port.SubjectDirectory}.
 *
 * <p>Exposed as the {@code port} named interface so that the composition root can implement the
 * last two. The vision server adapter is this module's own, since the vision server is
 * infrastructure rather than another feature.
 */
@NamedInterface("port")
package com.processpuzzle.ai.usecase.port;

import org.springframework.modulith.NamedInterface;
