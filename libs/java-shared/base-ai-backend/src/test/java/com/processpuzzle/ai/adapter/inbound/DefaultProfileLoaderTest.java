package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(OutputCaptureExtension.class)
class DefaultProfileLoaderTest {

    private static final String LOCATION = "classpath*:default-recognition-profiles/*-recognition-profiles.yaml";
    private static final String FILE_NAME = "other-org-recognition-profiles.yaml";
    private static final String BOAT_PROFILE = """
            recognitionProfiles:
              - entityName: boat
                name: Sailboat
                detectorClass: boat
            """;

    private final RecognitionProfiles profiles = mock(RecognitionProfiles.class);
    private final ResourcePatternResolver resources = mock(ResourcePatternResolver.class);
    private final DefaultProfileLoader loader = new DefaultProfileLoader(profiles, new AiMapper(), resources);

    @Test
    void theBundledTestbedFileSeedsTheBoatProfileIntoItsOrganization() {
        when(profiles.seed(eq("processpuzzle-testbed"), any())).thenReturn(Optional.of(new RecognitionProfile("processpuzzle-testbed", "boat")));

        loader.load(new ClassPathResource("default-recognition-profiles/processpuzzle-testbed-recognition-profiles.yaml"));

        ArgumentCaptor<RecognitionProfiles.Draft> draft = ArgumentCaptor.forClass(RecognitionProfiles.Draft.class);
        verify(profiles).seed(eq("processpuzzle-testbed"), draft.capture());
        assertThat(draft.getValue().entityName()).isEqualTo("boat");
        assertThat(draft.getValue().detectorClass()).isEqualTo("boat");
        assertThat(draft.getValue().identifierAttributeKey()).isEqualTo("sailNumber");
        assertThat(draft.getValue().identifierPattern()).isEqualTo("^[A-Z]{3} ?[0-9]{1,5}$");
        assertThat(draft.getValue().matching().getAcceptScore()).isEqualTo(0.75);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"profiles.yaml", "-recognition-profiles.yaml"})
    void aFileNotNamedAfterAnOrganizationIsSkipped(String fileName, CapturedOutput output) {
        loader.load(yamlFile(fileName, BOAT_PROFILE));

        verify(profiles, never()).seed(any(), any());
        assertThat(output.getOut()).contains("does not follow the '<orgKey>-recognition-profiles.yaml' convention");
    }

    @Test
    void scansEveryFileAndUsesEachFileNamesOrganization() throws IOException {
        when(resources.getResources(LOCATION)).thenReturn(new Resource[] {
                yamlFile(FILE_NAME, BOAT_PROFILE),
                yamlFile("second-org-recognition-profiles.yaml", BOAT_PROFILE)
        });

        loader.loadDefaults();

        verify(profiles).seed(eq("other-org"), any());
        verify(profiles).seed(eq("second-org"), any());
    }

    @Test
    void anUnscannableClasspathIsLoggedWithoutFailingStartup(CapturedOutput output) throws IOException {
        when(resources.getResources(LOCATION)).thenThrow(new IOException("Classpath unavailable"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verifyNoInteractions(profiles);
        assertThat(output.getOut()).contains("Unable to scan for default recognition profiles", "Classpath unavailable");
    }

    @Test
    void anApplicationWithoutBundledDefaultsSeedsNothing() throws IOException {
        when(resources.getResources(LOCATION)).thenReturn(new Resource[0]);

        loader.loadDefaults();

        verifyNoInteractions(profiles);
    }

    @Test
    void anUnreadableResourceIsLoggedAndTheNextFileIsStillLoaded(CapturedOutput output) throws IOException {
        Resource unreadable = mock(Resource.class);
        when(unreadable.getFilename()).thenReturn("broken-recognition-profiles.yaml");
        when(unreadable.getInputStream()).thenThrow(new IOException("Read denied"));
        when(resources.getResources(LOCATION)).thenReturn(new Resource[] {unreadable, yamlFile(FILE_NAME, BOAT_PROFILE)});

        loader.loadDefaults();

        verify(profiles).seed(eq("other-org"), any());
        assertThat(output.getOut()).contains("Failed to read default recognition profiles from broken-recognition-profiles.yaml", "Read denied");
    }

    @ParameterizedTest
    @ValueSource(strings = {"recognitionProfiles: [", ""})
    void malformedOrEmptyYamlIsLoggedAndSkipped(String content, CapturedOutput output) {
        assertThatCode(() -> loader.load(yamlFile(FILE_NAME, content))).doesNotThrowAnyException();

        verifyNoInteractions(profiles);
        assertThat(output.getOut()).contains("Failed to read default recognition profiles from " + FILE_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "~"})
    void aNullYamlDocumentIsLoggedAndDoesNotPreventLoadingTheNextFile(String content, CapturedOutput output) throws IOException {
        when(resources.getResources(LOCATION)).thenReturn(new Resource[] {
                yamlFile("empty-recognition-profiles.yaml", content), yamlFile(FILE_NAME, BOAT_PROFILE)
        });

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(profiles).seed(eq("other-org"), any());
        assertThat(output.getOut()).contains("empty-recognition-profiles.yaml", "contains no document");
    }

    @ParameterizedTest
    @ValueSource(strings = {"recognitionProfiles: []", "recognitionProfiles: null", "note: no profiles yet"})
    void missingOrEmptyProfileListsSeedNothing(String content, CapturedOutput output) {
        loader.load(yamlFile(FILE_NAME, content));

        verifyNoInteractions(profiles);
        assertThat(output.getOut()).contains("created=0, already present=0, rejected=0");
    }

    @Test
    void existingProfilesAreCountedWithoutBeingCreated(CapturedOutput output) {
        when(profiles.seed(eq("other-org"), any())).thenReturn(Optional.empty());

        loader.load(yamlFile(FILE_NAME, BOAT_PROFILE));

        verify(profiles).seed(eq("other-org"), any());
        assertThat(output.getOut()).contains("created=0, already present=1, rejected=0");
    }

    @Test
    void rejectedAndNullEntriesDoNotPreventLoadingSubsequentProfiles(CapturedOutput output) {
        when(profiles.seed(eq("other-org"), any()))
                .thenThrow(AiRequestException.invalid("ai.profile.invalid", "name is required"))
                .thenReturn(Optional.of(new RecognitionProfile("other-org", "boat")));

        loader.load(yamlFile(FILE_NAME, """
                recognitionProfiles:
                  - entityName: invalid
                    detectorClass: boat
                  - null
                  - entityName: boat
                    name: Sailboat
                    detectorClass: boat
                """));

        ArgumentCaptor<RecognitionProfiles.Draft> drafts = ArgumentCaptor.forClass(RecognitionProfiles.Draft.class);
        verify(profiles, times(2)).seed(eq("other-org"), drafts.capture());
        assertThat(drafts.getAllValues()).extracting(RecognitionProfiles.Draft::entityName).containsExactly("invalid", "boat");
        assertThat(output.getOut()).contains("profile 'invalid'", "profile 'null'", "created=1, already present=0, rejected=2");
    }

    private static Resource yamlFile(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
