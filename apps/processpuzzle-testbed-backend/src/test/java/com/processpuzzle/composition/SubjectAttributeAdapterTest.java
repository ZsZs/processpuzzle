package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.PlatformSubject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubjectAttributeAdapterTest {

    private final EntityAttributeQuery query = mock(EntityAttributeQuery.class);
    private final SubjectAttributeAdapter adapter = new SubjectAttributeAdapter(query,
            List.of(new PlatformSubject("calculation-run", Set.of("calculationId", "subjectId"))));

    @Test
    void answersFromTheEntityTypesDraft() {
        when(query.entityTypeExists("org-1", "order", Channel.DRAFT)).thenReturn(true);
        when(query.attributeKind("org-1", "order", "orderNumber", Channel.DRAFT)).thenReturn(Optional.of(EntityAttributeKind.values()[0]));

        assertThat(adapter.hasAttribute("org-1", "order", "orderNumber")).contains(true);
        assertThat(adapter.hasAttribute("org-1", "order", "orderNo")).contains(false);
    }

    // An entity type it does not know is not an answer: the check is skipped rather than failed.
    @Test
    void cannotTellForAnUnknownEntityType() {
        assertThat(adapter.hasAttribute("org-1", "invoice", "number")).isEmpty();
    }

    @Test
    void answersADeclaredPlatformSubjectFromItsDeclaration() {
        assertThat(adapter.hasAttribute("org-1", "calculation-run", "calculationId")).contains(true);
        assertThat(adapter.hasAttribute("org-1", "calculation-run", "total")).contains(false);
    }
}
