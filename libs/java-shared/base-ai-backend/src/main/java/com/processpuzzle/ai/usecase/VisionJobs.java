package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.MatchingSettings;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.domain.Recognition;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.ai.usecase.port.VisionJobRefusedException;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.ai.usecase.port.VisionServerUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The life of a vision job from this side: submission, the notification, and the polling fallback —
 * for enrollment tickets, which carry a batch of gallery photos, and recognition tickets, which carry
 * one {@link Recognition}.
 *
 * <p>Calls to the vision server are never made inside a transaction — a slow or absent server must
 * not hold a database connection — so each step is a short transaction, a call, and another short
 * transaction. The ticket is what ties them together and what survives a restart of either side.
 * The transactions are {@code REQUIRES_NEW} for the reason {@link Enrollments} gives.
 *
 * <p>A notification only says that a job finished. The result is always fetched from the vision
 * server, so a forged or replayed notification can at most trigger a fetch of a real result.
 */
@Service
public class VisionJobs {

    private static final Logger LOG = LoggerFactory.getLogger(VisionJobs.class);

    private final VisionJobTicketRepository tickets;
    private final EnrollmentPhotoRepository photos;
    private final RecognitionRepository recognitions;
    private final MediaUploadRepository uploads;
    private final RecognitionProfiles profiles;
    private final MediaStores stores;
    private final SubjectDirectory subjects;
    private final VisionServer visionServer;
    private final TransactionTemplate transaction;
    private final AiProperties properties;
    private final Executor background;

    @Autowired
    public VisionJobs(VisionJobTicketRepository tickets, EnrollmentPhotoRepository photos, RecognitionRepository recognitions,
                      MediaUploadRepository uploads, RecognitionProfiles profiles, MediaStores stores,
                      ObjectProvider<SubjectDirectory> subjects, VisionServer visionServer,
                      PlatformTransactionManager transactionManager, AiProperties properties) {
        this(tickets, photos, recognitions, uploads, profiles, stores, subjects.getIfUnique(() -> SubjectDirectory.PERMISSIVE),
                visionServer, transactionManager, properties, Executors.newVirtualThreadPerTaskExecutor());
    }

    VisionJobs(VisionJobTicketRepository tickets, EnrollmentPhotoRepository photos, RecognitionRepository recognitions,
               MediaUploadRepository uploads, RecognitionProfiles profiles, MediaStores stores, SubjectDirectory subjects,
               VisionServer visionServer, PlatformTransactionManager transactionManager, AiProperties properties,
               Executor background) {
        this.tickets = tickets;
        this.photos = photos;
        this.recognitions = recognitions;
        this.uploads = uploads;
        this.profiles = profiles;
        this.stores = stores;
        this.subjects = subjects;
        this.visionServer = visionServer;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.properties = properties;
        this.background = background;
    }

    // ── Submission ─────────────────────────────────────────────────

    /**
     * Submits an open ticket. A failure leaves it SUBMITTING for the poller; it never propagates,
     * because the caller's own work is already committed and fine. A refusal fails the job at once.
     */
    public void dispatch(UUID jobId) {
        Optional<Submission> submission = transaction.execute(status -> prepare(jobId));
        if (submission.isEmpty()) {
            return;
        }
        try {
            submission.get().send(visionServer);
        } catch (VisionServerUnavailableException e) {
            LOG.warn("vision job {} not submitted, will retry: {}", jobId, e.getMessage());
            return;
        } catch (VisionJobRefusedException e) {
            LOG.warn("vision job {} refused: {}", jobId, e.getMessage());
            transaction.executeWithoutResult(status -> fail(jobId, e.getMessage()));
            return;
        }
        transaction.executeWithoutResult(status -> tickets.findById(jobId)
                .filter(ticket -> ticket.getStatus() == VisionJobTicket.Status.SUBMITTING)
                .ifPresent(ticket -> ticket.submitted(Instant.now())));
    }

    /** {@link #dispatch} off the calling thread, for a caller that must not wait on the vision server. */
    public void dispatchLater(UUID jobId) {
        if (jobId != null) {
            background.execute(() -> dispatch(jobId));
        }
    }

