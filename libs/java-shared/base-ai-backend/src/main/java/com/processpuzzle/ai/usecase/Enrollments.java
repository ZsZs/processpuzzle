package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.galleries.SubjectGalleries;
import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.EnrollmentPhotoStatus;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionProfileRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The gallery of one subject, derived from the photos in its profile's {@code galleryAttributeKey}.
 * Synchronizing compares the photos the subject has with the ones already in its gallery: new ones are
 * enrolled, gone ones are dropped with their crops. The photos themselves are the subject's and are
 * never written or deleted here.
 *
 * <p>Synchronizing is two steps on purpose. The new photos and their vision job ticket are committed
 * first; only then is the job submitted, outside that transaction, by {@link VisionJobs#dispatch}. A
 * vision server that is down therefore costs nothing but latency — the photos stay PENDING and the
 * poller submits the ticket later — and a notification that arrives before the submission call returns
 * still finds its ticket.
 *
 * <p>Transactions are {@code REQUIRES_NEW}: {@link SubjectGalleries} is called from after-commit
 * listeners, where the finished transaction is still bound to the thread and a joining write would
 * silently never commit.
 */
@Service
public class Enrollments implements SubjectGalleries {

    private static final Logger LOG = LoggerFactory.getLogger(Enrollments.class);

    private final RecognitionProfiles profiles;
    private final RecognitionProfileRepository profileRepository;
    private final EnrollmentPhotoRepository photos;
    private final VisionJobTicketRepository tickets;
    private final VisionJobs visionJobs;
    private final MediaStores stores;
    private final SubjectDirectory subjects;
    private final OrganizationGuard guard;
    private final TransactionTemplate transaction;
    private final AiProperties.Media settings;

    public Enrollments(RecognitionProfiles profiles, RecognitionProfileRepository profileRepository,
                       EnrollmentPhotoRepository photos, VisionJobTicketRepository tickets, VisionJobs visionJobs,
                       MediaStores stores, ObjectProvider<SubjectDirectory> subjects, OrganizationGuard guard,
                       PlatformTransactionManager transactionManager, AiProperties properties) {
        this.profiles = profiles;
        this.profileRepository = profileRepository;
        this.photos = photos;
        this.tickets = tickets;
        this.visionJobs = visionJobs;
        this.stores = stores;
        this.subjects = subjects.getIfUnique(() -> SubjectDirectory.PERMISSIVE);
        this.guard = guard;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.settings = properties.getMedia();
    }

    public EnrollmentView find(String orgKey, String entityName, UUID objectId) {
        guard.requireAccess(orgKey);
        return transaction.execute(status -> view(requireSubject(orgKey, entityName, objectId), objectId));
    }

    /** Brings the gallery in line with the subject's photos and starts enrolling the new ones. */
    public EnrollmentView synchronize(String orgKey, String entityName, UUID objectId) {
        guard.requireAccess(orgKey);
        Sync sync = transaction.execute(status -> reconcile(requireSubject(orgKey, entityName, objectId), objectId));
        finish(sync);
        return find(orgKey, entityName, objectId);
    }

    /** Discards what was derived from the subject's photos; the next synchronization enrolls them afresh. */
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
        discardCrops(removed);
    }

    // ── SubjectGalleries: trusted, no caller to check ──────────────

    /** Subjects of a type without a profile are not recognized, so their changes are ignored. */
    @Override
    public void subjectChanged(String orgKey, String entityName, UUID objectId) {
        Sync sync = transaction.execute(status -> profileRepository.findByOrgKeyAndEntityName(orgKey, entityName)
                .map(profile -> reconcile(profile, objectId))
                .orElse(Sync.NONE));
        visionJobs.dispatchLater(sync.ticketId());
        discardCrops(sync.dropped());
    }

    @Override
    public void subjectDeleted(String orgKey, String entityName, UUID objectId) {
        List<EnrollmentPhoto> removed = transaction.execute(status -> {
            List<EnrollmentPhoto> gallery = photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(orgKey, entityName, objectId);
            photos.deleteAll(gallery);
            return gallery;
        });
        discardCrops(removed);
    }

    /**
     * Synchronizes the galleries of a recognition's candidates, which must be up to date before their
     * embeddings are read. Photos new to a gallery are still PENDING afterwards — the recognition does
     * not wait for them.
     */
    void synchronizeAll(RecognitionProfile profile, List<UUID> objectIds) {
        for (UUID objectId : objectIds) {
            try {
                Sync sync = transaction.execute(status -> reconcile(profile, objectId));
                finish(sync);
            } catch (RuntimeException e) {
                LOG.warn("gallery of {}/{} not synchronized: {}", profile.getEntityName(), objectId, e.getMessage());
            }
        }
    }

    // ── Reconciliation ─────────────────────────────────────────────

    private Sync reconcile(RecognitionProfile profile, UUID objectId) {
        String orgKey = profile.getOrgKey();
        String entityName = profile.getEntityName();
        Map<String, SubjectDirectory.SubjectPhoto> current = new LinkedHashMap<>();
        if (profile.getGalleryAttributeKey() != null) {
            subjects.photos(orgKey, entityName, objectId, profile.getGalleryAttributeKey())
                    .forEach(photo -> current.putIfAbsent(photo.photoRef(), photo));
        }
        List<EnrollmentPhoto> gallery = photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt(orgKey, entityName, objectId);
        Set<String> known = gallery.stream().map(EnrollmentPhoto::getPhotoRef).collect(Collectors.toSet());
        List<EnrollmentPhoto> dropped = gallery.stream().filter(photo -> !current.containsKey(photo.getPhotoRef())).toList();
        photos.deleteAll(dropped);

        Instant now = Instant.now();
        List<EnrollmentPhoto> added = new ArrayList<>();
        for (String photoRef : current.keySet()) {
            if (!known.contains(photoRef)) {
                added.add(new EnrollmentPhoto(orgKey, entityName, objectId, photoRef, now));
            }
        }
        if (added.isEmpty()) {
            return new Sync(null, dropped);
        }
        VisionJobTicket ticket = tickets.save(VisionJobTicket.open(orgKey, VisionJobTicket.Kind.ENROLLMENT, now));
        added.forEach(photo -> photo.assignTo(ticket.getJobId()));
        photos.saveAll(added);
        return new Sync(ticket.getJobId(), dropped);
    }

    private void finish(Sync sync) {
        if (sync.ticketId() != null) {
            visionJobs.dispatch(sync.ticketId());
        }
        discardCrops(sync.dropped());
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
                        photo.getPhotoRef(),
                        photo.getStatus(),
                        photoUrl(photo.getPhotoRef()),
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

    /** A photo URL the directory cannot sign leaves the entry without one rather than failing the view. */
    private String photoUrl(String photoRef) {
        try {
            return subjects.photoUrl(photoRef, settings.getReadUrlExpiry());
        } catch (RuntimeException e) {
            return null;
        }
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

    /** Only the crops are this module's; the photos stay with the subject. */
    private void discardCrops(List<EnrollmentPhoto> removed) {
        for (EnrollmentPhoto photo : removed) {
            if (photo.getCropObjectName() != null) {
                try {
                    stores.get().delete(photo.getCropObjectName());
                } catch (RuntimeException e) {
                    LOG.warn("crop {} not deleted: {}", photo.getCropObjectName(), e.getMessage());
                }
            }
        }
    }

    /** What a reconciliation left to do outside its transaction. */
    private record Sync(UUID ticketId, List<EnrollmentPhoto> dropped) {
        static final Sync NONE = new Sync(null, List.of());
    }
}
