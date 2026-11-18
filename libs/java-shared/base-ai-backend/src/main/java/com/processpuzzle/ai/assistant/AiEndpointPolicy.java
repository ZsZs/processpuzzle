package com.processpuzzle.ai.assistant;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * What address an organization may point its assistant at. The backend makes the call, so an address inside the
 * platform's network (the database, the identity provider, the cloud metadata service) must not be reachable this way:
 * only https, and only hosts that resolve to public addresses.
 */
public final class AiEndpointPolicy {

    private AiEndpointPolicy() {
    }

    /** @throws IllegalArgumentException with a reason fit to show the administrator */
    public static void require(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The base URL is not a valid address");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("The base URL must be an https address without credentials");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocalV6(address)) {
                    throw new IllegalArgumentException("The base URL must point to a public host");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("The base URL's host cannot be resolved");
        }
    }

    private static boolean isUniqueLocalV6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
