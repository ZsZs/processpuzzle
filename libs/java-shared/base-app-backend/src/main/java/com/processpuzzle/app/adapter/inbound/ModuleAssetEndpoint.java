package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.api.BaseAppModuleAssetsApi;
import com.processpuzzle.app.model.ModuleAssetInfo;
import com.processpuzzle.app.usecase.DeleteModuleAsset;
import com.processpuzzle.app.usecase.GetModuleAssetContent;
import com.processpuzzle.app.usecase.ListModuleAssets;
import com.processpuzzle.app.usecase.ModuleAssetSummary;
import com.processpuzzle.app.usecase.UploadModuleAsset;
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
 * REST adapter for the assets of a module, implementing the generated {@link BaseAppModuleAssetsApi}.
 *
 * <p>Content is streamed from here rather than redirected to object storage, and a user uploaded it, so what
 * the browser may do with it is decided here, in {@link AssetResponses}.
 */
@RestController
@LogClass
public class ModuleAssetEndpoint implements BaseAppModuleAssetsApi {

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
        return AssetResponses.serve(content.summaryOf(orgKey, moduleKey, path, channel), ifNoneMatch,
                () -> content.execute(orgKey, moduleKey, path, channel));
    }

    private static ModuleAssetInfo toModel(ModuleAssetSummary summary) {
        return new ModuleAssetInfo().path(summary.path()).mimeType(summary.mimeType()).size(summary.size())
                .sha256(summary.sha256());
    }
}
