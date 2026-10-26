package com.processpuzzle.app.usecase.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs outside a transaction here, so what is deferred in production runs at once. */
class ModuleAssetSweeperTest {

    private final ModuleAssetRepository assets = mock(ModuleAssetRepository.class);
    private final ModuleAssetStore store = mock(ModuleAssetStore.class);

    @SuppressWarnings("unchecked")
    private ModuleAssetSweeper sweeper(ModuleAssetStore target) {
        ObjectProvider<ModuleAssetStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(target);
        return new ModuleAssetSweeper(assets, provider);
    }

    @Test
    void removesContentNothingNamesAnyMoreAndKeepsContentSomethingStillDoes() {
        when(assets.existsByOrgKeyAndSha256("org", "kept")).thenReturn(true);

        sweeper(store).sweep("org", List.of("kept", "orphan"));

        verify(store).delete("org", "orphan");
        verify(store, never()).delete("org", "kept");
    }

    @Test
    void aFailedRemovalNeitherFailsTheCallerNorStopsTheRest() {
        doThrow(new IllegalStateException("minio down")).when(store).delete("org", "a");

        sweeper(store).sweep("org", List.of("a", "b"));

        verify(store).delete("org", "b");
    }

    @Test
    void withNoStoreWiredThereIsNothingToSweep() {
        ModuleAssetSweeper unwired = sweeper(null);

        assertThatCode(() -> unwired.sweep("org", List.of("a"))).doesNotThrowAnyException();
        assertThatCode(() -> unwired.eraseAll("org")).doesNotThrowAnyException();
        verify(assets, never()).existsByOrgKeyAndSha256(any(), any());
    }

    @Test
    void erasesATenantsContentWhole() {
        sweeper(store).eraseAll("org");

        verify(store).deleteAll("org");
    }

    @Test
    void anEmptyListTouchesNothing() {
        sweeper(store).sweep("org", List.of());

        verify(assets, never()).existsByOrgKeyAndSha256(any(), any());
    }

    // The point of the class: bytes must not go before the rows that name them are certain to have gone.
    @Test
    void insideATransactionItWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            sweeper(store).sweep("org", List.of("a"));
            verify(store, never()).delete(any(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(store).delete("org", "a");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
