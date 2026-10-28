package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.api.BaseAppDefinitionTranslationsApi;
import com.processpuzzle.app.model.AppTranslationUploadLanguage;
import com.processpuzzle.app.model.AppTranslationUploadReport;
import com.processpuzzle.app.usecase.DeleteAppTranslation;
import com.processpuzzle.app.usecase.DownloadAppTranslations;
import com.processpuzzle.app.usecase.GetAppTranslationBundle;
import com.processpuzzle.app.usecase.UploadAppTranslations;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.logging.LogClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the translation files of an app, implementing the generated
 * {@link BaseAppDefinitionTranslationsApi}. The use cases decide; this only moves bytes in and out and maps the
 * results onto the contract's models.
 */
@RestController
@LogClass
public class AppDefinitionTranslationEndpoint implements BaseAppDefinitionTranslationsApi {

    private final DownloadAppTranslations download;
    private final UploadAppTranslations upload;
    private final GetAppTranslationBundle bundle;
    private final DeleteAppTranslation delete;

    public AppDefinitionTranslationEndpoint(DownloadAppTranslations download, UploadAppTranslations upload,
                                            GetAppTranslationBundle bundle, DeleteAppTranslation delete) {
        this.download = download;
        this.upload = upload;
        this.bundle = bundle;
        this.delete = delete;
    }

    @Override
    public ResponseEntity<Resource> downloadAppTranslations(String orgKey, String appId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + appId + "-translations.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new ByteArrayResource(download.execute(orgKey, appId)));
    }

    @Override
    public ResponseEntity<AppTranslationUploadReport> uploadAppTranslations(String orgKey, String appId, MultipartFile file) {
        try {
            var result = upload.execute(orgKey, appId, file.getOriginalFilename(), file.getInputStream());
            return ResponseEntity.ok(new AppTranslationUploadReport().languages(result.languages().stream()
                    .map(language -> new AppTranslationUploadLanguage().locale(language.locale()).storedKeys(language.storedKeys()))
                    .toList()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> getAppDefinitionTranslationBundle(String orgKey, String appId, String locale,
                                                                                 String revision) {
        return ResponseEntity.ok(Map.copyOf(bundle.execute(orgKey, appId, locale, Channel.ofRevision(revision))));
    }

    @Override
    public ResponseEntity<Void> deleteAppTranslation(String orgKey, String appId, String locale) {
        delete.execute(orgKey, appId, locale);
        return ResponseEntity.noContent().build();
    }
}
