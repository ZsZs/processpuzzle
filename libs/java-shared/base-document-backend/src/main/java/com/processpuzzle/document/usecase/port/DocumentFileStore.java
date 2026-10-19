package com.processpuzzle.document.usecase.port;

/**
 * Outbound port to object storage, for the PDFs of signed reports. The adapter decides the bucket and
 * the object id; this module keeps what it returns.
 *
 * <p>The adapter lives in the composition root, over processpuzzle-store, so that this library does not
 * compile against another module. There is no permissive default: with no adapter wired,
 * attaching a PDF is refused with {@code DocumentFileStoreUnavailableException}, answered as 503.
 */
public interface DocumentFileStore {

    StoredFile store(byte[] content, String fileName, String mimeType);

    /** Where a stored file is, as an ARTIFACT value records it. */
    record StoredFile(String bucket, String objectId) {
    }
}
