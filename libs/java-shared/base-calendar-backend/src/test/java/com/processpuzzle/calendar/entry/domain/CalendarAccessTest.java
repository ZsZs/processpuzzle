package com.processpuzzle.calendar.entry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CalendarAccessTest {

    private static final CalendarAccess FULL_READER = new CalendarAccess(true, false, false, true);
    private static final CalendarAccess BUSY_READER = new CalendarAccess(true, false, false, false);
    private static final CalendarAccess BOOKER = new CalendarAccess(true, true, false, true);
    private static final CalendarAccess MANAGER = new CalendarAccess(true, true, true, true);

    @Test
    void aPrivateEntryIsBusyToEveryoneButItsOrganizerAndTheManagers() {
        assertThat(FULL_READER.seesBusyOnly(false, EntryVisibility.PRIVATE)).isTrue();
        assertThat(BOOKER.seesBusyOnly(false, EntryVisibility.PRIVATE)).isTrue();
        assertThat(BOOKER.seesBusyOnly(true, EntryVisibility.PRIVATE)).isFalse();
        assertThat(MANAGER.seesBusyOnly(false, EntryVisibility.PRIVATE)).isFalse();
    }

    @Test
    void aPublicEntryIsBusyOnlyToAReaderTheCalendarGivesNoDetail() {
        assertThat(BUSY_READER.seesBusyOnly(false, EntryVisibility.PUBLIC)).isTrue();
        assertThat(FULL_READER.seesBusyOnly(false, EntryVisibility.PUBLIC)).isFalse();
        assertThat(BUSY_READER.seesBusyOnly(true, EntryVisibility.PUBLIC)).as("the organizer always sees their own").isFalse();
    }

    @Test
    void anOrganizerEditsOwnEntriesOnlyWithTheBookingRightAndManagersEditAll() {
        assertThat(BOOKER.mayEdit(true)).isTrue();
        assertThat(BOOKER.mayEdit(false)).isFalse();
        assertThat(FULL_READER.mayEdit(true)).as("organizing is not enough without the booking right").isFalse();
        assertThat(MANAGER.mayEdit(false)).isTrue();
    }

    @Test
    void noAccessMeansNothingAtAll() {
        assertThat(CalendarAccess.NONE.canRead()).isFalse();
        assertThat(CalendarAccess.NONE.mayEdit(true)).isFalse();
    }
}
