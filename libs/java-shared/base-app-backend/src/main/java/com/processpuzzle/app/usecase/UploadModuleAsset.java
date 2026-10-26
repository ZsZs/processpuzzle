package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;
import com.processpuzzle.app.usecase.exception.ModuleAssetStoreUnavailableException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.app.usecase.service.ModuleAssetPaths;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds or replaces one asset of a module draft. The content goes to the object store under its hash; the
 * record says which path holds it. Replacing a path with other content leaves the old bytes to the sweeper,
 * which removes them once nothing else names them.
 */
@Service
@Transactional
public class UploadModuleAsset {

    /** Per asset. Generous for markdown and images, small enough that a PUT is never an archive in disguise. */
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    /** Per module and channel. */
    public static final int MAX_ASSETS = 500;

    private static final String OCTET_STREAM = MediaType.APPLICATION_OCTET_STREAM_VALUE;

    private final ModuleDefinitionRepository modules;
    private final ModuleAssetRepository assets;
    private final ObjectProvider<ModuleAssetStore> store;
    private final ModuleAssetSweeper sweeper;
    private final OrganizationGuard guard;

    public UploadModuleAsset(ModuleDefinitionRepository modules, ModuleAssetRepository assets,
                             ObjectProvider<ModuleAssetStore> store, ModuleAssetSweeper sweeper,
                             OrganizationGuard guard) {
        this.modules = modules;
        this.assets = assets;
        this.store = store;
        this.sweeper = sweeper;
        this.guard = guard;
    }

    /**
     * @param path where in the module the file goes; the file's own name, at the root, when blank
     * @param fileName the uploaded file's name, used for the default path and to guess a missing content type
     */
    public ModuleAssetSummary execute(String orgKey, String moduleKey, String path, String fileName,
                                      String contentType, byte[] content) {
        guard.requireDesign(orgKey);
        if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)) {
            throw new ModuleDefinitionNotFoundException(orgKey, moduleKey);
        }
        String target = ModuleAssetPaths.require(path == null || path.isBlank() ? ModuleAssetPaths.fileNameOf(fileName) : path);
        if (content.length == 0) {
            throw new ModuleAssetRejectedException("'" + target + "' is empty.");
        }
        if (content.length > MAX_BYTES) {
            throw new ModuleAssetRejectedException("'" + target + "' is larger than " + MAX_BYTES + " bytes.");
        }
        ModuleAssetStore objects = store.getIfAvailable();
        if (objects == null) {
            throw new ModuleAssetStoreUnavailableException("No object store is configured for module assets.");
        }

        Optional<ModuleAsset> existing = assets.findByOrgKeyAndModuleKeyAndChannelAndPath(orgKey, moduleKey, Channel.DRAFT, target);
        if (existing.isEmpty() && assets.countByOrgKeyAndModuleKeyAndChannel(orgKey, moduleKey, Channel.DRAFT) >= MAX_ASSETS) {
            throw new ModuleAssetRejectedException("A module holds at most " + MAX_ASSETS + " assets.");
        }
        String sha256 = sha256Of(content);
        String mimeType = mimeTypeOf(contentType, target);
        objects.put(orgKey, sha256, content, mimeType);

        ModuleAsset asset = existing.orElseGet(() -> new ModuleAsset(orgKey, moduleKey, Channel.DRAFT, target, sha256, mimeType, content.length));
        String replaced = existing.map(ModuleAsset::getSha256).orElse(null);
        asset.replaceContent(sha256, mimeType, content.length);
        assets.save(asset);
        if (replaced != null && !replaced.equals(sha256)) {
            sweeper.sweep(orgKey, List.of(replaced));
        }
        return new ModuleAssetSummary(target, mimeType, content.length, sha256);
    }

    static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform", e);
        }
    }

    /** The declared type without its parameters, else a guess from the extension, else octet-stream. */
    static String mimeTypeOf(String declared, String path) {
        if (declared != null && !declared.isBlank()) {
            try {
                MediaType type = MediaType.parseMediaType(declared);
                if (!type.isWildcardType() && !type.isWildcardSubtype() && !OCTET_STREAM.equals(type.getType() + "/" + type.getSubtype())) {
                    return type.getType() + "/" + type.getSubtype();
                }
            } catch (InvalidMediaTypeException ignored) {
                // fall through to the extension
            }
        }
        return MediaTypeFactory.getMediaType(path).map(type -> type.getType() + "/" + type.getSubtype()).orElse(OCTET_STREAM);
    }
}
