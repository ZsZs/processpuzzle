package com.processpuzzle.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumSet;
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
    private MediaUploadRepository uploads;
    @Autowired
    private EnrollmentPhotoRepository photos;
    @Autowired
    private VisionJobTicketRepository tickets;

    @Test
    void profileRoundTripsWithItsMatchingSettings() {
        RecognitionProfile profile = new RecognitionProfile("my-org", "Boat");
        profile.setName("Sailboat by sail number");
        profile.setDetectorClass("boat");
        profile.setIdentifierAttributeKey("sailNumber");
        profile.setMatching(new MatchingSettings(0.7, 0.8, 0.15, 2));
        profiles.saveAndFlush(profile);

        RecognitionProfile reloaded = profiles.findByOrgKeyAndEntityName("my-org", "Boat").orElseThrow();
        assertThat(reloaded.getMatching().getIdentifierWeight()).isEqualTo(0.7);
        assertThat(reloaded.getMatching().getSampleFps()).isEqualTo(2);
        assertThat(reloaded.readsIdentifier()).isTrue();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(profiles.findByOrgKey("other-org", PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void anEnrolledPhotoKeepsAWholeEmbedding() {
        MediaUpload upload = uploads.saveAndFlush(new MediaUpload("my-org", MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 10,
                Instant.now().plusSeconds(60)));
        EnrollmentPhoto photo = new EnrollmentPhoto(upload, "Boat", UUID.randomUUID(), Instant.now());
        byte[] vector = new byte[384 * 4];
        vector[vector.length - 1] = 7;
        photo.enrolled("my-org/crops/x.jpg", "facebook/dinov2-small", vector, "GER 1234");
        photos.saveAndFlush(photo);

        EnrollmentPhoto reloaded = photos.findById(photo.getPhotoId()).orElseThrow();
        assertThat(reloaded.getEmbedding()).hasSize(384 * 4).endsWith(7);
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentPhotoStatus.ENROLLED);
        assertThat(photos.existsByOrgKeyAndMediaKey("my-org", upload.getMediaKey())).isTrue();
        assertThat(photos.existsByOrgKeyAndEntityName("my-org", "Boat")).isTrue();
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
