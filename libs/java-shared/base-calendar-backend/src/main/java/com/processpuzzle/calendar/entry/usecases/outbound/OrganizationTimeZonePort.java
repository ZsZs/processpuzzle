package com.processpuzzle.calendar.entry.usecases.outbound;

import java.util.Optional;

/**
 * The time zone an organization works in. A calendar belongs to no zone of its own: all-day boundaries, the
 * default zone of new entries and what the widget displays follow the organization's setting.
 *
 * <p>The setting lives with the organization record, which this module does not know; the host application
 * adapts it. An empty answer means the organization has not set one, and the module falls back to its
 * configured default instead of refusing.
 */
@FunctionalInterface
public interface OrganizationTimeZonePort {

    /**
     * @return the organization's IANA zone id (for example {@code Europe/Budapest}), or empty when it has none
     */
    Optional<String> timeZoneOf(String orgKey);
}
