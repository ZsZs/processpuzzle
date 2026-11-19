/**
 * Base Calendar: team and resource calendars with timed, all-day and recurring entries, attendees and
 * role-based visibility, from anonymous to a named role.
 *
 * <p>The module knows nothing about entities, workflows, users or the organization record. What it needs
 * from the rest of the platform — does a referenced artifact exist, who can be invited, which time zone the
 * organization works in — it declares as outbound ports in {@code entry :: usecases.outbound}; the host
 * application supplies the adapters. Entry changes leave as
 * {@link com.processpuzzle.shared.event.PlatformEvent}s, never as this module's own event types.
 *
 * <p>Layers inside each part ({@code definition}, {@code entry}) are {@code domain}, {@code usecases} and
 * {@code adapters}; {@code HexagonalLayerRules} verifies their direction at build time, alongside the
 * Modulith module edges.
 *
 * <p>Design: {@code docs/calendar/base-calendar-design-plan.md}.
 */
@ApplicationModule(
        displayName = "Base Calendar",
        allowedDependencies = {"core", "shared"})
package com.processpuzzle.calendar;

import org.springframework.modulith.ApplicationModule;
