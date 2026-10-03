package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.usecase.RecognitionProfiles;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePatternResolver;

class DefaultProfileLoaderTest {

    private final RecognitionProfiles profiles = mock(RecognitionProfiles.class);
    private final DefaultProfileLoader loader = new DefaultProfileLoader(profiles, new AiMapper(), mock(ResourcePatternResolver.class));

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

    @Test
    void aFileNotNamedAfterAnOrganizationIsSkipped() {
        loader.load(new ByteArrayResource("recognitionProfiles: []".getBytes()) {
            @Override
            public String getFilename() {
                return "profiles.yaml";
            }
        });

        verify(profiles, never()).seed(any(), any());
    }
}
