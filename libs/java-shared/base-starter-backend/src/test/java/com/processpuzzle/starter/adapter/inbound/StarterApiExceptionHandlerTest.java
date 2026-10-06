package com.processpuzzle.starter.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.shared.model.ErrorResponse;
import com.processpuzzle.starter.registry.RegistryUnavailableException;
import com.processpuzzle.starter.usecase.ImportRejectedException;
import com.processpuzzle.starter.usecase.ImportReport;
import com.processpuzzle.starter.usecase.StarterNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class StarterApiExceptionHandlerTest {

    private final StarterApiExceptionHandler handler = new StarterApiExceptionHandler(new StarterMapper());

    @ParameterizedTest
    @CsvSource({"TOO_LARGE, 413", "HOLDS_INSTANCE_DATA, 409", "INVALID, 422"})
    void mapsEachRejectionToItsHttpStatusAndPreservesTheReport(ImportRejectedException.Reason reason, int status) {
        ImportReport report = ImportReport.rejected(true, "inventory", "1.0.0",
                List.of(new ImportReport.Error("manifest.yaml", "Import refused")));

        var response = handler.handleRejected(new ImportRejectedException(report, reason));

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(new StarterMapper().toModel(report));
    }

    @Test
    void returnsNotFoundWithTheStableErrorCodeAndMessage() {
        var response = handler.handleNotFound(new StarterNotFoundException("Unknown starter"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("starter.not-found", "Unknown starter"));
    }

    @Test
    void returnsServiceUnavailableWithTheStableErrorCodeAndMessage() {
        var response = handler.handleRegistryUnavailable(new RegistryUnavailableException("Registry offline"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("starter.registry-unavailable", "Registry offline"));
    }
}
