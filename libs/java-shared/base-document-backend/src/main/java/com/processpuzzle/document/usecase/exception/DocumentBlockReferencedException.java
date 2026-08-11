package com.processpuzzle.document.usecase.exception;

import java.util.List;

/**
 * Thrown by {@code DeleteDocumentBlock} when the block being removed is a REFERENCED widget
 * block still pointed at by a {@code widgetEmbed} node or another widget's
 * {@code props.childIds}. Carries the pointing block ids so the caller can report exactly
 * what needs to be un-wired first.
 */
public class DocumentBlockReferencedException extends RuntimeException {

    private final List<String> referencingBlockIds;

    public DocumentBlockReferencedException(String blockId, List<String> referencingBlockIds) {
        super("Block '" + blockId + "' is still referenced by: " + referencingBlockIds);
        this.referencingBlockIds = List.copyOf(referencingBlockIds);
    }

    public List<String> getReferencingBlockIds() {
        return referencingBlockIds;
    }
}
