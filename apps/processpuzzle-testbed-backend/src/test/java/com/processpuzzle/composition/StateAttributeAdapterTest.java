package com.processpuzzle.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.state.api.StateOperationApi;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StateAttributeAdapterTest {

    private static final String ORG = "org-1";

    private final StateOperationApi stateOperations = mock(StateOperationApi.class);
    private final StateAttributeAdapter adapter = new StateAttributeAdapter(stateOperations);

    @Test
    void managesTheStateAttributeOfAGovernedEntity() {
        when(stateOperations.stateAttributeKey(ORG, "order")).thenReturn(Optional.of("status"));

        assertThat(adapter.managedAttributesOf(ORG, "order")).containsExactly("status");
    }

    @Test
    void managesNothingOfAnUngovernedEntity() {
        when(stateOperations.stateAttributeKey(ORG, "partner")).thenReturn(Optional.empty());

        assertThat(adapter.managedAttributesOf(ORG, "partner")).isEmpty();
    }
}
