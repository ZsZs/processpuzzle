package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.usecase.port.MediaStore;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.store.usecases.outbound.FileStorageService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiPortsConfigurationTest {

    private final AiPortsConfiguration configuration = new AiPortsConfiguration();

    @Test
    void mediaGoesToTheStacksOwnBucketCreatedOnce() {
        FileStorageService storage = mock(FileStorageService.class);
        MediaStore store = configuration.aiMediaStore(storage, "processpuzzle-testbed");

        store.put("my-org/crops/a.jpg", new byte[] {1}, "image/jpeg");
        store.uploadUrl("my-org/uploads/b", "video/mp4", Duration.ofMinutes(5));

        verify(storage, times(1)).createBucket("processpuzzle-testbed-ai-media");
        verify(storage).uploadObject(eq("processpuzzle-testbed-ai-media"), eq("my-org/crops/a.jpg"), any(), eq("image/jpeg"), anyMap());
        verify(storage).getUploadUri("processpuzzle-testbed-ai-media", "my-org/uploads/b", "video/mp4", Duration.ofMinutes(5));
    }

    @Test
    void withoutAPrefixTheBucketIsUnprefixed() {
        FileStorageService storage = mock(FileStorageService.class);
        configuration.aiMediaStore(storage, "").exists("x");

        verify(storage).objectExists("ai-media", "x");
    }

    @Test
    void subjectsAreBaseEntityObjects() {
        EntityAttributeQuery attributes = mock(EntityAttributeQuery.class);
        EntityObjectAccess objects = mock(EntityObjectAccess.class);
        UUID boat = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(attributes.attributeKind("my-org", "boat", "sailNumber")).thenReturn(Optional.of(EntityAttributeKind.TEXT));
        when(attributes.attributeKind("my-org", "boat", "length")).thenReturn(Optional.of(EntityAttributeKind.NUMBER));
        when(objects.find("boat", boat)).thenReturn(new EntityObjectView(boat, 1, Map.of("sailNumber", "GER 1234")));
        when(objects.find("boat", missing)).thenThrow(new EntityObjectAccessException.NotFound("boat", missing));

        SubjectDirectory subjects = configuration.aiSubjectDirectory(attributes, objects, mock(FileStorageService.class));

        assertThat(subjects.isTextAttribute("my-org", "boat", "sailNumber")).isTrue();
        assertThat(subjects.isTextAttribute("my-org", "boat", "length")).isFalse();
        assertThat(subjects.isTextAttribute("other-org", "boat", "sailNumber")).isFalse();
        assertThat(subjects.subjectExists("my-org", "boat", boat)).isTrue();
        assertThat(subjects.subjectExists("my-org", "boat", missing)).isFalse();
        assertThat(subjects.identifier("my-org", "boat", boat, "sailNumber")).hasValue("GER 1234");
        assertThat(subjects.identifier("my-org", "boat", missing, "sailNumber")).isEmpty();
    }

    @Test
    void photosAreTheImageArtifactsOfTheGalleryAttribute() {
        EntityAttributeQuery attributes = mock(EntityAttributeQuery.class);
        EntityObjectAccess objects = mock(EntityObjectAccess.class);
        UUID boat = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(attributes.attributeKind("my-org", "boat", "photos")).thenReturn(Optional.of(EntityAttributeKind.REFERENCE));
        when(attributes.attributeKind("my-org", "boat", "sailNumber")).thenReturn(Optional.of(EntityAttributeKind.TEXT));
        when(objects.find("boat", boat)).thenReturn(new EntityObjectView(boat, 1, Map.of("photos", List.of(
                Map.of("bucket", "artifacts", "objectId", "a.jpg", "name", "a.jpg", "mimeType", "image/jpeg"),
                Map.of("bucket", "artifacts", "objectId", "b.pdf", "name", "b.pdf", "mimeType", "application/pdf")))));
        when(objects.find("boat", missing)).thenThrow(new EntityObjectAccessException.NotFound("boat", missing));

        SubjectDirectory subjects = configuration.aiSubjectDirectory(attributes, objects, mock(FileStorageService.class));

        assertThat(subjects.isPhotoAttribute("my-org", "boat", "photos")).isTrue();
        assertThat(subjects.isPhotoAttribute("my-org", "boat", "sailNumber")).isFalse();
        assertThat(subjects.isPhotoAttribute("my-org", "boat", "unknown")).isFalse();
        assertThat(subjects.photos("my-org", "boat", boat, "photos"))
                .containsExactly(new SubjectDirectory.SubjectPhoto("artifacts/a.jpg", "image/jpeg"));
        assertThat(subjects.photos("my-org", "boat", boat, "unset")).isEmpty();
        assertThat(subjects.photos("my-org", "boat", missing, "photos")).isEmpty();
    }

    @Test
    void photoUrlsAreSignedByTheStoreFromTheBucketAndObjectOfTheReference() {
        FileStorageService storage = mock(FileStorageService.class);
        when(storage.getObjectUri("artifacts", "boats/a.jpg")).thenReturn("http://minio/public");
        when(storage.getInternalObjectUri("artifacts", "boats/a.jpg", Duration.ofHours(6))).thenReturn("http://minio:9000/internal");
        SubjectDirectory subjects = configuration.aiSubjectDirectory(mock(EntityAttributeQuery.class), mock(EntityObjectAccess.class), storage);

        assertThat(subjects.photoUrl("artifacts/boats/a.jpg", Duration.ofHours(1))).isEqualTo("http://minio/public");
        assertThat(subjects.internalPhotoUrl("artifacts/boats/a.jpg", Duration.ofHours(6))).isEqualTo("http://minio:9000/internal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-slash", "/object", "bucket/"})
    void aMalformedPhotoReferenceIsRejected(String photoRef) {
        SubjectDirectory subjects = configuration.aiSubjectDirectory(mock(EntityAttributeQuery.class), mock(EntityObjectAccess.class),
                mock(FileStorageService.class));

        assertThatThrownBy(() -> subjects.photoUrl(photoRef, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subjects.internalPhotoUrl(photoRef, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anArtifactValueMayBeASingleObjectAListOrNothing() {
        Map<String, Object> photo = Map.of("bucket", "artifacts", "objectId", "a.png", "mimeType", "image/png");

        assertThat(AiPortsConfiguration.artifactPhotos(photo))
                .containsExactly(new SubjectDirectory.SubjectPhoto("artifacts/a.png", "image/png"));
        assertThat(AiPortsConfiguration.artifactPhotos(List.of(photo, photo))).hasSize(2);
        assertThat(AiPortsConfiguration.artifactPhotos(null)).isEmpty();
        assertThat(AiPortsConfiguration.artifactPhotos(List.of())).isEmpty();
    }

    @Test
    void valuesThatAreNotImageArtifactsAreSkipped() {
        assertThat(AiPortsConfiguration.artifactPhotos(List.of(
                "a string",
                UUID.randomUUID(),
                Map.of("objectId", "a.jpg", "mimeType", "image/jpeg"),
                Map.of("bucket", " ", "objectId", "a.jpg", "mimeType", "image/jpeg"),
                Map.of("bucket", "artifacts", "mimeType", "image/jpeg"),
                Map.of("bucket", "artifacts", "objectId", "", "mimeType", "image/jpeg"),
                Map.of("bucket", "artifacts", "objectId", "a.jpg"),
                Map.of("bucket", "artifacts", "objectId", "a.mp4", "mimeType", "video/mp4"),
                Map.of("bucket", 1, "objectId", "a.jpg", "mimeType", "image/jpeg")))).isEmpty();
    }
}
