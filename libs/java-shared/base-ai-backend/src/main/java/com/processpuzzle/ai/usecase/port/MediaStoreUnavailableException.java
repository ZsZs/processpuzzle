package com.processpuzzle.ai.usecase.port;

/** No {@link MediaStore} adapter is wired, or the storage refused the call. */
public class MediaStoreUnavailableException extends RuntimeException {

    public MediaStoreUnavailableException(String message) {
        super(message);
    }
}
