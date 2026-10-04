package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.domain.Recognition;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Frames against a candidate list: which candidate is the subject in front of the camera.
 *
 * <p>Starting one is the same two steps as an enrollment — commit the recognition and its ticket, then
 * submit — with the candidates' galleries synchronized first, so a photo added to a subject a moment
 * ago is at least on its way. The recognition does not wait for photos still PENDING.
 *
 * <p>Recognitions are purged with their frames and crop after {@code processpuzzle.ai.recognition.retention}:
 * the caller reads the outcome and records what it needs.
 */
@Service
public class Recognitions {

    private static final Logger LOG = LoggerFactory.getLogger(Recognitions.class);
    private static final String INVALID = "ai.recognition.invalid";

    private final RecognitionProfiles profiles;
    private final RecognitionRepository recognitions;
    private final VisionJobTicketRepository tickets;
    private final MediaUploadRepository uploadRepository;
    private final MediaUploads uploads;
    private final Enrollments enrollments;
    private final VisionJobs visionJobs;
    private final MediaStores stores;
    private final SubjectDirectory subjects;
    private final OrganizationGuard guard;
    private final TransactionTemplate transaction;
    private final AiProperties properties;

    public Recognitions(RecognitionProfiles profiles, RecognitionRepository recognitions, VisionJobTicketRepository tickets,
                        MediaUploadRepository uploadRepository, MediaUploads uploads, Enrollments enrollments,
                        VisionJobs visionJobs, MediaStores stores, ObjectProvider<SubjectDirectory> subjects,
                        OrganizationGuard guard, PlatformTransactionManager transactionManager, AiProperties properties) {
        this.profiles = profiles;
        this.recognitions = recognitions;
        this.tickets = tickets;
        this.uploadRepository = uploadRepository;
        this.uploads = uploads;
        this.enrollments = enrollments;
        this.visionJobs = visionJobs;
        this.stores = stores;
        this.subjects = subjects.getIfUnique(() -> SubjectDirectory.PERMISSIVE);
        this.guard = guard;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.properties = properties;
    }

    public RecognitionView start(String orgKey, String entityName, List<String> mediaKeys, List<UUID> candidateObjectIds) {
        guard.requireAccess(orgKey);
        AiProperties.Recognition limits = properties.getRecognition();
        List<String> frames = mediaKeys == null ? List.of() : List.copyOf(new LinkedHashSet<>(mediaKeys));
        List<UUID> candidates = candidateObjectIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(candidateObjectIds));
        if (frames.isEmpty() || frames.size() > limits.getMaxFrames()) {
            throw AiRequestException.invalid(INVALID, "Between 1 and " + limits.getMaxFrames() + " mediaKeys are required.");
        }
        if (candidates.isEmpty() || candidates.size() > limits.getMaxCandidates()) {
            throw AiRequestException.invalid(INVALID, "Between 1 and " + limits.getMaxCandidates() + " candidateObjectIds are required.");
        }
        RecognitionProfile profile = transaction.execute(status -> profiles.require(orgKey, entityName));
        for (UUID candidate : candidates) {
            if (!subjects.subjectExists(orgKey, entityName, candidate)) {
                throw AiRequestException.invalid("ai.recognition.candidate-unknown",
                        "Candidate " + candidate + " is not an object of '" + entityName + "'.");
            }
        }
        enrollments.synchronizeAll(profile, candidates);
        Recognition recognition = transaction.execute(status -> {
            List<UUID> frameKeys = new ArrayList<>();
            for (String mediaKey : frames) {
                MediaUpload upload = uploads.claim(orgKey, mediaKey, MediaPurpose.RECOGNITION_FRAME);
                frameKeys.add(upload.getMediaKey());
            }
            Instant now = Instant.now();
            VisionJobTicket ticket = tickets.save(VisionJobTicket.open(orgKey, VisionJobTicket.Kind.RECOGNITION, now));
            return recognitions.save(new Recognition(orgKey, entityName, frameKeys, candidates, ticket.getJobId(), now));
        });
        visionJobs.dispatch(recognition.getVisionJobId());
        return find(orgKey, recognition.getRecognitionId());
    }

    public RecognitionView find(String orgKey, UUID recognitionId) {
        guard.requireAccess(orgKey);
        return transaction.execute(status -> recognitions.findByOrgKeyAndRecognitionId(orgKey, recognitionId)
                .map(this::view)
                .orElseThrow(() -> AiRequestException.notFound("ai.recognition.not-found", "No recognition " + recognitionId + ".")));
    }

    /** Drops recognitions past their retention, with their frames and crop. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    public void purgeExpired() {
        Instant horizon = Instant.now().minus(properties.getRecognition().getRetention());
        List<Recognition> expired = transaction.execute(status -> recognitions.findByCreatedAtBefore(horizon));
        for (Recognition recognition : expired) {
            try {
                purge(recognition);
            } catch (RuntimeException e) {
                LOG.warn("could not purge recognition {}: {}", recognition.getRecognitionId(), e.getMessage());
            }
        }
    }

    private void purge(Recognition recognition) {
        List<MediaUpload> frames = transaction.execute(status -> uploadRepository.findAllById(recognition.getFrameMediaKeys()));
        for (MediaUpload frame : frames) {
            stores.get().delete(frame.getObjectName());
        }
        if (recognition.getCropObjectName() != null) {
            stores.get().delete(recognition.getCropObjectName());
        }
        transaction.executeWithoutResult(status -> {
            uploadRepository.deleteAllById(recognition.getFrameMediaKeys());
            recognitions.deleteById(recognition.getRecognitionId());
        });
    }

    private RecognitionView view(Recognition recognition) {
        return new RecognitionView(
                recognition.getRecognitionId(),
                recognition.getEntityName(),
                recognition.getStatus(),
                recognition.getOutcome(),
                recognition.getObjectId(),
                recognition.getScore(),
                recognition.getObservedIdentifier(),
                recognition.getObservedIdentifierConfidence(),
                recognition.getCropObjectName() == null ? null
                        : stores.get().readUrl(recognition.getCropObjectName(), properties.getMedia().getReadUrlExpiry()),
                List.copyOf(recognition.getRanking()),
                List.copyOf(recognition.getCandidatesWithoutGallery()),
                recognition.getFailureReason(),
                recognition.getCreatedAt(),
                recognition.getFinishedAt());
    }
}
