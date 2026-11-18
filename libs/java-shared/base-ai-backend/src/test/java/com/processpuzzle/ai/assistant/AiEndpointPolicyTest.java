package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AiEndpointPolicyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not a url", "ftp://8.8.8.8/v1", "http://8.8.8.8/v1", "https://user:pw@8.8.8.8/v1", "https:///v1"})
    void aMalformedOrNonHttpsAddressIsRefused(String url) {
        assertThatThrownBy(() -> AiEndpointPolicy.require(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://0.0.0.0/v1", "https://127.0.0.1/v1", "https://169.254.169.254/latest",
        "https://10.1.2.3/v1", "https://192.168.0.1/v1", "https://172.16.0.1/v1", "https://224.0.0.1/v1",
        "https://[::1]/v1", "https://[fd00::1]/v1", "https://[fe80::1]/v1"})
    void anAddressInsideTheNetworkIsRefused(String url) {
        assertThatThrownBy(() -> AiEndpointPolicy.require(url)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("public");
    }

    @Test
    void aHostThatDoesNotResolveIsRefused() {
        assertThatThrownBy(() -> AiEndpointPolicy.require("https://no-such-host.invalid/v1"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("resolved");
    }

    @Test
    void aPublicHttpsAddressIsAccepted() {
        assertThatCode(() -> AiEndpointPolicy.require("https://8.8.8.8/v1")).doesNotThrowAnyException();
        assertThatCode(() -> AiEndpointPolicy.require(" https://[2001:4860:4860::8888]/v1 ")).doesNotThrowAnyException();
    }
}
