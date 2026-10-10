package com.processpuzzle.ai.adapter.inbound;

import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.domain.RecognitionProfileRepository;
import com.processpuzzle.ai.domain.RecognitionRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.MediaStores;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes this feature's rows of an erased tenant — recognitions with their collection tables,
 * enrollment photos, upload slots, vision job tickets, recognition profiles — and, once that has
 * committed, the tenant's media and its still-open jobs on the vision server.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, for the reason base-app's cleaner gives: these deletes join the
 * erasing transaction, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 *
 * <p><b>Bulk statements, collection tables first.</b> A JPQL {@code delete} does not cascade, so the
 * four collection tables of {@code Recognition} go before it, through their owner, since they carry no
 * {@code org_key}. The other tables have no relationships between them.
 *
 * <p><b>Storage and the vision server only after commit.</b> Neither takes part in the transaction:
 * media deleted before a commit that then failed would leave rows pointing at nothing, and a job
 * discarded would leave a ticket the poller resubmits. So both wait for the commit, and both are
 * best-effort — the rows are gone by then, and an object store or vision server that is down must not
 * make an erasure that has succeeded look failed. Every object of the tenant is under {@code <orgKey>/},
 * so the media goes by prefix; the open job ids are read before their tickets go. The vision server
 * keeps nothing durable, so there is nothing else of the tenant on it.
 *
 * <p>Named, because base-app and other features declare a {@code TenantDataCleaner} too, and an
 * application that assembles several features would otherwise have two beans of one name.
 */
@Component("aiTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);
    private static final EnumSet<VisionJobTicket.Status> OPEN =
            EnumSet.of(VisionJobTicket.Status.SUBMITTING, VisionJobTicket.Status.SUBMITTED);

    private final RecognitionRepository recognitions;
    private final EnrollmentPhotoRepository photos;
    private final MediaUploadRepository uploads;
    private final VisionJobTicketRepository tickets;
    private final RecognitionProfileRepository profiles;
    private final MediaStores stores;
    private final VisionServer visionServer;

    public TenantDataCleaner(RecognitionRepository recognitions, EnrollmentPhotoRepository photos,
                             MediaUploadRepository uploads, VisionJobTicketRepository tickets,
                             RecognitionProfileRepository profiles, MediaStores stores, VisionServer visionServer) {
        this.recognitions = recognitions;
        this.photos = photos;
        this.uploads = uploads;
        this.tickets = tickets;
        this.profiles = profiles;
        this.stores = stores;
        this.visionServer = visionServer;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        List<UUID> openJobs = tickets.findJobIdsByOrgKeyAndStatusIn(orgKey, OPEN);

        recognitions.eraseFramesByOrgKey(orgKey);
        recognitions.eraseCandidateIdsByOrgKey(orgKey);
        recognitions.eraseCandidatesWithoutGalleryByOrgKey(orgKey);
        recognitions.eraseRankingByOrgKey(orgKey);
        int erasedRecognitions = recognitions.eraseByOrgKey(orgKey);
        int erasedPhotos = photos.eraseByOrgKey(orgKey);
        int erasedUploads = uploads.eraseByOrgKey(orgKey);
        int erasedTickets = tickets.eraseByOrgKey(orgKey);
        int erasedProfiles = profiles.eraseByOrgKey(orgKey);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteMedia(orgKey);
                openJobs.forEach(TenantDataCleaner.this::discard);
            }
        });
        LOG.info("Organization '{}' erased: {} recognition(s), {} enrollment photo(s), {} upload(s), {} vision job "
                        + "ticket(s) and {} recognition profile(s) deleted.", orgKey, erasedRecognitions, erasedPhotos,
                erasedUploads, erasedTickets, erasedProfiles);
    }

    private void deleteMedia(String orgKey) {
        try {
            stores.get().deleteByPrefix(orgKey + "/");
        } catch (RuntimeException e) {
            LOG.warn("Organization '{}' erased, but its media could not be deleted: {}", orgKey, e.getMessage());
        }
    }

    private void discard(UUID jobId) {
        try {
            visionServer.discard(jobId);
        } catch (RuntimeException e) {
            LOG.warn("Vision job {} of an erased organization could not be discarded: {}", jobId, e.getMessage());
        }
    }
}
