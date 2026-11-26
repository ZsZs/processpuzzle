package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.PendingRecordQueue;
import com.processpuzzle.release.domain.OrgPendingRecords;
import com.processpuzzle.release.domain.OrgPendingRecordsRepository;
import org.springframework.stereotype.Component;

/** {@link PendingRecordQueue} over the organization's pending records files, which {@link ReleaseOrganization} loads. */
@Component
public class QueuePendingRecords implements PendingRecordQueue {

    private final OrgPendingRecordsRepository repository;

    QueuePendingRecords(OrgPendingRecordsRepository repository) {
        this.repository = repository;
    }

    @Override
    public void enqueue(String orgKey, String fileName, byte[] yaml) {
        int next = repository.findByOrgKeyAndReleaseNumberIsNullOrderBySequenceNoAsc(orgKey).size();
        repository.save(new OrgPendingRecords(orgKey, next, fileName, yaml));
    }
}
