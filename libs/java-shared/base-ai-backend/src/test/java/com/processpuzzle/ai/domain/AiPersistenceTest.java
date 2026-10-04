package com.processpuzzle.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The mappings bind: the embedded matching settings, the embedding as a binary column of real length,
 * and the derived queries the use cases rely on. On H2 — the PostgreSQL caveat base-app's persistence
 * test records applies here too; {@code byte[]} maps to {@code bytea} there.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.ai.domain")
@EnableJpaRepositories("com.processpuzzle.ai.domain")
class AiPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private RecognitionProfileRepository profiles;
    @Autowired
    private EnrollmentPhotoRepository photos;
    @Autowired
    private VisionJobTicketRepository tickets;
    @Autowired
    private RecognitionRepository recognitions;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void profileRoundTripsWithItsMatchingSettings() {
        RecognitionProfile profile = new RecognitionProfile("my-org", "Boat");
        profile.setName("Sailboat by sail number");
        profile.setDetectorClass("boat");
        profile.setGalleryAttributeKey("photos");
        profile.setIdentifierAttributeKey("sailNumber");
        profile.setMatching(new MatchingSettings(0.7, 0.8, 0.15, 2));
        profiles.saveAndFlush(profile);

        RecognitionProfile reloaded = profiles.findByOrgKeyAndEntityName("my-org", "Boat").orElseThrow();
        assertThat(reloaded.getMatching().getIdentifierWeight()).isEqualTo(0.7);
        assertThat(reloaded.getMatching().getSampleFps()).isEqualTo(2);
        assertThat(reloaded.readsIdentifier()).isTrue();
        assertThat(reloaded.getGalleryAttributeKey()).isEqualTo("photos");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(profiles.findByOrgKey("other-org", PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void anEnrolledPhotoKeepsAWholeEmbedding() {
        UUID objectId = UUID.randomUUID();
        EnrollmentPhoto photo = new EnrollmentPhoto("my-org", "Boat", objectId, "artifact-1", Instant.now());
        byte[] vector = new byte[384 * 4];
        vector[vector.length - 1] = 7;
        photo.enrolled("my-org/crops/x.jpg", "facebook/dinov2-small", vector, "GER 1234");
        photos.saveAndFlush(photo);
        EnrollmentPhoto pending = photos.saveAndFlush(new EnrollmentPhoto("my-org", "Boat", objectId, "artifact-2", Instant.now()));

        EnrollmentPhoto reloaded = photos.findById(photo.getPhotoId()).orElseThrow();
        assertThat(reloaded.getEmbedding()).hasSize(384 * 4).endsWith(7);
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentPhotoStatus.ENROLLED);
        assertThat(reloaded.getPhotoRef()).isEqualTo("artifact-1");
        assertThat(photos.existsByOrgKeyAndEntityName("my-org", "Boat")).isTrue();
        assertThat(photos.findByOrgKeyAndEntityNameAndObjectIdOrderByAddedAt("my-org", "Boat", objectId))
                .extracting(EnrollmentPhoto::getPhotoId).containsExactly(photo.getPhotoId(), pending.getPhotoId());
        assertThat(photos.findByOrgKeyAndEntityNameAndObjectIdInAndStatus("my-org", "Boat",
                List.of(objectId, UUID.randomUUID()), EnrollmentPhotoStatus.ENROLLED))
                .extracting(EnrollmentPhoto::getPhotoId).containsExactly(photo.getPhotoId());
    }

    @Test
    void aRecognitionRoundTripsWithItsListsAndRanking() {
        UUID frame = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Recognition recognition = new Recognition("my-org", "Boat", List.of(frame), List.of(first, second), UUID.randomUUID(),
                Instant.now().minusSeconds(7200));
        recognition.withoutGallery(List.of(second));
        recognition.answered(first, 0.9, "GER 1", 0.8, "my-org/recognitions/r.jpg",
                List.of(new Recognition.RankedCandidate(first, 0.9, 1.0, 0.8), new Recognition.RankedCandidate(second, 0.2, null, null)),
                Instant.now());
        recognitions.saveAndFlush(recognition);
        entityManager.clear();

        Recognition reloaded = recognitions.findByOrgKeyAndRecognitionId("my-org", recognition.getRecognitionId()).orElseThrow();
        assertThat(reloaded.getFrameMediaKeys()).containsExactly(frame);
        assertThat(reloaded.getCandidateObjectIds()).containsExactly(first, second);
        assertThat(reloaded.getCandidatesWithoutGallery()).containsExactly(second);
        assertThat(reloaded.getOutcome()).isEqualTo(Recognition.Outcome.MATCHED);
        assertThat(reloaded.getRanking()).extracting(Recognition.RankedCandidate::getObjectId).containsExactly(first, second);
        assertThat(reloaded.getRanking().getLast().getIdentifierScore()).isNull();
        assertThat(recognitions.findByOrgKeyAndRecognitionId("other-org", recognition.getRecognitionId())).isEmpty();
        assertThat(recognitions.findByVisionJobId(recognition.getVisionJobId())).isPresent();
        assertThat(recognitions.findByCreatedAtBefore(Instant.now().minusSeconds(3600)))
                .extracting(Recognition::getRecognitionId).containsExactly(recognition.getRecognitionId());
        assertThat(recognitions.findByCreatedAtBefore(Instant.now().minusSeconds(10_000))).isEmpty();
    }

    @Test
    void overdueTicketsAreFoundByLastTransition() {
        Instant past = Instant.now().minusSeconds(3600);
        VisionJobTicket stale = tickets.saveAndFlush(VisionJobTicket.open("my-org", VisionJobTicket.Kind.ENROLLMENT, past));
        VisionJobTicket fresh = VisionJobTicket.open("my-org", VisionJobTicket.Kind.ENROLLMENT, past);
        fresh.submitted(Instant.now());
        tickets.saveAndFlush(fresh);

        var overdue = tickets.findByStatusInAndTouchedAtBefore(
                EnumSet.of(VisionJobTicket.Status.SUBMITTING, VisionJobTicket.Status.SUBMITTED), Instant.now().minusSeconds(60));

        assertThat(overdue).extracting(VisionJobTicket::getJobId).containsExactly(stale.getJobId());
    }
}
