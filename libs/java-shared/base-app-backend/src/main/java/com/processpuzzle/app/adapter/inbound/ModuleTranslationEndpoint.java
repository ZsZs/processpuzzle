package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.api.BaseAppModuleTranslationsApi;
import com.processpuzzle.app.model.ModuleTranslationCoverage;
import com.processpuzzle.app.model.ModuleTranslationUploadLanguage;
import com.processpuzzle.app.model.ModuleTranslationUploadReport;
import com.processpuzzle.app.usecase.DownloadModuleTranslations;
import com.processpuzzle.app.usecase.GetModuleTranslationBundle;
import com.processpuzzle.app.usecase.GetModuleTranslationCoverage;
import com.processpuzzle.app.usecase.UploadModuleTranslations;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.logging.LogClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST adapter for the prepared translation files of a module, implementing the generated
 * {@link BaseAppModuleTranslationsApi}. The use cases decide; this only moves bytes in and out and maps the
 * results onto the contract's models.
 */
@RestController
@LogClass
public class ModuleTranslationEndpoint implements BaseAppModuleTranslationsApi {

    private final DownloadModuleTranslations download;
    private final UploadModuleTranslations upload;
    private final GetModuleTranslationCoverage coverage;
    private final GetModuleTranslationBundle bundle;

    public ModuleTranslationEndpoint(DownloadModuleTranslations download, UploadModuleTranslations upload,
                                     GetModuleTranslationCoverage coverage, GetModuleTranslationBundle bundle) {
        this.download = download;
        this.upload = upload;
        this.coverage = coverage;
        this.bundle = bundle;
    }

    @Override
    public ResponseEntity<Resource> downloadModuleTranslations(String orgKey, String moduleKey) {
        // The generated operation declares only application/zip, but the content type is set explicitly all
        // the same: it is also what makes the browser save a file rather than try to display one.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + moduleKey + "-translations.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new ByteArrayResource(download.execute(orgKey, moduleKey)));
    }

    @Override
    public ResponseEntity<ModuleTranslationUploadReport> uploadModuleTranslations(String orgKey, String moduleKey,
                                                                                  MultipartFile file) {
        try {
            var result = upload.execute(orgKey, moduleKey, file.getOriginalFilename(), file.getInputStream());
            return ResponseEntity.ok(new ModuleTranslationUploadReport().languages(result.languages().stream()
                    .map(language -> new ModuleTranslationUploadLanguage()
                            .locale(language.locale())
                            .storedKeys(language.storedKeys())
                            .unknownKeys(language.unknownKeys())
                            .missingKeys(language.missingKeys())
                            .untranslatedKeys(language.untranslatedKeys()))
                    .toList()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<List<ModuleTranslationCoverage>> getModuleTranslationCoverage(String orgKey,
                                                                                         String moduleKey) {
        return ResponseEntity.ok(coverage.execute(orgKey, moduleKey).stream()
                .map(language -> new ModuleTranslationCoverage()
                        .locale(language.locale())
                        .defaultLocale(language.defaultLocale())
                        .keyCount(language.keyCount())
                        .translatedCount(language.translatedCount())
                        .missingKeys(language.missingKeys()))
                .toList());
    }

    @Override
    public ResponseEntity<Map<String, Object>> getModuleTranslationBundle(String orgKey, String moduleKey,
                                                                          String locale, String revision) {
        return ResponseEntity.ok(Map.copyOf(bundle.execute(orgKey, moduleKey, locale, Channel.ofRevision(revision))));
    }
}
