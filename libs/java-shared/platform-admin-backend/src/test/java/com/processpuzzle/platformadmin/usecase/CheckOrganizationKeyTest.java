package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.service.ReservedOrganizationKeys;
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
        checkOrganizationKey = new CheckOrganizationKey(repository, new ReservedOrganizationKeys(List.of(), null));
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
                new ReservedOrganizationKeys(List.of("Acme", " demo "), null));

        assertThat(withExtras.execute("acme").errorId()).isEqualTo("organization.key.reserved");
        assertThat(withExtras.execute("demo").errorId()).isEqualTo("organization.key.reserved");
    }

    @Test
    void partialInputFromATypingUser_isReportedNotRejectedOutright() {
        assertThat(checkOrganizationKey.execute("m").errorId()).isEqualTo("organization.key.length");
        assertThat(checkOrganizationKey.execute("").errorId()).isEqualTo("organization.key.missing");
        assertThat(checkOrganizationKey.execute(null).errorId()).isEqualTo("organization.key.missing");
    }

    /** The hyphen may only join slug words, so it can neither bound the key nor repeat. */
    @Test
    void hyphensAtAnEdgeOrDoubled_areRejected() {
        assertThat(checkOrganizationKey.execute("-my-org").errorId()).isEqualTo("organization.key.invalid");
        assertThat(checkOrganizationKey.execute("my-org-").errorId()).isEqualTo("organization.key.invalid");
        assertThat(checkOrganizationKey.execute("my--org").errorId()).isEqualTo("organization.key.invalid");
        assertThat(checkOrganizationKey.execute("my-2nd-org").available()).isTrue();
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

    /** Provisioning asks the same question twice, once as a predicate and once for the reason. */
    @Test
    void theProvisioningShortcutsAgreeWithTheFullOutcome() {
        when(repository.existsById("taken-org")).thenReturn(true);

        assertThat(checkOrganizationKey.isClaimable("my-org")).isTrue();
        assertThat(checkOrganizationKey.rejectionReason("my-org")).isNull();
        assertThat(checkOrganizationKey.isClaimable("taken-org")).isFalse();
        assertThat(checkOrganizationKey.rejectionReason("taken-org")).isEqualTo("organization.key.taken");
        assertThat(checkOrganizationKey.rejectionReason("api")).isEqualTo("organization.key.reserved");
    }

    /** Input with nothing slug-like in it has no usable alternative to offer. */
    @Test
    void inputWithNoSlugCharactersAtAll_isRejectedWithoutSuggestions() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("!!! ???");

        assertThat(outcome.available()).isFalse();
        assertThat(outcome.errorId()).isEqualTo("organization.key.invalid");
        assertThat(outcome.suggestions()).isEmpty();
    }

    /** Leading and repeated separators must not become leading or doubled hyphens in a suggestion. */
    @Test
    void slugifiedSuggestionsCarryNoLeadingOrRepeatedHyphens() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("...My  Org...");

        assertThat(outcome.suggestions()).isNotEmpty()
                .allSatisfy(suggestion -> assertThat(suggestion).doesNotStartWith("-").doesNotContain("--"));
        assertThat(outcome.suggestions()).contains("my-org");
    }

    /** Digits are slug characters; anything ordered after {@code z} is a separator like anything else. */
    @Test
    void digitsSurviveSlugificationAndCharactersBeyondZDoNot() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("My Org 2 ~ Ltd");

        assertThat(outcome.errorId()).isEqualTo("organization.key.invalid");
        assertThat(outcome.suggestions()).contains("my-org-2-ltd");
    }

    /**
     * The decorated alternatives ({@code -app}, {@code -1}) would push an already long key past the
     * 63-character maximum, so they are dropped rather than suggested and then rejected.
     */
    @Test
    void suggestionsLongerThanTheMaximumAreDropped() {
        String almostTooLong = "a".repeat(62);

        KeyCheckOutcome outcome = checkOrganizationKey.execute(almostTooLong + "!");

        assertThat(outcome.errorId()).isEqualTo("organization.key.invalid");
        assertThat(outcome.suggestions()).containsExactly(almostTooLong);
    }

    /** A one-character slug is below the minimum, so it must not be suggested as-is. */
    @Test
    void suggestionsShorterThanTheMinimumAreDropped() {
        KeyCheckOutcome outcome = checkOrganizationKey.execute("m!");

        assertThat(outcome.suggestions()).allSatisfy(suggestion ->
                assertThat(suggestion).hasSizeGreaterThanOrEqualTo(2));
        assertThat(outcome.suggestions()).doesNotContain("m");
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
