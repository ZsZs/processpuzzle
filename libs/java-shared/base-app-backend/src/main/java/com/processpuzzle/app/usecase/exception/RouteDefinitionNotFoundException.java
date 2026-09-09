package com.processpuzzle.app.usecase.exception;

/**
 * The route does not exist, or exists but no nav entry the caller can see reaches it. Both cases
 * are surfaced as 404 on purpose — distinguishing them would disclose that a route the caller may
 * not reach exists.
 */
public class RouteDefinitionNotFoundException extends RuntimeException {

    public RouteDefinitionNotFoundException(String orgKey, String appId, String routePath) {
        super("Route definition not found: " + orgKey + "/" + appId + "/" + routePath);
    }
}
