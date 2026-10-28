package com.processpuzzle.app.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleTranslationRejectedException;
import com.processpuzzle.app.usecase.port.SupportedLanguages;
import com.processpuzzle.app.usecase.service.AppTranslationReleases;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** The app translation use cases against real repositories. */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class AppTranslationUseCasesTest {

    private static final String ORG = "my-org";
    private static final String APP = "app";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private AppDefinitionRepository apps;
    @Autowired
    private AppDefinitionTranslationRepository translations;
    @Autowired
    private EntityManager entityManager;

    private UploadAppTranslations upload;
    private GetAppTranslationBundle bundle;
    private DeleteAppTranslation delete;
    private DownloadAppTranslations download;
    private AppTranslationReleases releases;

    @BeforeEach
    void setUp() {
        wire(AppTestFixtures.permissiveGuard(), null);
        apps.save(new AppDefinition(ORG, APP, "App", null, null, AppTestFixtures.validGraph()));
        flush();
    }

    @SuppressWarnings("unchecked")
    private void wire(OrganizationGuard guard, SupportedLanguages supported) {
        ObjectProvider<SupportedLanguages> supportedProvider = mock(ObjectProvider.class);
        when(supportedProvider.getIfAvailable()).thenReturn(supported);
        ObjectProvider<com.processpuzzle.app.usecase.port.TenantDirectory> tenants = mock(ObjectProvider.class);
        when(tenants.getIfAvailable()).thenReturn(null);
        var languages = new ModuleTranslationLanguages(supportedProvider, tenants);
        upload = new UploadAppTranslations(apps, translations, languages, guard);
        bundle = new GetAppTranslationBundle(apps, translations, guard);
        delete = new DeleteAppTranslation(apps, translations, guard);
        download = new DownloadAppTranslations(apps, translations, languages, guard);
        releases = new AppTranslationReleases(translations);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private AppTranslationUploadResult put(String file, String json) {
        var result = upload.execute(ORG, APP, file, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        flush();
        return result;
    }

    // --- upload --------------------------------------------------------------------------

    @Test
    void storesTheFlatTextsOfALanguageInTheDraftChannel() {
        var result = put("de.json", "{\"home.cards.plan.title\": \"Rennen planen\", \"home.cards.plan.text\": \"Regatten\"}");

        assertThat(result.languages()).singleElement().satisfies(language -> {
            assertThat(language.locale()).isEqualTo("de");
            assertThat(language.storedKeys()).isEqualTo(2);
        });
        assertThat(bundle.execute(ORG, APP, "de", Channel.DRAFT)).containsEntry("home.cards.plan.title", "Rennen planen");
        assertThat(translations.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void flattensANestedFileAndAcceptsAnyKeysTheWidgetsName() {
        put("en.json", "{\"home\": {\"cards\": {\"plan\": {\"title\": \"Plan Race\"}}}, \"other_key-1\": \"x\"}");

        assertThat(bundle.execute(ORG, APP, "en", Channel.DRAFT))
                .containsOnly(Map.entry("home.cards.plan.title", "Plan Race"), Map.entry("other_key-1", "x"));
    }

    @Test
    void anUploadReplacesTheWholeBundleOfALanguage() {
        put("en.json", "{\"a\": \"1\", \"b\": \"2\"}");
        put("en.json", "{\"a\": \"3\"}");

        assertThat(bundle.execute(ORG, APP, "en", Channel.DRAFT)).containsOnly(Map.entry("a", "3"));
    }

    @Test
    void storesEveryLanguageOfAZipOrNothing() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("en.json"));
            zip.write("{\"a\": \"1\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("de.json"));
            zip.write("{\"bad key!\": \"2\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThatThrownBy(() -> upload.execute(ORG, APP, "all.zip", new ByteArrayInputStream(bytes.toByteArray())))
                .isInstanceOf(ModuleTranslationRejectedException.class).hasMessageContaining("bad key!");
        assertThat(translations.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
    }

    @Test
    void refusesKeysAndTextsOutsideTheLimits() {
        assertThatThrownBy(() -> put("en.json", "{\"a..b\": \"1\"}")).isInstanceOf(ModuleTranslationRejectedException.class);
        assertThatThrownBy(() -> put("en.json", "{\".a\": \"1\"}")).isInstanceOf(ModuleTranslationRejectedException.class);
        assertThatThrownBy(() -> put("en.json", "{\"" + "k".repeat(201) + "\": \"1\"}")).isInstanceOf(ModuleTranslationRejectedException.class);
        assertThatThrownBy(() -> put("en.json", "{\"a\": \"" + "t".repeat(4001) + "\"}")).isInstanceOf(ModuleTranslationRejectedException.class)
                .hasMessageContaining("longer than");
        assertThatThrownBy(() -> put("en.json", "{\"a\": 1}")).isInstanceOf(ModuleTranslationRejectedException.class);
    }

    @Test
    void refusesALanguageTheOrganizationDoesNotSupport() {
        wire(AppTestFixtures.permissiveGuard(), new SupportedLanguages() {
            @Override
            public List<String> codes(String orgKey) {
                return List.of("en", "de");
            }
        });

        assertThatThrownBy(() -> put("fr.json", "{\"a\": \"1\"}")).isInstanceOf(ModuleTranslationRejectedException.class)
                .hasMessageContaining("'fr' is not supported");
    }

    @Test
    void anUnknownAppIsNotFound() {
        assertThatThrownBy(() -> upload.execute(ORG, "nope", "en.json", new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AppDefinitionNotFoundException.class);
        assertThatThrownBy(() -> bundle.execute(ORG, "nope", "en", Channel.DRAFT)).isInstanceOf(AppDefinitionNotFoundException.class);
        assertThatThrownBy(() -> delete.execute(ORG, "nope", "en")).isInstanceOf(AppDefinitionNotFoundException.class);
        assertThatThrownBy(() -> download.execute(ORG, "nope")).isInstanceOf(AppDefinitionNotFoundException.class);
    }

    @Test
    void writingNeedsADesignPermissionAndTheDraftReadToo() {
        wire(AppTestFixtures.denyingGuard(), null);

        assertThatThrownBy(() -> upload.execute(ORG, APP, "en.json", new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> delete.execute(ORG, APP, "en")).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> download.execute(ORG, APP)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> bundle.execute(ORG, APP, "en", Channel.DRAFT)).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    // --- read, delete, download -----------------------------------------------------------

    @Test
    void aLanguageNothingIsStoredForIsAnEmptyBundle() {
        assertThat(bundle.execute(ORG, APP, "hu", Channel.RELEASED)).isEmpty();
    }

    @Test
    void deletingTheDraftOfALanguageLeavesTheReleasedOneUntilTheNextRelease() {
        put("en.json", "{\"a\": \"1\"}");
        releases.release(ORG, APP);
        flush();

        delete.execute(ORG, APP, "en");
        flush();

        assertThat(bundle.execute(ORG, APP, "en", Channel.DRAFT)).isEmpty();
        assertThat(bundle.execute(ORG, APP, "en", Channel.RELEASED)).containsOnly(Map.entry("a", "1"));

        releases.release(ORG, APP);
        flush();
        assertThat(bundle.execute(ORG, APP, "en", Channel.RELEASED)).isEmpty();
    }

    @Test
    void deletingALanguageNothingIsStoredForIsHarmless() {
        delete.execute(ORG, APP, "en");
    }

    @Test
    void theDownloadHoldsEveryStoredLanguageAndAnEmptyFileForThePreparedOne() throws IOException {
        put("de.json", "{\"a\": \"1\"}");

        Map<String, String> files = new java.util.LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(download.execute(ORG, APP)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", ""));
            }
        }

        assertThat(files).containsEntry("en.json", "{}").containsEntry("de.json", "{\"a\":\"1\"}");
    }

    // --- release -------------------------------------------------------------------------

    @Test
    void releaseMakesTheReleasedBundlesEqualToTheDraftOnes() {
        put("en.json", "{\"a\": \"1\"}");
        put("de.json", "{\"a\": \"2\"}");
        releases.release(ORG, APP);
        flush();
        assertThat(translations.findByOrgKeyAndAppIdAndChannel(ORG, APP, Channel.RELEASED)).hasSize(2);

        put("en.json", "{\"a\": \"changed\"}");
        delete.execute(ORG, APP, "de");
        flush();
        releases.release(ORG, APP);
        flush();

        assertThat(bundle.execute(ORG, APP, "en", Channel.RELEASED)).containsOnly(Map.entry("a", "changed"));
        assertThat(translations.findByOrgKeyAndAppIdAndChannel(ORG, APP, Channel.RELEASED)).extracting("locale").containsExactly("en");
    }

    @Test
    void removeAllDeletesBothChannelsOfOneAppOnly() {
        apps.save(new AppDefinition(ORG, "other", "Other", null, null, AppTestFixtures.validGraph()));
        put("en.json", "{\"a\": \"1\"}");
        upload.execute(ORG, "other", "en.json", new ByteArrayInputStream("{\"a\": \"1\"}".getBytes(StandardCharsets.UTF_8)));
        releases.release(ORG, APP);
        flush();

        releases.removeAll(ORG, APP);
        flush();

        assertThat(translations.findByOrgKeyAndAppId(ORG, APP)).isEmpty();
        assertThat(translations.findByOrgKeyAndAppId(ORG, "other")).hasSize(1);
    }

    @Test
    void byAppCanonicalizesTheBundlesSoEqualTextsFingerprintEqually() {
        put("en.json", "{\"b\": \"2\", \"a\": \"1\"}");

        assertThat(releases.byApp(ORG, Channel.DRAFT)).containsEntry(APP, Map.of("en", "{a=1, b=2}"));
        assertThat(releases.byApp(ORG, Channel.RELEASED)).isEmpty();
    }
}
