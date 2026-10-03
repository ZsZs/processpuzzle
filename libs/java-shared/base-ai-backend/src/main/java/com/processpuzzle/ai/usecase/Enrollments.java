package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The gallery of one subject: adding photos, reading the enrollment, removing photos.
 *
 * <p>Adding photos is two steps on purpose. The photos and their vision job ticket are committed first;
 * only then is the job submitted, outside that transaction, by {@link VisionJobs#dispatch}. A vision
 * server that is down therefore costs nothing but latency — the photos stay PENDING and the poller
 * submits the ticket later — and a notification that arrives before the submission call returns still
 * finds its ticket.
 */
@Service
public class Enrollments {

    private final RecognitionProfiles profiles;
    private final MediaUploads uploads;
    private final EnrollmentPhotoRepository photos;
    private final VisionJobTicketRepository tickets;
    private final VisionJobs visionJobs;
    private final MediaStores stores;
    private final SubjectDirectory subjects;
    private final OrganizationGuard guard;
    private final TransactionTemplate transaction;
    private final AiProperties.Media settings;

    public Enrollments(RecognitionProfiles profiles, MediaUploads uploads, EnrollmentPhotoRepository photos,
                       VisionJobTicketRepository tickets, VisionJobs visionJobs, MediaStores stores,
                       ObjectProvider<SubjectDirectory> subjects, OrganizationGuard guard,
                       PlatformTransactionManager transactionManager, AiProperties properties) {
        this.profiles = profiles;
        this.uploads = uploads;
        this.photos = photos;
        this.tickets = tickets;
        this.visionJobs = visionJobs;
        this.stores = stores;
        this.subjects = subjects.getIfUnique(() -> SubjectDirectory.PERMISSIVE);
        this.guard = guard;
        this.transaction = new TransactionTemplate(transactionManager);
        this.settings = properties.getMedia();
    }

    public EnrollmentView find(String orgKey, String entityName, UUID objectId) {
        guard.requireAccess(orgKey);
        return transaction.execute(status -> view(requireSubject(orgKey, entityName, objectId), objectId));
    }

    /** Adds the uploaded photos and starts their enrollment; idempotent per media key. */
    public EnrollmentView addPhotos(String orgKey, String entityName, UUID objectId, List<String> mediaKeys) {
        guard.requireAccess(orgKey);
        if (mediaKeys == null || mediaKeys.isEmpty()) {
            throw AiRequestException.invalid("ai.enrollment.invalid", "mediaKeys must not be empty.");
        }
        UUID ticketId = transaction.execute(status -> register(orgKey, entityName, objectId, mediaKeys));
        if (ticketId != null) {
            visionJobs.dispatch(ticketId);
        }
        return find(orgKey, entityName, objectId);
    }

    public void deletePhoto(String orgKey, String entityName, UUID objectId, UUID photoId) {
        guard.requireAccess(orgKey);
        EnrollmentPhoto photo = transaction.execute(status -> {
            EnrollmentPhoto found = photos.findByOrgKeyAndEntityNameAndObjectIdAndPhotoId(orgKey, entityName, objectId, photoId)
                    .orElseThrow(() -> AiRequestException.notFound("ai.enrollment.photo-not-found", "No photo " + photoId + "."));
            photos.delete(found);
            return found;
        });
        discardObjects(List.of(photo));
    }

    public void delete(String orgKey, String entityName, UUID objectId) {
        guard.requireAccess(orgKey);
        List<EnrollmentPhoto> removed = transaction.execute(status -> {
            List<EnrollmentPhoto> gallery = photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(orgKey, entityName, objectId);
            if (gallery.isEmpty()) {
                throw AiRequestException.notFound("ai.enrollment.not-found", "The subject has no enrollment.");
            }
            photos.deleteAll(gallery);
            return gallery;
        });
        discardObjects(removed);
    }

    private UUID register(String orgKey, String entityName, UUID objectId, List<String> mediaKeys) {
        requireSubject(orgKey, entityName, objectId);
        Instant now = Instant.now();
        List<EnrollmentPhoto> added = new ArrayList<>();
        for (String mediaKey : new LinkedHashSet<>(mediaKeys)) {
            MediaUpload upload = uploads.claim(orgKey, mediaKey, MediaPurpose.ENROLLMENT_PHOTO);
            if (!photos.existsByOrgKeyAndMediaKey(orgKey, upload.getMediaKey())) {
                added.add(new EnrollmentPhoto(upload, entityName, objectId, now));
            }
        }
        if (added.isEmpty()) {
            return null;
        }
        VisionJobTicket ticket = tickets.save(VisionJobTicket.open(orgKey, VisionJobTicket.Kind.ENROLLMENT, now));
        added.forEach(photo -> photo.assignTo(ticket.getJobId()));
        photos.saveAll(added);
        return ticket.getJobId();
    }

    private RecognitionProfile requireSubject(String orgKey, String entityName, UUID objectId) {
        RecognitionProfile profile = profiles.require(orgKey, entityName);
        if (!subjects.subjectExists(orgKey, entityName, objectId)) {
            throw AiRequestException.notFound("ai.enrollment.subject-not-found",
                    "No " + entityName + " object " + objectId + ".");
        }
        return profile;
    }

    private EnrollmentView view(RecognitionProfile profile, UUID objectId) {
        String orgKey = profile.getOrgKey();
        String entityName = profile.getEntityName();
        List<EnrollmentPhoto> gallery = photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(orgKey, entityName, objectId);
        Optional<String> registered = profile.readsIdentifier()
                ? subjects.identifier(orgKey, entityName, objectId, profile.getIdentifierAttributeKey())
                : Optional.empty();
        List<EnrollmentView.Photo> views = gallery.stream()
                .map(photo -> new EnrollmentView.Photo(
                        photo.getPhotoId(),
                        photo.getStatus(),
                        stores.get().readUrl(photo.getPhotoObjectName(), settings.getReadUrlExpiry()),
                        photo.getCropObjectName() == null ? null
                                : stores.get().readUrl(photo.getCropObjectName(), settings.getReadUrlExpiry()),
                        photo.getObservedIdentifier(),
                        mismatch(photo.getObservedIdentifier(), registered.orElse(null)),
                        photo.getFailureReason(),
                        photo.getAddedAt()))
                .toList();
        String model = gallery.stream()
                .filter(photo -> photo.getStatus() == EnrollmentPhotoStatus.ENROLLED)
                .map(EnrollmentPhoto::getEmbeddingModel)
                .findFirst().orElse(null);
        Instant updated = gallery.stream().map(EnrollmentPhoto::getAddedAt).max(Comparator.naturalOrder()).orElse(null);
        return new EnrollmentView(entityName, objectId, status(gallery), registered.orElse(null), model, views, updated);
    }

    static EnrollmentView.Status status(List<EnrollmentPhoto> gallery) {
        if (gallery.isEmpty()) {
            return EnrollmentView.Status.NOT_ENROLLED;
        }
        if (gallery.stream().anyMatch(photo -> photo.getStatus() == EnrollmentPhotoStatus.PENDING)) {
            return EnrollmentView.Status.PROCESSING;
        }
        if (gallery.stream().anyMatch(photo -> photo.getStatus() == EnrollmentPhotoStatus.ENROLLED)) {
            return EnrollmentView.Status.READY;
        }
        return EnrollmentView.Status.FAILED;
    }

    /** Readings and registrations compare with case and whitespace ignored, as the vision server normalizes. */
    static boolean mismatch(String observed, String registered) {
        if (observed == null || registered == null) {
            return false;
        }
        return !compact(observed).equals(compact(registered));
    }

    private static String compact(String identifier) {
        return identifier.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private void discardObjects(List<EnrollmentPhoto> removed) {
        for (EnrollmentPhoto photo : removed) {
            stores.get().delete(photo.getPhotoObjectName());
            if (photo.getCropObjectName() != null) {
                stores.get().delete(photo.getCropObjectName());
            }
        }
    }
}
