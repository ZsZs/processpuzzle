package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.usecase.ModuleAssetContent;
import com.processpuzzle.app.usecase.ModuleAssetSummary;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * How an asset's content is served, for the module and the app endpoints alike. It was uploaded by a user, so
 * what the browser may do with it is decided here: it is never sniffed into another type, and the types that
 * can carry script of their own are either sandboxed (SVG, which an {@code <img>} renders harmlessly but a
 * direct visit would execute) or downloaded rather than shown (HTML).
 */
final class AssetResponses {

    /** Rendered by a browser as a document, with whatever script that document carries. */
    private static final Set<String> DOWNLOAD_ONLY = Set.of("text/html", "application/xhtml+xml");
    private static final String SANDBOX = "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:";

    private AssetResponses() {
    }

    /**
     * 304 when {@code ifNoneMatch} names the content's hash, else the file. The bytes are only read for the
     * second case, so a revalidation costs the record and not the object store.
     */
    static ResponseEntity<Resource> serve(ModuleAssetSummary known, String ifNoneMatch, Supplier<ModuleAssetContent> content) {
        String etag = "\"" + known.sha256() + "\"";
        // Revalidated on every use rather than cached outright: the same path holds other content after the
        // next upload or release, so only the hash says whether what the browser has is still the asset.
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate());
        if (matches(ifNoneMatch, known.sha256())) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).build();
        }
        ModuleAssetContent asset = content.get();
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
}
