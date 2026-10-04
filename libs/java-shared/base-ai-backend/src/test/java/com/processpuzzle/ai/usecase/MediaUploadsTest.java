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
        MediaUploads.Slot slot = uploads.create(ORG, MediaPurpose.RECOGNITION_FRAME, "Image/JPEG", 1_000_000);

        assertThat(slot.uploadUrl()).isEqualTo("http://minio/put");
        assertThat(slot.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        assertThat(slot.upload().getObjectName()).startsWith(ORG + "/uploads/");
    }

    @Test
    void refusesAMissingPurposeAnUnsupportedTypeAndABadSize() {
        assertThatThrownBy(() -> uploads.create(ORG, null, "image/jpeg", 10))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.invalid");
        assertThatThrownBy(() -> uploads.create(ORG, MediaPurpose.RECOGNITION_FRAME, "video/mp4", 10))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.content-type-unsupported");
        assertThatThrownBy(() -> uploads.create(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 0))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.invalid");
        assertThatThrownBy(() -> uploads.create(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 21L * 1024 * 1024))
                .isInstanceOfSatisfying(AiRequestException.class,
                        e -> assertThat(e.getKind()).isEqualTo(AiRequestException.Kind.TOO_LARGE));
    }

    @Test
    void aClaimNeedsTheRightPurposeAFinishedUploadAndALiveSlot() {
        MediaUpload frame = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 1, Instant.now().plusSeconds(60));
        String key = frame.getMediaKey().toString();
        when(repository.findByOrgKeyAndMediaKey(ORG, frame.getMediaKey())).thenReturn(Optional.of(frame));

        assertThatThrownBy(() -> uploads.claim(ORG, key, null))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.wrong-purpose");

        when(store.exists(frame.getObjectName())).thenReturn(false);
        assertThatThrownBy(() -> uploads.claim(ORG, key, MediaPurpose.RECOGNITION_FRAME))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.not-uploaded");

        when(store.exists(frame.getObjectName())).thenReturn(true);
        assertThat(uploads.claim(ORG, key, MediaPurpose.RECOGNITION_FRAME).isUsed()).isTrue();
    }

    @Test
    void aSlotCanBeClaimedOnlyOnce() {
        MediaUpload frame = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 1, Instant.now().plusSeconds(60));
        String key = frame.getMediaKey().toString();
        when(repository.findByOrgKeyAndMediaKey(ORG, frame.getMediaKey())).thenReturn(Optional.of(frame));
        when(store.exists(frame.getObjectName())).thenReturn(true);
        uploads.claim(ORG, key, MediaPurpose.RECOGNITION_FRAME);

        assertThatThrownBy(() -> uploads.claim(ORG, key, MediaPurpose.RECOGNITION_FRAME))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.already-used");
    }

    @Test
    void anExpiredOrUnknownSlotCannotBeClaimed() {
        MediaUpload stale = new MediaUpload(ORG, MediaPurpose.RECOGNITION_FRAME, "image/jpeg", 1, Instant.now().minusSeconds(1));
        when(repository.findByOrgKeyAndMediaKey(ORG, stale.getMediaKey())).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> uploads.claim(ORG, stale.getMediaKey().toString(), MediaPurpose.RECOGNITION_FRAME))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.expired");
        assertThatThrownBy(() -> uploads.claim(ORG, "not-a-uuid", MediaPurpose.RECOGNITION_FRAME))
                .hasFieldOrPropertyWithValue("errorId", "ai.media.unknown");
    }
}
