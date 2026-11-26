package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.InstanceDataProbe;
import com.processpuzzle.core.definition.PendingRecordQueue;
import com.processpuzzle.core.definition.RecordImportParticipant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link DefinitionImportParticipant} beans the deploying application contributes, one per kind,
 * and its {@link InstanceDataProbe}s. Which kinds a deployment can import, and which instance data it
 * can hold, depends only on which feature libraries it hosts.
 */
@Component
public class ImportParticipants {

    private final Map<String, DefinitionImportParticipant> byKind = new LinkedHashMap<>();
    private final List<InstanceDataProbe> probes;
    private final RecordImportParticipant records;
    private final PendingRecordQueue pendingRecords;

    @Autowired
    public ImportParticipants(ObjectProvider<DefinitionImportParticipant> participants,
                              ObjectProvider<InstanceDataProbe> probes,
                              ObjectProvider<RecordImportParticipant> records,
                              ObjectProvider<PendingRecordQueue> pendingRecords) {
        this(participants.stream().toList(), probes.stream().toList(), records == null ? null : records.getIfAvailable(),
                pendingRecords == null ? null : pendingRecords.getIfAvailable());
    }

    /** For a caller whose records load at once, such as a test or a deployment that cannot release. */
    public ImportParticipants(ObjectProvider<DefinitionImportParticipant> participants,
                              ObjectProvider<InstanceDataProbe> probes,
                              ObjectProvider<RecordImportParticipant> records) {
        this(participants, probes, records, null);
    }

    /** For a caller that has no record loader, such as a test or a deployment without seed records. */
    public ImportParticipants(ObjectProvider<DefinitionImportParticipant> participants,
                              ObjectProvider<InstanceDataProbe> probes) {
        this(participants.stream().toList(), probes.stream().toList(), null, null);
    }

    private ImportParticipants(List<DefinitionImportParticipant> participants, List<InstanceDataProbe> probes,
                               RecordImportParticipant records, PendingRecordQueue pendingRecords) {
        participants.stream()
                .sorted(Comparator.comparingInt(DefinitionImportParticipant::order))
                .forEach(this::add);
        this.probes = List.copyOf(probes);
        this.records = records;
        this.pendingRecords = pendingRecords;
    }

    /** For a caller that holds the participants already, such as a test. */
    public static ImportParticipants of(List<DefinitionImportParticipant> participants) {
        return of(participants, List.of());
    }

    public static ImportParticipants of(List<DefinitionImportParticipant> participants, List<InstanceDataProbe> probes) {
        return new ImportParticipants(participants, probes, null, null);
    }

    /** As {@link #of(List)}, with a record loader and, when not null, the queue records wait in for the release. */
    public static ImportParticipants of(List<DefinitionImportParticipant> participants, RecordImportParticipant records,
                                        PendingRecordQueue pendingRecords) {
        return new ImportParticipants(participants, List.of(), records, pendingRecords);
    }

    public Optional<DefinitionImportParticipant> forKind(String kind) {
        return Optional.ofNullable(byKind.get(kind));
    }

    /** The loader of seed records, if the deployment hosts one. */
    public Optional<RecordImportParticipant> records() {
        return Optional.ofNullable(records);
    }

    /**
     * Where seed records wait for the organization's next release, if the deployment can release. The definitions an
     * install or an upgrade writes are drafts, and a record is validated against its released type, so records that
     * come with drafts are queued rather than loaded.
     */
    public Optional<PendingRecordQueue> pendingRecords() {
        return Optional.ofNullable(pendingRecords);
    }

    /** In import order. */
    public Collection<DefinitionImportParticipant> inOrder() {
        return byKind.values();
    }

    /** In reverse import order, the order definitions are removed in: dependents first. */
    public List<DefinitionImportParticipant> inRemovalOrder() {
        return List.copyOf(byKind.values()).reversed();
    }

    public List<InstanceDataProbe> probes() {
        return probes;
    }

    private void add(DefinitionImportParticipant participant) {
        DefinitionImportParticipant previous = byKind.putIfAbsent(participant.kind(), participant);
        if (previous != null) {
            throw new IllegalStateException("Two import participants for kind '" + participant.kind() + "': "
                    + previous.getClass().getName() + " and " + participant.getClass().getName());
        }
    }
}
