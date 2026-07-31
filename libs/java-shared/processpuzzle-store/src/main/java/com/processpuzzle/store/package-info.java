/**
 * ProcessPuzzle Store: object storage for entity documents, images and configuration, backed by
 * MinIO (or Firebase Storage in the serverless topology).
 *
 * <p>Exposes {@code store :: storage} — the {@code FileStorageService} abstraction and the
 * {@code StoredObject} it hands back. The MinIO adapter and its configuration stay internal: a
 * consumer that reaches for {@code MinioClient} directly has bound itself to one topology.
 */
@ApplicationModule(displayName = "ProcessPuzzle Store", allowedDependencies = {"core", "shared"})
package com.processpuzzle.store;

import org.springframework.modulith.ApplicationModule;
