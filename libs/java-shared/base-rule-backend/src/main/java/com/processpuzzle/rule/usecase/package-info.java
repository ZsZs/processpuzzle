/**
 * The use cases of the Base Rule feature, and the module's outward-facing surface.
 *
 * <p>Exposed as the {@code usecase} named interface. Note that {@code propagate} defaults to
 * {@code false}, so the nested {@code engine}, {@code service} and {@code exception} packages remain
 * internal — only the types in this package itself are reachable from other modules.
 */
@NamedInterface("usecase")
package com.processpuzzle.rule.usecase;

import org.springframework.modulith.NamedInterface;
