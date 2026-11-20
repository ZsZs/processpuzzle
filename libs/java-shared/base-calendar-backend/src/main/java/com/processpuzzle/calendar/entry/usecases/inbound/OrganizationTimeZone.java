package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.usecases.outbound.OrganizationTimeZonePort;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The zone an organization's calendars work in: the organization's own setting when the host supplies one,
 * otherwise {@code base-calendar.default-time-zone} (UTC unless configured).
 *
 * <p>A zone id the host returns that Java does not know is treated as no setting rather than as an error: a
 * mistyped organization record must not take every calendar of the organization down. A mistyped
 * <em>configured</em> default is the operator's own mistake and stops the start-up.
 */
@Service
public class OrganizationTimeZone {

    private final OrganizationTimeZonePort port;
    private final ZoneId fallback;

    public OrganizationTimeZone(ObjectProvider<OrganizationTimeZonePort> port,
                                @Value("${base-calendar.default-time-zone:UTC}") String defaultTimeZone) {
        this.port = port.getIfUnique(() -> orgKey -> Optional.empty());
        try {
            this.fallback = ZoneId.of(defaultTimeZone);
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                    "base-calendar.default-time-zone is not a time zone id: '" + defaultTimeZone + "'", e);
        }
    }

    /** The zone of the organization, never null. */
    public ZoneId of(String orgKey) {
        return port.timeZoneOf(orgKey).flatMap(OrganizationTimeZone::parse).orElse(fallback);
    }

    private static Optional<ZoneId> parse(String id) {
        try {
            return Optional.of(ZoneId.of(id));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
