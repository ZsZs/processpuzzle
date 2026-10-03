package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
        when(attributes.attributeKind("boat", "sailNumber")).thenReturn(Optional.of(EntityAttributeKind.TEXT));
        when(attributes.attributeKind("boat", "length")).thenReturn(Optional.of(EntityAttributeKind.NUMBER));
        when(objects.find("boat", boat)).thenReturn(new EntityObjectView(boat, 1, Map.of("sailNumber", "GER 1234")));
        when(objects.find("boat", missing)).thenThrow(new EntityObjectAccessException.NotFound("boat", missing));

        SubjectDirectory subjects = configuration.aiSubjectDirectory(attributes, objects);

        assertThat(subjects.isTextAttribute("my-org", "boat", "sailNumber")).isTrue();
        assertThat(subjects.isTextAttribute("my-org", "boat", "length")).isFalse();
        assertThat(subjects.subjectExists("my-org", "boat", boat)).isTrue();
        assertThat(subjects.subjectExists("my-org", "boat", missing)).isFalse();
        assertThat(subjects.identifier("my-org", "boat", boat, "sailNumber")).hasValue("GER 1234");
        assertThat(subjects.identifier("my-org", "boat", missing, "sailNumber")).isEmpty();
    }
}
