package com.processpuzzle.app.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import com.processpuzzle.app.usecase.port.SupportedLanguages;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.app.usecase.service.ModuleTranslationFiles;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * The four translation use cases against a real repository. What they pin down is the round trip a translator
 * makes — download, edit, upload — and what each side refuses or merely reports.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class ModuleTranslationUseCasesTest {

    private static final String ORG = "my-org";
    private static final String MODULE = "claims";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private ModuleDefinitionRepository modules;
    @Autowired
    private ModuleTranslationRepository translations;
    @Autowired
    private EntityManager entityManager;

    private DownloadModuleTranslations download;
    private UploadModuleTranslations upload;
    private GetModuleTranslationCoverage coverage;
    private GetModuleTranslationBundle bundle;

    @BeforeEach
    void setUp() {
        languages(List.of("en", "de", "fr"), "en");
        ModuleDefinition module = new ModuleDefinition(ORG, MODULE, "Claims", Channel.DRAFT);
        module.setRoutes(List.of(route("list", "Claim list"), route("claim/:id", "Claim")));
        modules.save(module);
        flush();
    }

    private void languages(List<String> supported, String defaultLocale) {
        SupportedLanguages port = new SupportedLanguages() {
            @Override
            public List<String> codes(String orgKey) {
                return supported;
            }
        };
        var languages = new ModuleTranslationLanguages(providerOf(port),
                AppTestFixtures.tenantDirectoryWithLocale(ORG, defaultLocale));
        download = new DownloadModuleTranslations(modules, translations, languages, AppTestFixtures.permissiveGuard());
        upload = new UploadModuleTranslations(modules, translations, languages, AppTestFixtures.permissiveGuard());
        coverage = new GetModuleTranslationCoverage(modules, translations, languages, AppTestFixtures.permissiveGuard());
        bundle = new GetModuleTranslationBundle(modules, translations, AppTestFixtures.permissiveGuard());
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    private static AppRoute route(String path, String title) {
        return AppTestFixtures.appRoute(path, title);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private static byte[] zipOf(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private ModuleTranslationUploadResult uploadZip(Map<String, String> files) throws IOException {
        return upload.execute(ORG, MODULE, "t.zip", new ByteArrayInputStream(zipOf(files)));
    }

    private Map<String, Map<String, String>> downloaded() throws IOException {
        Map<String, Map<String, String>> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(download.execute(ORG, MODULE)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.putAll(ModuleTranslationFiles.read(entry.getName(), new ByteArrayInputStream(zip.readAllBytes())));
            }
        }
        return files;
    }

    // --- download ------------------------------------------------------------------------

    @Test
    void theDownloadHoldsOneCompleteFilePerSupportedLanguage_startingFromTheAuthoredTexts() throws IOException {
        Map<String, Map<String, String>> files = downloaded();

        assertThat(files).containsOnlyKeys("en", "de", "fr");
        assertThat(files.get("de")).containsExactly(
                Map.entry("module.name", "Claims"),
                Map.entry("route.list", "Claim list"),
                Map.entry("route.claim/:id", "Claim"));
    }

    @Test
    void theDownloadCarriesWhatWasUploadedBeforeAndTheAuthoredTextForTheRest() throws IOException {
        uploadZip(Map.of("de.json", "{\"route.list\": \"Schadenliste\"}"));
        flush();

        assertThat(downloaded().get("de")).containsEntry("route.list", "Schadenliste")
                .containsEntry("module.name", "Claims");
    }

    @Test
    void theDownloadLeadsWithTheDefaultLanguageEvenWhenTheSupportedListLeavesItOut() throws IOException {
        languages(List.of("de", "fr"), "en");

        assertThat(downloaded()).containsOnlyKeys("en", "de", "fr");
    }

    @Test
    void withNoLanguagesNamedOnlyTheDefaultOneIsPrepared() throws IOException {
        languages(List.of(), "hu");

        assertThat(downloaded()).containsOnlyKeys("hu");
    }

    // --- upload --------------------------------------------------------------------------

    @Test
    void anUploadReplacesTheLanguagesItNamesAndLeavesTheOthers() throws IOException {
        uploadZip(Map.of("de.json", "{\"module.name\": \"Schaeden\"}", "fr.json", "{\"module.name\": \"Sinistres\"}"));
        flush();
        uploadZip(Map.of("de.json", "{\"route.list\": \"Liste\"}"));
        flush();

        assertThat(translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(ORG, MODULE, Channel.DRAFT, "de"))
                .hasValueSatisfying(de -> assertThat(de.getMessages()).containsExactly(Map.entry("route.list", "Liste")));
        assertThat(translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(ORG, MODULE, Channel.DRAFT, "fr"))
                .hasValueSatisfying(fr -> assertThat(fr.getMessages()).containsEntry("module.name", "Sinistres"));
    }

    @Test
    void anUploadWritesTheDraftOnly() throws IOException {
        uploadZip(Map.of("de.json", "{\"module.name\": \"Schaeden\"}"));
        flush();

        assertThat(translations.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void theReportNamesUnknownMissingAndUntranslatedKeys() throws IOException {
        var report = uploadZip(Map.of("de.json",
                "{\"module.name\": \"Claims\", \"route.list\": \"Liste\", \"route.gone\": \"x\"}"));

        assertThat(report.languages()).singleElement().satisfies(de -> {
            assertThat(de.locale()).isEqualTo("de");
            assertThat(de.storedKeys()).isEqualTo(2);
            assertThat(de.unknownKeys()).containsExactly("route.gone");
            assertThat(de.missingKeys()).containsExactly("route.claim/:id");
            assertThat(de.untranslatedKeys()).containsExactly("module.name");
        });
    }

    @Test
    void theDefaultLanguageIsNeverReportedUntranslated() throws IOException {
        var report = uploadZip(Map.of("en.json", "{\"module.name\": \"Claims\"}"));

        assertThat(report.languages().getFirst().untranslatedKeys()).isEmpty();
    }

    @Test
    void unknownKeysAreDroppedNotStored() throws IOException {
        uploadZip(Map.of("de.json", "{\"route.gone\": \"x\", \"module.name\": \"Schaeden\"}"));
        flush();

        assertThat(translations.findByOrgKeyAndModuleKeyAndChannelAndLocale(ORG, MODULE, Channel.DRAFT, "de")
                .orElseThrow().getMessages()).containsOnlyKeys("module.name");
    }

    @Test
    void aLanguageThatIsNotSupportedRejectsTheWholeUpload() {
        Map<String, String> files = Map.of("de.json", "{}", "it.json", "{}");

        assertThatThrownBy(() -> uploadZip(files))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("'it'");
        flush();

        assertThat(translations.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
    }

    @Test
    void theSpellingOfTheSupportedListWinsOverTheFilesName() throws IOException {
        languages(List.of("en", "pt-BR"), "en");

        assertThat(uploadZip(Map.of("pt-br.json", "{}")).languages()).extracting(
                ModuleTranslationUploadResult.Language::locale).containsExactly("pt-BR");
    }

    @Test
    void withNoLanguagesNamedAnyWellFormedTagIsAccepted() throws IOException {
        languages(List.of(), "en");

        assertThat(uploadZip(Map.of("it.json", "{}")).languages()).hasSize(1);
    }

    @Test
    void aMissingModuleIsNotFound() {
        ByteArrayInputStream body = new ByteArrayInputStream("{}".getBytes());

        assertThatThrownBy(() -> upload.execute(ORG, "nope", "de.json", body))
                .isInstanceOf(ModuleDefinitionNotFoundException.class);
        assertThatThrownBy(() -> download.execute(ORG, "nope")).isInstanceOf(ModuleDefinitionNotFoundException.class);
        assertThatThrownBy(() -> coverage.execute(ORG, "nope")).isInstanceOf(ModuleDefinitionNotFoundException.class);
    }

    @Test
    void designRightsAreRequiredToPrepareAndToUpload() {
        var denied = AppTestFixtures.denyingGuard();
        var languages = new ModuleTranslationLanguages(ModuleTranslationUseCasesTest.<SupportedLanguages>providerOf(null),
                AppTestFixtures.noTenantDirectory());

        assertThatThrownBy(() -> new DownloadModuleTranslations(modules, translations, languages, denied)
                .execute(ORG, MODULE)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new UploadModuleTranslations(modules, translations, languages, denied)
                .execute(ORG, MODULE, "de.json", new ByteArrayInputStream("{}".getBytes())))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> new GetModuleTranslationCoverage(modules, translations, languages, denied)
                .execute(ORG, MODULE)).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    // --- coverage ------------------------------------------------------------------------

    @Test
    void coverageCountsOnlyValuesThatDifferFromTheAuthoredText() throws IOException {
        uploadZip(Map.of("de.json", "{\"module.name\": \"Claims\", \"route.list\": \"Liste\"}"));
        flush();

        var byLocale = new LinkedHashMap<String, ModuleTranslationCoverageResult>();
        coverage.execute(ORG, MODULE).forEach(result -> byLocale.put(result.locale(), result));

        assertThat(byLocale.get("en")).satisfies(en -> {
            assertThat(en.defaultLocale()).isTrue();
            assertThat(en.translatedCount()).isEqualTo(3);
            assertThat(en.missingKeys()).isEmpty();
        });
        assertThat(byLocale.get("de")).satisfies(de -> {
            assertThat(de.keyCount()).isEqualTo(3);
            assertThat(de.translatedCount()).isEqualTo(1);
            assertThat(de.missingKeys()).containsExactly("route.claim/:id");
        });
        assertThat(byLocale.get("fr").translatedCount()).isZero();
        assertThat(byLocale.get("fr").missingKeys()).hasSize(3);
    }

    // --- bundle --------------------------------------------------------------------------

    @Test
    void theBundleOfAChannelIsWhatWasStoredThere() throws IOException {
        uploadZip(Map.of("de.json", "{\"module.name\": \"Schaeden\"}"));
        translations.save(new ModuleTranslation(ORG, MODULE, Channel.RELEASED, "de", Map.of("module.name", "Alt")));
        modules.save(new ModuleDefinition(ORG, MODULE, "Claims", Channel.RELEASED));
        flush();

        assertThat(bundle.execute(ORG, MODULE, "de", Channel.DRAFT)).containsEntry("module.name", "Schaeden");
        assertThat(bundle.execute(ORG, MODULE, "de", Channel.RELEASED)).containsEntry("module.name", "Alt");
    }

    @Test
    void aLanguageNothingWasStoredForIsAnEmptyBundleNotAnError() {
        assertThat(bundle.execute(ORG, MODULE, "de", Channel.DRAFT)).isEmpty();
    }

    @Test
    void aModuleNotYetReleasedHasNoReleasedBundle() {
        assertThatThrownBy(() -> bundle.execute(ORG, MODULE, "de", Channel.RELEASED))
                .isInstanceOf(ModuleDefinitionNotFoundException.class);
    }

    @Test
    void theDefaultLocaleFallsBackToTheFirstSupportedLanguageThenEn() {
        var viaNoDirectory = new ModuleTranslationLanguages(providerOf(new SupportedLanguages() {
            @Override
            public List<String> codes(String orgKey) {
                return List.of("hu", "en");
            }
        }), providerOf((TenantDirectory) null));

        assertThat(viaNoDirectory.defaultLocale(ORG)).isEqualTo("hu");
        assertThat(new ModuleTranslationLanguages(ModuleTranslationUseCasesTest.<SupportedLanguages>providerOf(null),
                providerOf((TenantDirectory) null))
                .defaultLocale(ORG)).isEqualTo("en");
    }
}
