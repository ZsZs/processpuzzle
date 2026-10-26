package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Removes content from the object store once nothing refers to it.
 *
 * <p>Content is shared — by two paths, and by a draft and a released row — so deleting an asset record says
 * nothing about its bytes; they go when the last record naming them is gone. It runs <em>after commit</em>: a
 * rolled-back transaction must not have deleted bytes its rows still name. Nothing here can fail the request
 * that triggered it: a failed removal leaves an unreferenced object behind, which wastes space and breaks
 * nothing.
 */
@Component
public class ModuleAssetSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleAssetSweeper.class);

    private final ModuleAssetRepository assets;
    private final ObjectProvider<ModuleAssetStore> store;

    public ModuleAssetSweeper(ModuleAssetRepository assets, ObjectProvider<ModuleAssetStore> store) {
        this.assets = assets;
        this.store = store;
    }

    /** Deletes each of {@code hashes} that no record of the organization names any more. */
    public void sweep(String orgKey, Collection<String> hashes) {
        if (hashes.isEmpty()) {
            return;
        }
        afterCommit(() -> {
            ModuleAssetStore target = store.getIfAvailable();
            if (target == null) {
                return;
            }
            for (String hash : hashes) {
                try {
                    if (!assets.existsByOrgKeyAndSha256(orgKey, hash)) {
                        target.delete(orgKey, hash);
                    }
                } catch (RuntimeException e) {
                    LOG.warn("Could not remove unreferenced module asset {}/{}: {}", orgKey, hash, e.getMessage());
                }
            }
        });
    }

    /** Deletes everything the organization has stored, for erasing the tenant. */
    public void eraseAll(String orgKey) {
        afterCommit(() -> {
            ModuleAssetStore target = store.getIfAvailable();
            if (target == null) {
                return;
            }
            try {
                target.deleteAll(orgKey);
            } catch (RuntimeException e) {
                LOG.warn("Could not erase the module assets of {}: {}", orgKey, e.getMessage());
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
