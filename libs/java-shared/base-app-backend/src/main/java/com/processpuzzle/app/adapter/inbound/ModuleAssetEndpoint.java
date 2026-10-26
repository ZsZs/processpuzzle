package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.api.BaseAppModuleAssetsApi;
import com.processpuzzle.app.model.ModuleAssetInfo;
import com.processpuzzle.app.usecase.DeleteModuleAsset;
import com.processpuzzle.app.usecase.GetModuleAssetContent;
import com.processpuzzle.app.usecase.ListModuleAssets;
import com.processpuzzle.app.usecase.ModuleAssetContent;
import com.processpuzzle.app.usecase.ModuleAssetSummary;
import com.processpuzzle.app.usecase.UploadModuleAsset;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.logging.LogClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the assets of a module, implementing the generated {@link BaseAppModuleAssetsApi}.
 *
 * <p>Content is streamed from here rather than redirected to object storage, and a user uploaded it, so what
 * the browser may do with it is decided here: it is never sniffed into another type, and the types that can
 * carry script of their own are either sandboxed (SVG, which an {@code <img>} renders harmlessly but a direct
 * visit would execute) or downloaded rather than shown (HTML).
 */
@RestController
@LogClass
public class ModuleAssetEndpoint implements BaseAppModuleAssetsApi {

    /** Rendered by a browser as a document, with whatever script that document carries. */
    private static final Set<String> DOWNLOAD_ONLY = Set.of("text/html", "application/xhtml+xml");
    private static final String SANDBOX = "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:";

    private final UploadModuleAsset upload;
    private final ListModuleAssets list;
    private final GetModuleAssetContent content;
    private final DeleteModuleAsset delete;

    public ModuleAssetEndpoint(UploadModuleAsset upload, ListModuleAssets list, GetModuleAssetContent content,
                               DeleteModuleAsset delete) {
        this.upload = upload;
        this.list = list;
        this.content = content;
        this.delete = delete;
    }

    @Override
    public ResponseEntity<List<ModuleAssetInfo>> listModuleAssets(String orgKey, String moduleKey, String revision) {
        return ResponseEntity.ok(list.execute(orgKey, moduleKey, Channel.ofRevision(revision)).stream()
                .map(ModuleAssetEndpoint::toModel).toList());
    }

    @Override
    public ResponseEntity<ModuleAssetInfo> uploadModuleAsset(String orgKey, String moduleKey, MultipartFile file, String path) {
        try {
            return ResponseEntity.ok(toModel(upload.execute(orgKey, moduleKey, path, file.getOriginalFilename(),
                    file.getContentType(), file.getBytes())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Void> deleteModuleAsset(String orgKey, String moduleKey, String path) {
        delete.execute(orgKey, moduleKey, path);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> getModuleAssetContent(String orgKey, String moduleKey, String path, String revision,
                                                          String ifNoneMatch) {
        Channel channel = Channel.ofRevision(revision);
        ModuleAssetSummary known = content.find(orgKey, moduleKey, path, channel);
        String etag = "\"" + known.sha256() + "\"";
        // Revalidated on every use rather than cached outright: the same path holds other content after the
        // next upload or release, so only the hash says whether what the browser has is still the asset.
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate());
        if (matches(ifNoneMatch, known.sha256())) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).build();
        }
        ModuleAssetContent asset = content.execute(orgKey, moduleKey, path, channel);
        response.header("X-Content-Type-Options", "nosniff").contentType(MediaType.parseMediaType(asset.mimeType()));
        if ("image/svg+xml".equals(asset.mimeType())) {
            response.header("Content-Security-Policy", SANDBOX);
        }
        if (DOWNLOAD_ONLY.contains(asset.mimeType())) {
            response.header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(asset.path().substring(asset.path().lastIndexOf('/') + 1)).build().toString());
        }
        return response.body(new ByteArrayResource(asset.content()));
    }

    private static boolean matches(String ifNoneMatch, String sha256) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.trim();
            if (tag.startsWith("W/")) {
                tag = tag.substring(2);
            }
            if (tag.equals("\"" + sha256 + "\"") || "*".equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static ModuleAssetInfo toModel(ModuleAssetSummary summary) {
        return new ModuleAssetInfo().path(summary.path()).mimeType(summary.mimeType()).size(summary.size())
                .sha256(summary.sha256());
    }
}
