package com.processpuzzle.core.exception;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The most-used advice in the codebase and, until the error body was standardized, the least tested.
 * Every assertion below is about the wire: the status, and the {@code errorId}/{@code errorText} pair
 * that {@code shared-api.yaml}'s {@code ErrorResponse} declares.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void anIllegalArgumentIs400() {
        var response = handler.handleBadRequest(new IllegalArgumentException("orgKey must not be blank"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.invalid-argument");
        assertThat(response.getBody().errorText()).isEqualTo("orgKey must not be blank");
    }

    /**
     * An RSQL coercion failure reaches us wrapped by Spring's exception translator. It reports the same
     * id as a direct {@code IllegalArgumentException}: to the caller it is the same mistake, and the
     * wrapping is a detail of our persistence layer.
     */
    @Test
    void aWrappedIllegalArgumentFromTheRsqlSpecificationIsAlso400WithTheRootMessage() {
        var wrapped = new InvalidDataAccessApiUsageException("translated", new IllegalArgumentException("not a number: 'abc'"));

        var response = handler.handleDaoApiUsage(wrapped);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.invalid-argument");
        assertThat(response.getBody().errorText()).isEqualTo("not a number: 'abc'");
    }

    @Test
    void aDaoMisuseThatIsNotAnIllegalArgumentIsRethrownRatherThanReportedAs400() {
        var wrapped = new InvalidDataAccessApiUsageException("query is broken", new IllegalStateException("no session"));

        assertThatThrownBy(() -> handler.handleDaoApiUsage(wrapped)).isSameAs(wrapped);
    }

    @Test
    void anIllegalStateIs409() {
        var response = handler.handleConflictState(new IllegalStateException("already published"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().errorId()).isEqualTo("request.illegal-state");
        assertThat(response.getBody().errorText()).isEqualTo("already published");
    }

    /**
     * The HTTP-layer half of the same refusal, and the reason it is declared on Spring's exception
     * instead of relying on the cause fallback into {@link #anUnparseablePayloadIs400AndSaysSo}'s
     * handler: Boot 4 reads bodies with Jackson 3, whose exceptions are in {@code tools.jackson.core}
     * and share no supertype with Jackson 2's. The cause here is deliberately <em>not</em> a
     * {@code JsonProcessingException} — that is the whole case, and it was answered 500 in a live run
     * until this handler existed.
     */
    @Test
    void anUnreadableRequestBodyIs400EvenWhenItsCauseIsNotAJackson2Exception() {
        var foreignParseFailure = new RuntimeException("Unexpected end-of-input within/between Object entries");
        var response = handler.handleUnreadableBody(new HttpMessageNotReadableException("JSON parse error", foreignParseFailure, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.malformed-payload");
        assertThat(response.getBody().errorText()).contains("Unexpected end-of-input");
    }

    @Test
    void anUnparseablePayloadIs400AndSaysSo() {
        var response = handler.handleParseError(new JsonParseException(null, "unexpected character"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.malformed-payload");
        // Format-neutral on purpose: the same handler answers a malformed JSON request body, which
        // reaches it as the cause of HttpMessageNotReadableException.
        assertThat(response.getBody().errorText()).startsWith("Could not parse the request payload: ");
    }

    /**
     * The field errors are flattened into {@code errorText}. They used to be the body's keys, which made
     * the shape depend on the payload — the one thing a declared schema cannot describe.
     */
    @Test
    void validationFailuresAreFlattenedIntoErrorTextRatherThanBecomingTheBodysKeys() throws Exception {
        var response = handler.handleValidation(validationFailure());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errorId()).isEqualTo("request.validation-failed");
        assertThat(response.getBody().errorText()).isEqualTo("slug: must not be blank; title: must not be blank");
    }

    /**
     * The invariant every other test here is blind to, because each one calls a handler directly.
     * Spring resolves a wrapping exception through its <em>cause</em> only when no handler in this class
     * matches the wrapper itself, so a {@code @ExceptionHandler(Exception.class)} added here would
     * silently take over both of the cases below — which is exactly how a malformed request body and an
     * invalid {@code UUID} path variable came back as 500 instead of 400. The catch-all belongs to
     * {@link UnhandledExceptionHandler}; this test fails if it moves back.
     */
    @Test
    void thisAdviceHasNoCatchAllSoWrappedExceptionsStillResolveThroughTheirCause() {
        var resolver = new ExceptionHandlerMethodResolver(ApiExceptionHandler.class);

        assertThat(resolver.resolveMethodByThrowable(new RuntimeException("body unreadable", new JsonParseException(null, "unexpected character"))))
                .extracting(java.lang.reflect.Method::getName).isEqualTo("handleParseError");
        assertThat(resolver.resolveMethodByThrowable(new RuntimeException("bad path variable", new IllegalArgumentException("Invalid UUID string"))))
                .extracting(java.lang.reflect.Method::getName).isEqualTo("handleBadRequest");
        assertThat(resolver.resolveMethodByThrowable(new RuntimeException("nothing claims this"))).isNull();
    }

    private static MethodArgumentNotValidException validationFailure() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "documentInput");
        bindingResult.addError(new FieldError("documentInput", "slug", "must not be blank"));
        bindingResult.addError(new FieldError("documentInput", "title", "must not be blank"));

        var method = ApiExceptionHandlerTest.class.getDeclaredMethod("validationFailure");
        return new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(method, -1), bindingResult);
    }
}
