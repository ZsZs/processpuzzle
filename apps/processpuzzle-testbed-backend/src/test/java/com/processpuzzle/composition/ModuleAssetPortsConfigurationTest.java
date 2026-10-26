package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.store.usecases.outbound.FileStorageService;
import com.processpuzzle.store.usecases.outbound.StoredObject;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Where module assets land: one bucket per stack, objects named by organization and content. */
class ModuleAssetPortsConfigurationTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final ModuleAssetPortsConfiguration configuration = new ModuleAssetPortsConfiguration();

    @Test
    void storesContentUnderTheOrganizationAndItsHashInTheStacksModulesBucket() {
        when(storage.bucketExists("stage-modules")).thenReturn(false);

        configuration.moduleAssetStore(storage, "stage").put("my-org", "abc", new byte[] {1}, "text/markdown");

        verify(storage).createBucket("stage-modules");
        verify(storage).uploadObject(eq("stage-modules"), eq("my-org/abc"), any(), eq("text/markdown"), anyMap());
    }

    @Test
    void storingContentThatIsAlreadyThereWritesNothing() {
        when(storage.bucketExists("modules")).thenReturn(true);
        when(storage.objectExists("modules", "my-org/abc")).thenReturn(true);

        configuration.moduleAssetStore(storage, "").put("my-org", "abc", new byte[] {1}, "text/markdown");

        verify(storage, never()).uploadObject(any(), any(), any(), any(), any());
    }

    @Test
    void readsTheBytesBack() {
        when(storage.bucketExists("modules")).thenReturn(true);
        when(storage.objectExists("modules", "my-org/abc")).thenReturn(true);
        when(storage.getObject("modules", "my-org/abc")).thenReturn(new StoredObject(new ByteArrayInputStream(new byte[] {7, 8}), Map.of()));

        var bytes = configuration.moduleAssetStore(storage, "").get("my-org", "abc");

        assertThat(bytes).isPresent();
        assertThat(bytes.get()).containsExactly(7, 8);
    }

    @Test
    void answersAMissingObjectOrBucketWithNothingRatherThanAnError() {
        var store = configuration.moduleAssetStore(storage, "");
        assertThat(store.get("my-org", "abc")).isEmpty();

        when(storage.bucketExists("modules")).thenReturn(true);
        assertThat(store.get("my-org", "abc")).isEmpty();
    }

    @Test
    void erasesATenantByItsPrefix() {
        configuration.moduleAssetStore(storage, "").deleteAll("my-org");

        verify(storage).deleteByPrefix("modules", "my-org/");
    }

    @Test
    void deletesOneObjectOnlyWhereTheBucketExists() {
        var store = configuration.moduleAssetStore(storage, "");
        store.delete("my-org", "abc");
        verify(storage, never()).deleteObject(any(), any());

        when(storage.bucketExists("modules")).thenReturn(true);
        store.delete("my-org", "abc");
        verify(storage).deleteObject("modules", "my-org/abc");
    }
}
