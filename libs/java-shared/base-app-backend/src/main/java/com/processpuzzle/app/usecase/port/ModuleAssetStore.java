package com.processpuzzle.app.usecase.port;

import java.util.Optional;

/**
 * Outbound port to the object store that holds the bytes of module assets. base-app records which path of
 * which module holds which content; this keeps the content, named by its hash so that identical files are
 * stored once.
 *
 * <p>The adapter lives in the composition root, over processpuzzle-store, so that this library does not
 * compile against another module. There is no permissive default: with no adapter wired, an upload or a read
 * of an asset is refused with {@code ModuleAssetStoreUnavailableException}, answered as 503. Listing and
 * removing the records still work.
 */
public interface ModuleAssetStore {

    /** Stores the content under its hash; storing what is already there is harmless. */
    void put(String orgKey, String sha256, byte[] content, String mimeType);

    Optional<byte[]> get(String orgKey, String sha256);

    void delete(String orgKey, String sha256);

    /** Everything of one organization, for erasing the tenant. */
    void deleteAll(String orgKey);
}
