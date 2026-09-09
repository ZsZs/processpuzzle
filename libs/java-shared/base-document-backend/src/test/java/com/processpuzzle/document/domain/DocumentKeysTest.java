package com.processpuzzle.document.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two {@code @IdClass}es. JPA requires a no-arg constructor and {@code equals}/{@code hashCode}
 * over exactly the {@code @Id} fields — get either wrong and lookups by key silently miss, which is
 * the kind of failure that shows up as "the row is gone" rather than as an exception.
 */
class DocumentKeysTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void aDocumentKeyIsTheOrganizationAndTheIdTogether() {
        DocumentKey key = new DocumentKey(ORG, ID);

        assertThat(key.getOrgKey()).isEqualTo(ORG);
        assertThat(key.getId()).isEqualTo(ID);
        assertThat(key).isEqualTo(new DocumentKey(ORG, ID)).hasSameHashCodeAs(new DocumentKey(ORG, ID));
    }

    @Test
    void theSameDocumentIdInAnotherOrganizationIsADifferentKey() {
        // Dropping orgKey from the key is what would make an unscoped read expressible.
        DocumentKey key = new DocumentKey(ORG, ID);

        assertThat(key).isNotEqualTo(new DocumentKey("other", ID));
        assertThat(key).isNotEqualTo(new DocumentKey(ORG, "22222222-2222-2222-2222-222222222222"));
        assertThat(key).isEqualTo(key).isNotEqualTo(null).isNotEqualTo("not a key");
    }

    @Test
    void aDocumentKeyRoundTripsThroughTheNoArgConstructorJpaNeeds() {
        DocumentKey empty = new DocumentKey();

        assertThat(empty.getOrgKey()).isNull();
        assertThat(empty.getId()).isNull();
        assertThat(empty).isEqualTo(new DocumentKey()).hasSameHashCodeAs(new DocumentKey());
    }

    @Test
    void aTranslationKeyAddsTheLocale() {
        DocumentTranslationKey key = new DocumentTranslationKey(ORG, ID, "en");

        assertThat(key.getOrgKey()).isEqualTo(ORG);
        assertThat(key.getDocumentId()).isEqualTo(ID);
        assertThat(key.getLocale()).isEqualTo("en");
        assertThat(key).isEqualTo(new DocumentTranslationKey(ORG, ID, "en"))
                .hasSameHashCodeAs(new DocumentTranslationKey(ORG, ID, "en"));
    }

    @Test
    void everyFieldOfATranslationKeyDistinguishesIt() {
        DocumentTranslationKey key = new DocumentTranslationKey(ORG, ID, "en");

        assertThat(key).isNotEqualTo(new DocumentTranslationKey("other", ID, "en"));
        assertThat(key).isNotEqualTo(new DocumentTranslationKey(ORG, "22222222-2222-2222-2222-222222222222", "en"));
        assertThat(key).isNotEqualTo(new DocumentTranslationKey(ORG, ID, "de"));
        assertThat(key).isEqualTo(key).isNotEqualTo(null).isNotEqualTo("not a key");
    }

    @Test
    void aTranslationKeyAlsoHasTheNoArgConstructorJpaNeeds() {
        DocumentTranslationKey empty = new DocumentTranslationKey();

        assertThat(empty.getOrgKey()).isNull();
        assertThat(empty.getDocumentId()).isNull();
        assertThat(empty.getLocale()).isNull();
        assertThat(empty).hasSameHashCodeAs(new DocumentTranslationKey());
    }
}
