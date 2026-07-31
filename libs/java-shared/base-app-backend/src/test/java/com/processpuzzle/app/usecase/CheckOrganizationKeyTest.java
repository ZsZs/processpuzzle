package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.usecase.service.ReservedOrganizationKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckOrganizationKeyTest {

    private OrganizationRepository repository;
    private CheckOrganizationKey checkOrganizationKey;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationRepository.class);
        when(repository.existsById(anyString())).thenReturn(false);
        checkOrganizationKey = new CheckOrganizationKey(repository, new ReservedOrganizationKeys(List.of()));
    }

    @Test
    void freeWellFormedKey_isAvailable() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("my-org");

        assertThat(outcome.available()).isTrue();
        assertThat(outcome.errorId()).isNull();
        assertThat(outcome.suggestions()).isEmpty();
    }

    @Test
    void takenKey_isRejectedWithSuggestions() {
        when(repository.existsById("my-org")).thenReturn(true);

        KeyCheckOutcome outcome = checkOrganizationKey.execute("my-org");

        assertThat(outcome.available()).isFalse();
        assertThat(outcome.errorId()).isEqualTo("organization.key.taken");
        assertThat(outcome.suggestions()).isNotEmpty().doesNotContain("my-org");
    }

    @Test
    void reservedPlatformSegment_isRejected() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("api");

        assertThat(outcome.available()).isFalse();
        assertThat(outcome.errorId()).isEqualTo("organization.key.reserved");
    }

    @Test
    void additionallyReservedKey_isRejected() {
        CheckOrganizationKey withExtras = new CheckOrganizationKey(repository,
                new ReservedOrganizationKeys(List.of("Acme", " demo ")));

        assertThat(withExtras.execute("acme").errorId()).isEqualTo("organization.key.reserved");
        assertThat(withExtras.execute("demo").errorId()).isEqualTo("organization.key.reserved");
    }

    @Test
    void partialInputFromATypingUser_isReportedNotRejectedOutright() {
        assertThat(checkOrganizationKey.execute("m").errorId()).isEqualTo("organization.key.length");
        assertThat(checkOrganizationKey.execute("").errorId()).isEqualTo("organization.key.missing");
        assertThat(checkOrganizationKey.execute(null).errorId()).isEqualTo("organization.key.missing");
    }

    @Test
    void malformedKey_isRejectedWithASlugifiedSuggestion() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("My Org Ltd.");

        assertThat(outcome.available()).isFalse();
        assertThat(outcome.errorId()).isEqualTo("organization.key.invalid");
        assertThat(outcome.suggestions()).contains("my-org-ltd");
    }

    @Test
    void keysAreNormalisedToLowercaseAndTrimmed() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("  My-Org  ");

        assertThat(outcome.key()).isEqualTo("my-org");
        assertThat(outcome.available()).isTrue();
    }

    @Test
    void overlongKey_isRejected() {
        assertThat(checkOrganizationKey.execute("a".repeat(64)).errorId())
                .isEqualTo("organization.key.length");
    }

    @Test
    void suggestionsSkipTakenAndReservedAlternatives() {
        when(repository.existsById("my-org")).thenReturn(true);
        when(repository.existsById("my-org-app")).thenReturn(true);

        List<String> suggestions = checkOrganizationKey.execute("my-org").suggestions();

        assertThat(suggestions).doesNotContain("my-org", "my-org-app").hasSizeLessThanOrEqualTo(3);
        assertThat(suggestions).allSatisfy(suggestion -> assertThat(suggestion).startsWith("my-org-"));
    }
}
