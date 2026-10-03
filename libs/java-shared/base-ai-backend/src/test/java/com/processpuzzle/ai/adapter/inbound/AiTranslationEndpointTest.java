package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiTranslationEndpointTest {

    @Test
    void bothTranslationEndpointsReturnEmptyBundlesUntilMessagesAreDefined() {
        var endpoint = new AiTranslationEndpoint();

        var all = endpoint.getAiTranslations("my-org", "en");
        var scoped = endpoint.getAiScopedTranslations("my-org", "recognition", "hu");

        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).isEmpty();
        assertThat(scoped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scoped.getBody()).isEmpty();
    }
}
