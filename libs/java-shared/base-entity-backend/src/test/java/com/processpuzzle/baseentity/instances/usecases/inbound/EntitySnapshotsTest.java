package com.processpuzzle.baseentity.instances.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SnapshotView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntitySnapshotsTest {

    private static final UUID BOAT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Map<String, SnapshotView> BOAT_SNAPSHOT = Map.of("boat", new SnapshotView("sailrc-boat", List.of("sailNumber", "name")));
    private static final Map<String, Map<String, Object>> RECORDS = Map.of(BOAT.toString(), Map.of("sailNumber", "G 470", "name", "Blue", "secret", "x"));

    private static Optional<Map<String, Object>> lookup(String type, UUID id) {
        return "sailrc-boat".equals(type) ? Optional.ofNullable(RECORDS.get(id.toString())) : Optional.empty();
    }

    @Test
    void copiesTheNamedFieldsOfTheReferencedRecordUnderTheSnapshotKey() {
        Map<String, Object> written = EntitySnapshots.withSnapshots(Map.of("boat", BOAT.toString()), BOAT_SNAPSHOT, null, EntitySnapshotsTest::lookup);

        assertThat(written).containsEntry("boat", BOAT.toString());
        assertThat(written).containsEntry("boat$snapshot", Map.of("sailNumber", "G 470", "name", "Blue"));
    }

    @Test
    void keepsTheStoredSnapshotWhileTheReferenceIsUnchanged() {
        Map<String, Object> stored = Map.of("boat", BOAT.toString(), "boat$snapshot", Map.of("sailNumber", "G 1", "name", "Old"));

        Map<String, Object> written = EntitySnapshots.withSnapshots(Map.of("boat", BOAT.toString()), BOAT_SNAPSHOT, stored, EntitySnapshotsTest::lookup);

        assertThat(written).containsEntry("boat$snapshot", Map.of("sailNumber", "G 1", "name", "Old"));
    }

    @Test
    void neverTrustsASnapshotTheClientSends() {
        Map<String, Object> forged = Map.of("boat", BOAT.toString(), "boat$snapshot", Map.of("sailNumber", "FAKE"));

        Map<String, Object> written = EntitySnapshots.withSnapshots(forged, BOAT_SNAPSHOT, null, EntitySnapshotsTest::lookup);

        assertThat(written).containsEntry("boat$snapshot", Map.of("sailNumber", "G 470", "name", "Blue"));
    }

    @Test
    void removesTheSnapshotWhenTheReferenceIsCleared() {
        Map<String, Object> cleared = new java.util.HashMap<>();
        cleared.put("boat", null);
        cleared.put("boat$snapshot", Map.of("sailNumber", "G 1"));

        Map<String, Object> written = EntitySnapshots.withSnapshots(cleared, BOAT_SNAPSHOT, null, EntitySnapshotsTest::lookup);

        assertThat(written).doesNotContainKey("boat$snapshot");
    }

    @Test
    void refusesAReferenceTheWriterCannotSee() {
        Map<String, Object> unseen = Map.of("boat", UUID.randomUUID().toString());
        assertThatThrownBy(() -> EntitySnapshots.withSnapshots(unseen, BOAT_SNAPSHOT, null, EntitySnapshotsTest::lookup))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("not visible here");
    }

    @Test
    void refusesAMultiValuedReference() {
        Map<String, Object> many = Map.of("boat", List.of(BOAT.toString()));
        assertThatThrownBy(() -> EntitySnapshots.withSnapshots(many, BOAT_SNAPSHOT, null, EntitySnapshotsTest::lookup))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("takes one reference");
    }

    @Test
    void leavesAPayloadWithoutSnapshotsAsItIs() {
        Map<String, Object> payload = Map.of("boat", BOAT.toString());

        assertThat(EntitySnapshots.withSnapshots(payload, Map.of(), null, EntitySnapshotsTest::lookup)).isSameAs(payload);
    }
}
