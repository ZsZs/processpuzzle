package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns a stored entry into what the current caller may see of it, and what they may do with it. The one place that
 * applies the busy rule and the invitation rules, so a single entry, a list and a window cannot disagree about them.
 */
@Component
public class EntryViews {

    private final CallerMembership membership;

    public EntryViews(CallerMembership membership) {
        this.membership = membership;
    }

    public EntryView of(CalendarEntry entry, CalendarAccess access) {
        String organization = entry.getOrgKey();
        String subject = membership.memberSubject(organization).orElse(null);
        Set<String> roles = membership.memberRoles(organization);
        boolean mine = entry.isOrganizedBy(subject);
        boolean invited = subject != null && entry.isInvited(subject, roles);
        boolean canRespond = subject != null && entry.invitationOf(subject).isPresent();
        return new EntryView(entry, access.seesBusyOnly(mine, invited, entry.getVisibility()), mine, access.mayEdit(mine), invited, canRespond);
    }
}
