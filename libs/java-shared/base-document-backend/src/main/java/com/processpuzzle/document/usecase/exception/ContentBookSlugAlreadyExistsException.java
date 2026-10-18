package com.processpuzzle.document.usecase.exception;

/**
 * The slug is already taken by another content book of this organization — mapped to 409. Books and
 * documents have separate slug spaces: a book and a page may share a name.
 */
public class ContentBookSlugAlreadyExistsException extends RuntimeException {

    public ContentBookSlugAlreadyExistsException(String orgKey, String slug) {
        super("A content book with slug '" + slug + "' already exists in organization '" + orgKey + "'");
    }
}
