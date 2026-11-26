package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.core.definition.RecordImportParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.release.domain.OrgPendingRecords;
import com.processpuzzle.release.domain.OrgPendingRecordsRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Loads the records files a starter's install or upgrade queued, once the release has released every kind — so the
 * records are validated against, and stored under, the definitions just released. Writes through the release's
 * transaction; a refusal is answered as problems, and the release then throws, which rolls everything back.
 */
@Component
public class PendingRecordsLoader {

    /** The kind a refused record is reported under. */
    public static final String RECORD = "record";

    private final OrgPendingRecordsRepository repository;
    private final ObjectProvider<RecordImportParticipant> records;

    PendingRecordsLoader(OrgPendingRecordsRepository repository, ObjectProvider<RecordImportParticipant> records) {
        this.repository = repository;
        this.records = records;
    }

    boolean hasPending(String orgKey) {
        return repository.existsByOrgKeyAndReleaseNumberIsNull(orgKey);
    }

    /** Loads every pending file in order and marks it as loaded by release {@code number}; why not, or empty. */
    List<ReleaseProblem> load(String orgKey, long number) {
        List<OrgPendingRecords> pending = repository.findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(orgKey);
        if (pending.isEmpty()) {
            return List.of();
        }
        RecordImportParticipant loader = records.getIfAvailable();
        if (loader == null) {
            return List.of(ReleaseProblem.error(RECORD, pending.getFirst().getFileName(), "release.records.unsupported",
                "Seed records are pending, but this application cannot load records."));
        }
        List<ReleaseProblem> problems = new ArrayList<>();
        for (OrgPendingRecords file : pending) {
            ParticipantResult result = loader.load(orgKey, file.getFileName(), file.yaml());
            if (result.isRejected()) {
                result.errors().forEach(message ->
                    problems.add(ReleaseProblem.error(RECORD, file.getFileName(), "release.records.refused", message)));
                return problems;
            }
            file.loadedIn(number, result.items().size());
        }
        return problems;
    }
}
