package com.processpuzzle.calendar.entry.domain;

/**
 * What one caller may do with one calendar, decided afresh on every call from the released definition and the
 * caller's token — nothing is cached or stored.
 *
 * @param canRead may see the calendar at all; a caller who may not gets 404, as for a calendar that does not exist
 * @param canBook may book entries and edit their own
 * @param canManage may edit and cancel anybody's entries
 * @param fullDetail sees the content of an entry that is not private, not only when it takes place
 */
public record CalendarAccess(boolean canRead, boolean canBook, boolean canManage, boolean fullDetail) {

    /** The access of a caller who may not even know the calendar exists. */
    public static final CalendarAccess NONE = new CalendarAccess(false, false, false, false);

    /** Whether the caller sees only the times of an entry with this visibility. */
    public boolean seesBusyOnly(boolean organizer, EntryVisibility visibility) {
        if (organizer || canManage) {
            return false;
        }
        return visibility == EntryVisibility.PRIVATE || !fullDetail;
    }

    /** Whether the caller may replace or cancel an entry. */
    public boolean mayEdit(boolean organizer) {
        return canManage || (organizer && canBook);
    }
}
