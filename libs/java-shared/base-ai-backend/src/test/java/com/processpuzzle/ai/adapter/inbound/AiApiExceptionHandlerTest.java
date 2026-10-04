package com.processpuzzle.ai.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.MediaStoreUnavailableException;
import com.processpuzzle.shared.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class AiApiExceptionHandlerTest {

    private final AiApiExceptionHandler handler = new AiApiExceptionHandler();

    @ParameterizedTest
    @CsvSource({"NOT_FOUND,404", "INVALID,400", "CONFLICT,409", "TOO_LARGE,413", "UNAUTHORIZED,401"})
    void refusalsKeepTheirStatusTranslationKeyAndMessage(AiRequestException.Kind kind, int status) {
        var exception = new AiRequestException(kind, "ai.request.refused", "The request was refused.");

        var response = handler.handleRefusal(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("ai.request.refused", "The request was refused."));
    }

    @Test
    void missingStorageProducesAServiceUnavailableResponse() {
        var response = handler.handleStorageUnavailable(new MediaStoreUnavailableException("Storage is not configured."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("ai.media.storage-unavailable", "Storage is not configured."));
    }
}
