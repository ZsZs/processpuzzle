package com.processpuzzle.document.usecase.exception;

/**
 * The slug is already taken in this organization — mapped to 409. Separate from
 * {@link DocumentAlreadyExistsException} because they are different collisions with different
 * fixes: an id collision means something generated a duplicate UUID, while this one means an
 * author has to pick another name.
 */
public class DocumentSlugAlreadyExistsException extends RuntimeException {

    public DocumentSlugAlreadyExistsException(String orgKey, String slug) {
        super("A document with slug '" + slug + "' already exists in organization '" + orgKey + "'");
    }
}
