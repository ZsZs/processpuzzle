package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.api.BaseAppAssetsApi;
import com.processpuzzle.app.model.ModuleAssetInfo;
import com.processpuzzle.app.usecase.DeleteAppAsset;
import com.processpuzzle.app.usecase.GetAppAssetContent;
import com.processpuzzle.app.usecase.ListAppAssets;
import com.processpuzzle.app.usecase.ModuleAssetSummary;
import com.processpuzzle.app.usecase.UploadAppAsset;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.logging.LogClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the assets of an app, implementing the generated {@link BaseAppAssetsApi}: {@link ModuleAssetEndpoint}
 * one level up, serving content the same way (see {@link AssetResponses}).
 */
@RestController
@LogClass
public class AppAssetEndpoint implements BaseAppAssetsApi {

    private final UploadAppAsset upload;
    private final ListAppAssets list;
    private final GetAppAssetContent content;
    private final DeleteAppAsset delete;

    public AppAssetEndpoint(UploadAppAsset upload, ListAppAssets list, GetAppAssetContent content, DeleteAppAsset delete) {
        this.upload = upload;
        this.list = list;
        this.content = content;
        this.delete = delete;
    }

    @Override
    public ResponseEntity<List<ModuleAssetInfo>> listAppAssets(String orgKey, String appId, String revision) {
        return ResponseEntity.ok(list.execute(orgKey, appId, Channel.ofRevision(revision)).stream()
                .map(AppAssetEndpoint::toModel).toList());
    }

    @Override
    public ResponseEntity<ModuleAssetInfo> uploadAppAsset(String orgKey, String appId, MultipartFile file, String path) {
        try {
            return ResponseEntity.ok(toModel(upload.execute(orgKey, appId, path, file.getOriginalFilename(),
                    file.getContentType(), file.getBytes())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Void> deleteAppAsset(String orgKey, String appId, String path) {
        delete.execute(orgKey, appId, path);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> getAppAssetContent(String orgKey, String appId, String path, String revision, String ifNoneMatch) {
        Channel channel = Channel.ofRevision(revision);
        return AssetResponses.serve(content.summaryOf(orgKey, appId, path, channel), ifNoneMatch,
                () -> content.execute(orgKey, appId, path, channel));
    }

    private static ModuleAssetInfo toModel(ModuleAssetSummary summary) {
        return new ModuleAssetInfo().path(summary.path()).mimeType(summary.mimeType()).size(summary.size())
                .sha256(summary.sha256());
    }
}
