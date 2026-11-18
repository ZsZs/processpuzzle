package com.processpuzzle.baseentity.instances.usecases.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A lookup that knows nothing of commons answers that no type is shared. */
class EntityDefinitionLookupPortDefaultsTest {

    private final EntityDefinitionLookupPort lookup = (orgKey, code, channel) -> Optional.<EntityDefinitionView>empty();

    @Test
    void noTypeIsSharedByDefault() {
        assertThat(lookup.sharedFrom("acme", "order")).isEmpty();
        assertThat(lookup.sharingOf("acme", "order")).isEmpty();
        assertThat(lookup.isShared("acme", "order")).isFalse();
    }

    @Test
    void sharingNamesAreReadAsPolicies() {
        assertThat(new EntityDefinitionLookupPort.SharingView("OWNER_ORG", "OWNER_ONLY").ownerWrites()).isTrue();
        assertThat(new EntityDefinitionLookupPort.SharingView("COMMON_ADMIN", "PUBLIC").ownerOnly()).isFalse();
    }
}