    /** Records the attempt and its fresh callback token, and builds the request; empty if there is nothing to submit. */
    private Optional<Submission> prepare(UUID jobId) {
        VisionJobTicket ticket = tickets.findById(jobId).orElse(null);
        if (ticket == null || !ticket.isOpen()) {
            return Optional.empty();
        }
        if (ticket.getSubmitAttempts() >= properties.getPoller().getMaxSubmitAttempts()) {
            fail(jobId, "the vision server could not be reached");
            return Optional.empty();
        }
        return ticket.getKind() == VisionJobTicket.Kind.RECOGNITION ? prepareRecognition(ticket) : prepareEnrollment(ticket);
    }

    private Optional<Submission> prepareEnrollment(VisionJobTicket ticket) {
        List<EnrollmentPhoto> pending = photos.findByVisionJobId(ticket.getJobId());
        List<VisionServer.Media> media = new ArrayList<>();
        for (EnrollmentPhoto photo : pending) {
            try {
                media.add(new VisionServer.Media(photo.getPhotoId().toString(),
                        subjects.internalPhotoUrl(photo.getPhotoRef(), properties.getMedia().getVisionUrlExpiry())));
            } catch (RuntimeException e) {
                photo.rejected(EnrollmentPhotoStatus.FAILED, "the photo could not be read: " + e.getMessage());
            }
        }
        if (media.isEmpty()) {
            ticket.completed(Instant.now());
            return Optional.empty();
        }
        EnrollmentPhoto first = pending.getFirst();
        RecognitionProfile profile = profiles.require(first.getOrgKey(), first.getEntityName());
        ticket.submitting(Instant.now());
        String token = ticket.newCallbackToken();
        var request = new VisionServer.EnrollmentRequest(ticket.getJobId(), ticket.getOrgKey(), token,
                profile.getDetectorClass(), profile.readsIdentifier(), profile.getIdentifierPattern(), media);
        return Optional.of(server -> server.submitEnrollment(request));
    }

    private Optional<Submission> prepareRecognition(VisionJobTicket ticket) {
        Recognition recognition = recognitions.findByVisionJobId(ticket.getJobId()).filter(Recognition::isPending).orElse(null);
        if (recognition == null) {
            ticket.completed(Instant.now());
            return Optional.empty();
        }
        RecognitionProfile profile = profiles.require(recognition.getOrgKey(), recognition.getEntityName());
        Map<UUID, MediaUpload> frames = uploads.findAllById(recognition.getFrameMediaKeys()).stream()
                .collect(Collectors.toMap(MediaUpload::getMediaKey, Function.identity()));
        List<VisionServer.Media> media = recognition.getFrameMediaKeys().stream()
                .filter(frames::containsKey)
                .map(key -> new VisionServer.Media(key.toString(),
                        stores.get().internalReadUrl(frames.get(key).getObjectName(), properties.getMedia().getVisionUrlExpiry())))
                .toList();
        if (media.isEmpty()) {
            fail(ticket.getJobId(), "the frames are gone");
            return Optional.empty();
        }
        List<VisionServer.Candidate> candidates = candidates(profile, recognition);
        recognition.withoutGallery(candidates.stream().filter(candidate -> candidate.gallery().isEmpty())
                .map(candidate -> UUID.fromString(candidate.candidateId())).toList());
        MatchingSettings matching = profile.getMatching() == null ? MatchingSettings.defaults() : profile.getMatching();
        ticket.submitting(Instant.now());
        String token = ticket.newCallbackToken();
        var request = new VisionServer.RecognitionRequest(ticket.getJobId(), ticket.getOrgKey(), token,
                profile.getDetectorClass(), profile.readsIdentifier(), profile.getIdentifierPattern(),
                matching.getIdentifierWeight(), matching.getAcceptScore(), matching.getAcceptMargin(), media, candidates);
        return Optional.of(server -> server.submitRecognition(request));
    }

    /** Every candidate with its registered identifier and the embeddings of its enrolled photos. */
    private List<VisionServer.Candidate> candidates(RecognitionProfile profile, Recognition recognition) {
        Map<UUID, List<VisionServer.GalleryEmbedding>> galleries = new LinkedHashMap<>();
        photos.findByOrgKeyAndEntityNameAndObjectIdInAndStatus(recognition.getOrgKey(), recognition.getEntityName(),
                        recognition.getCandidateObjectIds(), EnrollmentPhotoStatus.ENROLLED)
                .forEach(photo -> galleries.computeIfAbsent(photo.getObjectId(), id -> new ArrayList<>())
                        .add(new VisionServer.GalleryEmbedding(photo.getEmbeddingModel(), photo.getEmbedding())));
        return recognition.getCandidateObjectIds().stream()
                .map(objectId -> new VisionServer.Candidate(
                        objectId.toString(),
                        profile.readsIdentifier()
                                ? subjects.identifier(recognition.getOrgKey(), recognition.getEntityName(), objectId,
                                        profile.getIdentifierAttributeKey()).orElse(null)
                                : null,
                        galleries.getOrDefault(objectId, List.of())))
                .toList();
    }

