/**
 * Base Widget's domain. Exposed as the {@code domain} named interface so that a consumer can name a
 * {@link com.processpuzzle.widget.domain.WidgetDefinitionStatus} without reaching for the JPA entity
 * or the repository, which stay internal — the same split base-rule makes for {@code Severity}.
 */
@NamedInterface("domain")
package com.processpuzzle.widget.domain;

import org.springframework.modulith.NamedInterface;
