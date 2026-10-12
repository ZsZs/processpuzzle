package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PendingChangeTest {

    @Test
    void between_aDraftWithNothingReleased_isAdded() {
        assertThat(PendingChange.between("module", "m", "{}", null))
                .isEqualTo(new PendingChange("module", "m", PendingChange.Change.ADDED, "{}"));
    }

    @Test
    void between_aDifferentDraft_isChanged() {
        assertThat(PendingChange.between("module", "m", "{\"a\":2}", "{\"a\":1}").change()).isEqualTo(PendingChange.Change.CHANGED);
    }

    @Test
    void between_aReleasedRowWithoutDraft_isRemoved() {
        assertThat(PendingChange.between("module", "m", null, "{}"))
                .isEqualTo(new PendingChange("module", "m", PendingChange.Change.REMOVED, null));
    }

    @Test
    void between_equalFingerprintsOrNothing_isNoChange() {
        assertThat(PendingChange.between("module", "m", "{}", "{}")).isNull();
        assertThat(PendingChange.between("module", "m", null, null)).isNull();
    }

    @Test
    void channel_ofRevision_defaultsToTheDraft() {
        assertThat(Channel.ofRevision(null)).isEqualTo(Channel.DRAFT);
        assertThat(Channel.ofRevision("released")).isEqualTo(Channel.RELEASED);
    }
}