    // ── Completion ─────────────────────────────────────────────────

    /** The vision server's callback. Verifies the token, then settles the job. */
    public void notified(String orgKey, String callbackToken, UUID jobId, VisionServer.Status reported) {
        VisionJobTicket ticket = transaction.execute(status -> tickets.findById(jobId)
                .filter(found -> found.getOrgKey().equals(orgKey))
                .orElseThrow(() -> AiRequestException.notFound("ai.vision-job.not-found", "No vision job " + jobId + ".")));
        if (!ticket.accepts(callbackToken)) {
            throw new AiRequestException(AiRequestException.Kind.UNAUTHORIZED, "ai.vision-job.token-rejected",
                    "The callback token does not belong to vision job " + jobId + ".");
        }
        if (ticket.isOpen()) {
            settle(ticket, reported);
        }
    }

    private void settle(VisionJobTicket ticket, VisionServer.Status reported) {
        UUID jobId = ticket.getJobId();
        try {
            if (reported != VisionServer.Status.DONE) {
                String reason = visionServer.job(jobId).map(VisionServer.JobState::failureReason)
                        .orElse("the vision job was " + reported.name().toLowerCase());
                transaction.executeWithoutResult(status -> fail(jobId, reason));
            } else if (ticket.getKind() == VisionJobTicket.Kind.RECOGNITION) {
                VisionServer.RecognitionOutcome outcome = visionServer.recognitionResult(jobId);
                transaction.executeWithoutResult(status -> applyRecognition(jobId, outcome));
            } else {
                VisionServer.EnrollmentOutcome outcome = visionServer.enrollmentResult(jobId);
                transaction.executeWithoutResult(status -> applyEnrollment(jobId, outcome));
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

    private void applyRecognition(UUID jobId, VisionServer.RecognitionOutcome outcome) {
        VisionJobTicket ticket = tickets.findById(jobId).orElse(null);
        if (ticket == null || !ticket.isOpen()) {
            return;
        }
        Instant now = Instant.now();
        recognitions.findByVisionJobId(jobId).filter(Recognition::isPending).ifPresent(recognition -> {
            if (outcome.subject().isEmpty()) {
                recognition.noSubject(now);
                return;
            }
            VisionServer.SubjectSighting sighting = outcome.subject().get();
            String cropName = null;
            if (sighting.cropJpeg() != null) {
                cropName = recognition.getOrgKey() + "/recognitions/" + recognition.getRecognitionId() + ".jpg";
                stores.get().put(cropName, sighting.cropJpeg(), "image/jpeg");
            }
            List<Recognition.RankedCandidate> ranking = sighting.ranking().stream()
                    .map(score -> new Recognition.RankedCandidate(UUID.fromString(score.candidateId()), score.score(),
                            score.identifierScore(), score.embeddingScore()))
                    .toList();
            recognition.answered(sighting.candidateId() == null ? null : UUID.fromString(sighting.candidateId()),
                    sighting.score(), sighting.identifierText(), sighting.identifierConfidence(), cropName, ranking, now);
        });
        ticket.completed(now);
    }

    private void fail(UUID jobId, String reason) {
        tickets.findById(jobId).filter(VisionJobTicket::isOpen).ifPresent(ticket -> {
            Instant now = Instant.now();
            if (ticket.getKind() == VisionJobTicket.Kind.RECOGNITION) {
                recognitions.findByVisionJobId(jobId).filter(Recognition::isPending)
                        .ifPresent(recognition -> recognition.failed(reason, now));
            } else {
                photos.findByVisionJobId(jobId).forEach(photo -> photo.rejected(EnrollmentPhotoStatus.FAILED, reason));
            }
            ticket.failed(reason, now);
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
            settle(ticket, state.get().status());
        }
    }

    /** A prepared request, sent outside the transaction that built it. */
    @FunctionalInterface
    private interface Submission {
        void send(VisionServer server);
    }
}
