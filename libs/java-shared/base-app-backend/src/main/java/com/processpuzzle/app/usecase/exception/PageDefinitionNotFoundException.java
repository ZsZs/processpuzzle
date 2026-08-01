package com.processpuzzle.app.usecase.exception;

/**
 * The page does not exist, or exists but no nav entry the caller can see reaches it. Both cases
 * are surfaced as 404 on purpose — distinguishing them would disclose that a page the caller may
 * not reach exists.
 */
public class PageDefinitionNotFoundException extends RuntimeException {

    public PageDefinitionNotFoundException(String orgKey, String appId, String pageId) {
        super("Page definition not found: " + orgKey + "/" + appId + "/" + pageId);
    }
}
