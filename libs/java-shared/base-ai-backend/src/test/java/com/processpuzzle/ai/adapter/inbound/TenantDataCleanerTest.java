package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.processpuzzle.ai.domain.EnrollmentPhoto;
import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.domain.Recognition;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionProfileRepository;
import com.processpuzzle.ai.domain.AssistantSession;
import com.processpuzzle.ai.domain.AssistantSessionRepository;
import com.processpuzzle.ai.domain.RecognitionRepository;
import com.processpuzzle.ai.domain.VisionJobTicket;
import com.processpuzzle.ai.domain.VisionJobTicketRepository;
import com.processpuzzle.ai.usecase.MediaStores;
import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.MediaStoreUnavailableException;
import com.processpuzzle.ai.usecase.port.VisionServer;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erasing one tenant removes every row this feature holds for it, and nothing of another's; its media
 * and its open vision jobs go only once that has committed.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its deletes discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction.
 *
 * <p>H2 in PostgreSQL mode, as base-rule's cleaner test, so the native collection-table deletes run in
 * the dialect they are written for.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.ai.domain")
@EnableJpaRepositories("com.processpuzzle.ai.domain")
@Import({TenantDataCleaner.class, MediaStores.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantDataCleanerTest {

    private static final String ERASED = "erased-org";
    private static final String KEPT = "kept-org";
    private static final List<String> COLLECTION_TABLES = List.of("ai_recognition_frames",
            "ai_recognition_candidate_ids", "ai_recognition_without_gallery", "ai_recognition_ranking");

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        MediaStore mediaStore() {
            return mock(MediaStore.class);
        }

        @Bean
        VisionServer visionServer() {
            return mock(VisionServer.class);
        }
    }

    @Autowired
    private RecognitionRepository recognitions;
    @Autowired
    private EnrollmentPhotoRepository photos;
    @Autowired
    private MediaUploadRepository uploads;
    @Autowired
    private VisionJobTicketRepository tickets;
    @Autowired
    private RecognitionProfileRepository profiles;
    @Autowired
    private AssistantSessionRepository assistantSessions;
    @Autowired
    private MediaStore mediaStore;
    @Autowired
    private VisionServer visionServer;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void erasesTheAssistantSessionsOfTheTenantWithTheirMessagesAndKeepsTheOthers() {
        givenAssistantSessionFor(ERASED);
        givenAssistantSessionFor(KEPT);

        erase(ERASED);

        assertThat(assistantSessions.findAll()).extracting(AssistantSession::getOrgKey).containsExactly(KEPT);
    }

    private void givenAssistantSessionFor(String orgKey) {
        AssistantSession session = new AssistantSession(orgKey, "ada-subject", Instant.now());
        session.append("user", "Define an invoice.", Instant.now());
        assistantSessions.save(session);
    }

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenDataFor(ERASED);
        givenDataFor(KEPT);

        erase(ERASED);

        assertThat(count("ai_recognitions", ERASED)).isZero();
        assertThat(count("ai_enrollment_photos", ERASED)).isZero();
        assertThat(count("ai_media_uploads", ERASED)).isZero();
        assertThat(count("ai_vision_job_tickets", ERASED)).isZero();
        assertThat(count("ai_recognition_profiles", ERASED)).isZero();
        COLLECTION_TABLES.forEach(table -> assertThat(countCollection(table, ERASED)).as(table).isZero());
        assertThat(totalCollectionRows()).as("only the kept tenant's collection rows remain")
                .isEqualTo(COLLECTION_TABLES.stream().mapToInt(table -> countCollection(table, KEPT)).sum());

        assertThat(count("ai_recognitions", KEPT)).isEqualTo(1);
        assertThat(count("ai_enrollment_photos", KEPT)).isEqualTo(1);
        assertThat(count("ai_media_uploads", KEPT)).isEqualTo(1);
        assertThat(count("ai_vision_job_tickets", KEPT)).isEqualTo(3);
        assertThat(count("ai_recognition_profiles", KEPT)).isEqualTo(1);
        COLLECTION_TABLES.forEach(table -> assertThat(countCollection(table, KEPT)).as(table).isPositive());
    }

    /** Only once the rows are committed gone, and only the jobs the vision server may still be running. */
    @Test
    void deletesTheTenantsMediaAndDiscardsItsOpenJobsAfterCommit() {
        TenantData erased = givenDataFor(ERASED);
        TenantData kept = givenDataFor(KEPT);

        erase(ERASED);

        verify(mediaStore).deleteByPrefix(ERASED + "/");
        verify(mediaStore, never()).deleteByPrefix(KEPT + "/");
        verify(visionServer).discard(erased.submitting());
        verify(visionServer).discard(erased.submitted());
        verify(visionServer, never()).discard(erased.completed());
        verify(visionServer, never()).discard(kept.submitting());
        verify(visionServer, never()).discard(kept.submitted());
    }

    @Test
    void aRolledBackErasureDeletesNothingAndCallsNothingOutside() {
        givenDataFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(count("ai_recognitions", ERASED)).isEqualTo(1);
        assertThat(count("ai_vision_job_tickets", ERASED)).isEqualTo(3);
        COLLECTION_TABLES.forEach(table -> assertThat(countCollection(table, ERASED)).as(table).isPositive());
        verifyNoInteractions(mediaStore, visionServer);
    }

    /** The rows are gone by then; storage or a vision server that is down must not undo or fail that. */
    @Test
    void aFailingMediaStoreOrVisionServerDoesNotFailTheErasure() {
        TenantData erased = givenDataFor(ERASED);
        doThrow(new MediaStoreUnavailableException("down")).when(mediaStore).deleteByPrefix(anyString());
        doThrow(new IllegalStateException("down")).when(visionServer).discard(any());

        erase(ERASED);

        assertThat(count("ai_recognitions", ERASED)).isZero();
        verify(visionServer).discard(erased.submitting());
        verify(visionServer).discard(erased.submitted());
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNothingIsHarmless() {
        erase("nobody");

        assertThat(recognitions.count()).isZero();
        verify(mediaStore).deleteByPrefix("nobody/");
        verify(visionServer, never()).discard(any());
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    private TenantData givenDataFor(String orgKey) {
        Instant now = Instant.now();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        RecognitionProfile profile = new RecognitionProfile(orgKey, "Boat");
        profile.setName("Boats");
        profile.setDetectorClass("boat");
        profiles.saveAndFlush(profile);
        photos.saveAndFlush(new EnrollmentPhoto(orgKey, "Boat", first, "artifacts/" + first, now));
        MediaUpload frame = uploads.saveAndFlush(new MediaUpload(orgKey, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 10,
                now.plusSeconds(600)));

        VisionJobTicket submitting = tickets.saveAndFlush(VisionJobTicket.open(orgKey, VisionJobTicket.Kind.ENROLLMENT, now));
        VisionJobTicket submitted = VisionJobTicket.open(orgKey, VisionJobTicket.Kind.RECOGNITION, now);
        submitted.submitted(now);
        tickets.saveAndFlush(submitted);
        VisionJobTicket completed = VisionJobTicket.open(orgKey, VisionJobTicket.Kind.RECOGNITION, now);
        completed.completed(now);
        tickets.saveAndFlush(completed);

        Recognition recognition = new Recognition(orgKey, "Boat", List.of(frame.getMediaKey()), List.of(first, second),
                completed.getJobId(), now);
        recognition.withoutGallery(List.of(second));
        recognition.answered(first, 0.9, "GER 1", 0.8, orgKey + "/recognitions/r.jpg",
                List.of(new Recognition.RankedCandidate(first, 0.9, 1.0, 0.8),
                        new Recognition.RankedCandidate(second, 0.2, null, null)), now);
        recognitions.saveAndFlush(recognition);

        return new TenantData(submitting.getJobId(), submitted.getJobId(), completed.getJobId());
    }

    private int count(String table, String orgKey) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where org_key = ?", Integer.class, orgKey);
        return count == null ? 0 : count;
    }

    private int countCollection(String table, String orgKey) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " c join ai_recognitions r"
                + " on r.recognition_id = c.recognition_id where r.org_key = ?", Integer.class, orgKey);
        return count == null ? 0 : count;
    }

    private int totalCollectionRows() {
        return COLLECTION_TABLES.stream()
                .mapToInt(table -> {
                    Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
                    return count == null ? 0 : count;
                })
                .sum();
    }

    private record TenantData(UUID submitting, UUID submitted, UUID completed) {
    }

    @AfterEach
    void cleanUp() {
        recognitions.deleteAll();
        photos.deleteAll();
        uploads.deleteAll();
        tickets.deleteAll();
        profiles.deleteAll();
        reset(mediaStore, visionServer);
    }
}
