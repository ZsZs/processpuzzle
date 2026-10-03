package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The life of a vision job from this side: submission, the notification, and the polling fallback.
 *
 * <p>Calls to the vision server are never made inside a transaction — a slow or absent server must
 * not hold a database connection — so each step is a short transaction, a call, and another short
 * transaction. The ticket is what ties them together and what survives a restart of either side.
 *
 * <p>A notification only says that a job finished. The result is always fetched from the vision
 * server, so a forged or replayed notification can at most trigger a fetch of a real result.
 */
@Service
public class VisionJobs {

    private static final Logger LOG = LoggerFactory.getLogger(VisionJobs.class);

    private final VisionJobTicketRepository tickets;
    private final EnrollmentPhotoRepository photos;
    private final RecognitionProfiles profiles;
    private final MediaStores stores;
    private final VisionServer visionServer;
    private final TransactionTemplate transaction;
    private final AiProperties properties;

    public VisionJobs(VisionJobTicketRepository tickets, EnrollmentPhotoRepository photos, RecognitionProfiles profiles,
                      MediaStores stores, VisionServer visionServer,
                      PlatformTransactionManager transactionManager, AiProperties properties) {
        this.tickets = tickets;
        this.photos = photos;
        this.profiles = profiles;
        this.stores = stores;
        this.visionServer = visionServer;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    // ── Submission ─────────────────────────────────────────────────

    /**
     * Submits an open ticket. A failure leaves it SUBMITTING for the poller; it never propagates,
     * because the caller's own work — the photos — is already committed and fine.
     */
    public void dispatch(UUID jobId) {
        VisionServer.EnrollmentRequest request = transaction.execute(status -> prepare(jobId));
        if (request == null) {
            return;
        }
        try {
            visionServer.submitEnrollment(request);
        } catch (VisionServerUnavailableException e) {
            LOG.warn("vision job {} not submitted, will retry: {}", jobId, e.getMessage());
            return;
        }
        transaction.executeWithoutResult(status -> tickets.findById(jobId)
                .filter(ticket -> ticket.getStatus() == VisionJobTicket.Status.SUBMITTING)
                .ifPresent(ticket -> ticket.submitted(Instant.now())));
    }

    /** Records the attempt and its fresh callback token, and builds the request; null if there is nothing to submit. */
    private VisionServer.EnrollmentRequest prepare(UUID jobId) {
        VisionJobTicket ticket = tickets.findById(jobId).orElse(null);
        if (ticket == null || !ticket.isOpen()) {
            return null;
        }
        List<EnrollmentPhoto> pending = photos.findByVisionJobId(jobId);
        if (pending.isEmpty()) {
            ticket.completed(Instant.now());
            return null;
        }
        if (ticket.getSubmitAttempts() >= properties.getPoller().getMaxSubmitAttempts()) {
            String reason = "the vision server could not be reached";
            pending.forEach(photo -> photo.rejected(EnrollmentPhotoStatus.FAILED, reason));
            ticket.failed(reason, Instant.now());
            return null;
        }
        EnrollmentPhoto first = pending.getFirst();
        RecognitionProfile profile = profiles.require(first.getOrgKey(), first.getEntityName());
        ticket.submitting(Instant.now());
        String token = ticket.newCallbackToken();
        List<VisionServer.Media> media = pending.stream()
                .map(photo -> new VisionServer.Media(photo.getPhotoId().toString(),
                        stores.get().internalReadUrl(photo.getPhotoObjectName(), properties.getMedia().getVisionUrlExpiry())))
                .toList();
        return new VisionServer.EnrollmentRequest(jobId, ticket.getOrgKey(), token, profile.getDetectorClass(),
                profile.readsIdentifier(), profile.getIdentifierPattern(), media);
    }

    // ── Completion ─────────────────────────────────────────────────

    /** The vision server's callback. Verifies the token, then settles the job. */
    public void notified(String orgKey, String callbackToken, UUID jobId, VisionServer.Status reported) {
        VisionJobTicket ticket = transaction.execute(status -> tickets.findById(jobId)
                .filter(found -> found.getOrgKey().equals(orgKey))
                .orElse(null));
        if (ticket == null) {
            throw AiRequestException.notFound("ai.vision-job.not-found", "No vision job " + jobId + ".");
        }
        if (!ticket.accepts(callbackToken)) {
            throw new AiRequestException(AiRequestException.Kind.UNAUTHORIZED, "ai.vision-job.token-rejected",
                    "The callback token does not belong to vision job " + jobId + ".");
        }
        if (ticket.isOpen()) {
            settle(jobId, reported);
        }
    }

    private void settle(UUID jobId, VisionServer.Status reported) {
        try {
            if (reported == VisionServer.Status.DONE) {
                VisionServer.EnrollmentOutcome outcome = visionServer.enrollmentResult(jobId);
                transaction.executeWithoutResult(status -> applyEnrollment(jobId, outcome));
            } else {
                String reason = visionServer.job(jobId).map(VisionServer.JobState::failureReason)
                        .orElse("the vision job was " + reported.name().toLowerCase());
                transaction.executeWithoutResult(status -> fail(jobId, reason));
            }
            visionServer.discard(jobId);
        } catch (VisionServerUnavailableException e) {
            LOG.warn("vision job {} could not be settled now, the poller will retry: {}", jobId, e.getMessage());
        }
    }

    private void applyEnrollment(UUID jobId, VisionServer.EnrollmentOutcome outcome) {
        VisionJobTicket ticket = tickets.findById(jobId).orElse(null);
        if (ticket == null || !ticket.isOpen()) {
            return;
        }
        Map<String, VisionServer.PhotoOutcome> byPhoto = outcome.photos().stream()
                .collect(Collectors.toMap(VisionServer.PhotoOutcome::mediaId, Function.identity(), (a, b) -> a));
        for (EnrollmentPhoto photo : photos.findByVisionJobId(jobId)) {
            VisionServer.PhotoOutcome result = byPhoto.get(photo.getPhotoId().toString());
            if (result == null) {
                photo.rejected(EnrollmentPhotoStatus.FAILED, "the vision server returned no result for this photo");
            } else if (result.status() == EnrollmentPhotoStatus.ENROLLED && result.cropJpeg() != null && result.embedding() != null) {
                String cropName = photo.getOrgKey() + "/crops/" + photo.getPhotoId() + ".jpg";
                stores.get().put(cropName, result.cropJpeg(), "image/jpeg");
                photo.enrolled(cropName, result.embeddingModel(), result.embedding(), result.identifierText());
            } else {
                EnrollmentPhotoStatus rejection = result.status() == EnrollmentPhotoStatus.ENROLLED
                        ? EnrollmentPhotoStatus.FAILED : result.status();
                photo.rejected(rejection, result.failureReason());
            }
        }
        ticket.completed(Instant.now());
    }

    private void fail(UUID jobId, String reason) {
        tickets.findById(jobId).filter(VisionJobTicket::isOpen).ifPresent(ticket -> {
            photos.findByVisionJobId(jobId).forEach(photo -> photo.rejected(EnrollmentPhotoStatus.FAILED, reason));
            ticket.failed(reason, Instant.now());
        });
    }

    // ── Polling fallback ───────────────────────────────────────────

    /**
     * Chases tickets nothing has happened to for a while: resubmits the unsubmitted and the ones the
     * vision server has forgotten, settles the ones that finished without a notification arriving.
     */
    @Scheduled(fixedDelayString = "${processpuzzle.ai.poller.interval:PT1M}", initialDelayString = "PT1M")
    public void poll() {
        if (!properties.getPoller().isEnabled()) {
            return;
        }
        Instant horizon = Instant.now().minus(properties.getPoller().getOverdueAfter());
        List<VisionJobTicket> overdue = transaction.execute(status -> tickets.findByStatusInAndTouchedAtBefore(
                EnumSet.of(VisionJobTicket.Status.SUBMITTING, VisionJobTicket.Status.SUBMITTED), horizon));
        for (VisionJobTicket ticket : overdue) {
            try {
                chase(ticket);
            } catch (RuntimeException e) {
                LOG.warn("chasing vision job {} failed: {}", ticket.getJobId(), e.getMessage());
            }
        }
    }

    private void chase(VisionJobTicket ticket) {
        if (ticket.getStatus() == VisionJobTicket.Status.SUBMITTING) {
            dispatch(ticket.getJobId());
            return;
        }
        Optional<VisionServer.JobState> state = visionServer.job(ticket.getJobId());
        if (state.isEmpty()) {
            LOG.info("vision job {} is unknown to the vision server, resubmitting", ticket.getJobId());
            dispatch(ticket.getJobId());
        } else if (state.get().isFinished()) {
            settle(ticket.getJobId(), state.get().status());
        }
    }
}
