package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.definition.usecases.inbound.FindCalendarDefinition;
import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The one place that decides what a caller may do with a calendar. Computed afresh on every call from the released
 * definition and the caller's token, so a role change takes effect at once — nothing is copied into an entry.
 *
 * <p><b>It deliberately does not ask {@code OrganizationGuard}.</b> That guard lets an unauthenticated caller
 * through and treats one as holding every role, which is right for the tenant API's open-by-default compromise and
 * wrong for a calendar, where anonymous means "only what is public". Here a caller is a <em>member</em> only when
 * their token is valid and was issued by this organization's realm (or they are platform staff); anybody else —
 * no token, or another tenant's — is treated as anonymous.
 */
@Service
public class ResolveCalendarAccess {

    private final FindCalendarDefinition definitions;
    private final CurrentPrincipal principal;

    public ResolveCalendarAccess(FindCalendarDefinition definitions, CurrentPrincipal principal) {
        this.definitions = definitions;
        this.principal = principal;
    }

    /** A released calendar together with what the caller may do with it. */
    public record ReadableCalendar(CalendarDefinition definition, CalendarAccess access) {
    }

    /**
     * The released calendar when the caller may read it.
     *
     * @throws CalendarNotFoundException when no such calendar is released or the caller may not read it — the same
     *         answer for both, so the existence of a calendar is not revealed
     */
    public ReadableCalendar readable(String orgKey, String calendarKey) {
        return find(orgKey, calendarKey).orElseThrow(CalendarNotFoundException::new);
    }

    /** As {@link #readable}, but empty instead of failing: for a list that leaves out what the caller may not see. */
    public Optional<ReadableCalendar> find(String orgKey, String calendarKey) {
        return definitions.released(orgKey, calendarKey)
                .map(definition -> new ReadableCalendar(definition, of(orgKey, definition)))
                .filter(calendar -> calendar.access().canRead());
    }

    public CalendarAccess of(String orgKey, CalendarDefinition definition) {
        boolean authenticated = principal.isAuthenticated();
        boolean staff = authenticated && principal.isPlatformAdmin();
        boolean member = authenticated && (staff || principal.isMemberOf(orgKey));
        Set<String> roles = member ? principal.authorities() : Set.of();

        boolean canManage = member && (staff || holdsAny(roles, definition.getManageRoles()));
        boolean canRead = canManage || switch (definition.getVisibility()) {
            case PUBLIC -> true;
            case AUTHENTICATED -> member;
            case ROLES -> member && holdsAny(roles, definition.getReadRoles());
        };
        boolean canBook = canRead && member && (staff || definition.getBookRoles().isEmpty() || holdsAny(roles, definition.getBookRoles()));
        boolean fullDetail = canBook || canManage || definition.getPublicDetail() == PublicDetail.FULL;
        return canRead ? new CalendarAccess(true, canBook, canManage, fullDetail) : CalendarAccess.NONE;
    }

    /** Whether the caller holds at least one of {@code required}; an empty list is held by nobody. */
    private static boolean holdsAny(Collection<String> held, Collection<String> required) {
        return required.stream().anyMatch(held::contains);
    }
}
