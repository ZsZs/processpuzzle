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

    /**
     * The referring ids, structured. Note the HTTP body no longer carries them as a separate key — the
     * contract's {@code ErrorResponse} is exactly {@code errorId} plus {@code errorText}, and the message
     * above already names them. This accessor stays because it is the only structured form, and because
     * it is where a future declared {@code details} schema would read from.
     */
    public List<String> getReferencingBlockIds() {
        return referencingBlockIds;
    }
}
