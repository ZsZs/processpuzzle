package com.processpuzzle.baseentity.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagedAttributesPortTest {

    @Test
    void managesWhatAnyOwnerNames() {
        ManagedAttributesPort state = (orgKey, entity) -> "order".equals(entity) ? Set.of("status") : Set.of();
        ManagedAttributesPort calculation = (orgKey, entity) -> "order".equals(entity) ? Set.of("total") : Set.of();

        ManagedAttributesPort union = ManagedAttributesPort.unionOf(List.of(state, calculation));

        assertThat(union.managedAttributesOf("acme", "order")).containsExactlyInAnyOrder("status", "total");
        assertThat(union.managedAttributesOf("acme", "boat")).isEmpty();
        assertThat(ManagedAttributesPort.unionOf(List.of())).isSameAs(ManagedAttributesPort.NONE);
    }
}
