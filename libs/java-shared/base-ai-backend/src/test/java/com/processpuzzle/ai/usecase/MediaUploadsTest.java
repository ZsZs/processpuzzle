package com.processpuzzle.ai.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.AiProperties;
import com.processpuzzle.ai.domain.MediaPurpose;
import com.processpuzzle.ai.domain.MediaUpload;
import com.processpuzzle.ai.domain.MediaUploadRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MediaUploadsTest {

    private static final String ORG = "my-org";

    private MediaUploadRepository repository;
    private MediaStore store;
    private MediaUploads uploads;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(MediaUploadRepository.class);
        store = mock(MediaStore.class);
        ObjectProvider<MediaStore> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(store);
        when(repository.save(any(MediaUpload.class))).thenAnswer(call -> call.getArgument(0));
        when(store.uploadUrl(anyString(), anyString(), any())).thenReturn("http://minio/put");
        uploads = new MediaUploads(repository, new MediaStores(provider), new AiProperties(), mock(OrganizationGuard.class));
    }

    @Test
    void reservesASlotUnderTheOrganizationsPrefix() {
        MediaUploads.Slot slot = uploads.create(ORG, MediaPurpose.RECOGNITION_VIDEO, "Video/MP4", 1_000_000);

        assertThat(slot.uploadUrl()).isEqualTo("http://minio/put");
        assertThat(slot.requiredHeaders()).containsEntry("Content-Type", "video/mp4");
        assertThat(slot.upload().getObjectName()).startsWith(ORG + "/uploads/");
    }

    @Test
    void refusesAnUnsupportedTypeAndAnOversizedFile() {
        assertThatThrownBy(() -> uploads.create(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/heic", 10))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.content-type-unsupported");
        assertThatThrownBy(() -> uploads.create(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 41L * 1024 * 1024))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.TOO_LARGE));
    }

    @Test
    void aClaimNeedsTheRightPurposeAFinishedUploadAndALiveSlot() {
        MediaUpload video = new MediaUpload(ORG, MediaPurpose.RECOGNITION_VIDEO, "video/mp4", 1, Instant.now().plusSeconds(60));
        String key = video.getMediaKey().toString();
        when(repository.findByOrgKeyAndMediaKey(ORG, video.getMediaKey())).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> uploads.claim(ORG, key, MediaPurpose.ENROLLMENT_PHOTO))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.wrong-purpose");

        when(store.exists(video.getObjectName())).thenReturn(false);
        assertThatThrownBy(() -> uploads.claim(ORG, key, MediaPurpose.RECOGNITION_VIDEO))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.not-uploaded");

        when(store.exists(video.getObjectName())).thenReturn(true);
        assertThat(uploads.claim(ORG, key, MediaPurpose.RECOGNITION_VIDEO).isUsed()).isTrue();
    }

    @Test
    void anExpiredOrUnknownSlotCannotBeClaimed() {
        MediaUpload stale = new MediaUpload(ORG, MediaPurpose.ENROLLMENT_PHOTO, "image/jpeg", 1, Instant.now().minusSeconds(1));
        when(repository.findByOrgKeyAndMediaKey(ORG, stale.getMediaKey())).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> uploads.claim(ORG, stale.getMediaKey().toString(), MediaPurpose.ENROLLMENT_PHOTO))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.expired");
        assertThatThrownBy(() -> uploads.claim(ORG, "not-a-uuid", MediaPurpose.ENROLLMENT_PHOTO))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.unknown");
    }
}
