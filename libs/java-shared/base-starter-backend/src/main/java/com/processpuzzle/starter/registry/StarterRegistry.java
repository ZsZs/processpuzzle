package com.processpuzzle.starter.registry;

/**
 * Outbound port to wherever the Business Starters are published. The one adapter,
 * {@link HttpStarterRegistry}, reads the public {@code processpuzzle-starters} bucket.
 */
public interface StarterRegistry {

    /**
     * @throws RegistryUnavailableException if the catalog cannot be read
     */
    StarterCatalog catalog();

    /**
     * The bytes of one bundle, unverified: checking them against the catalog's sha256 is the caller's.
     *
     * @param path relative to the registry, as {@link StarterCatalog.Version#bundle()} names it
     * @throws RegistryUnavailableException if the bundle cannot be read
     */
    byte[] bundle(String path);
}
