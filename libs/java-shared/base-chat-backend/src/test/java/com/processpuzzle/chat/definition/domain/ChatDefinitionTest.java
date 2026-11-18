package com.processpuzzle.chat.definition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.Channel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ChatDefinitionTest {

    /** A valid ON_DEMAND, SINGLE definition of {@code order} in organization {@code org-1}. */
    public static ChatDefinition definition(String key) {
        return ChatDefinition.builder()
                .orgKey("org-1")
                .definitionKey(key)
                .name("Order discussion")
                .subjectType(SubjectType.ENTITY)
                .subjectDefinition("order")
                .build();
    }

    @Test
    void aMinimalDefinitionIsValidAndDefaultsToOneOnDemandConversation() {
        ChatDefinition definition = definition("order-discussion");

        assertThat(definition.validate()).isEmpty();
        assertThat(definition.getMultiplicity()).isEqualTo(Multiplicity.SINGLE);
        assertThat(definition.getLifecycle()).isEqualTo(ChatLifecycle.ON_DEMAND);
        assertThat(definition.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(definition.getReadRoles()).isEmpty();
    }

    @Test
    void requiresAWellFormedKeyANameAndASubject() {
        ChatDefinition definition = ChatDefinition.builder().definitionKey("Order Chat").build();

        assertThat(definition.validate()).hasSize(4)
                .anyMatch(error -> error.contains("'key'"))
                .anyMatch(error -> error.contains("'name'"))
                .anyMatch(error -> error.contains("'subjectType'"))
                .anyMatch(error -> error.contains("'subjectDefinition'"));
    }

    @Test
    void aStateBoundChatNeedsWritableStatesAndOthersMustNotHaveAny() {
        ChatDefinition stateBound = definition("order-discussion");
        stateBound.setLifecycle(ChatLifecycle.STATE_BOUND);
        assertThat(stateBound.validate()).singleElement().asString().contains("STATE_BOUND");

        stateBound.setWritableStates(new ArrayList<>(List.of("Running")));
        assertThat(stateBound.validate()).isEmpty();

        ChatDefinition onDemand = definition("order-discussion");
        onDemand.setWritableStates(new ArrayList<>(List.of("Running")));
        assertThat(onDemand.validate()).singleElement().asString().contains("only applies to a STATE_BOUND");
    }

    @Test
    void anAutoCreatedChatIsOnePerSubject() {
        ChatDefinition definition = definition("order-discussion");
        definition.setLifecycle(ChatLifecycle.AUTO_CREATE);
        definition.setMultiplicity(Multiplicity.MANY);

        assertThat(definition.validate()).singleElement().asString().contains("cannot be MANY");
    }

    @Test
    void rejectsNonPositiveRetentionAndBlankRolesOrStates() {
        ChatDefinition definition = definition("order-discussion");
        definition.setRetentionDays(0);
        definition.setReadRoles(new ArrayList<>(List.of(" ")));

        assertThat(definition.validate()).hasSize(2)
                .anyMatch(error -> error.contains("retentionDays"))
                .anyMatch(error -> error.contains("must not be blank"));
    }

    @Test
    void namesEveryRoleOnceForTheRoleCatalog() {
        ChatDefinition definition = definition("order-discussion");
        definition.setReadRoles(new ArrayList<>(List.of("dispatcher", "sailor")));
        definition.setWriteRoles(new ArrayList<>(List.of("sailor")));
        definition.setCreateRoles(new ArrayList<>(List.of("dispatcher", "judge")));

        assertThat(definition.allRoles()).containsExactly("dispatcher", "sailor", "judge");
    }

    @Test
    void listsAreNeverNullEvenWhenALoadedRowHadNone() {
        ChatDefinition definition = definition("order-discussion");
        definition.setWritableStates(null);
        definition.setReadRoles(null);
        definition.setWriteRoles(null);
        definition.setCreateRoles(null);

        assertThat(definition.getWritableStates()).isEmpty();
        assertThat(definition.getReadRoles()).isEmpty();
        assertThat(definition.getWriteRoles()).isEmpty();
        assertThat(definition.getCreateRoles()).isEmpty();
    }

    @Test
    void aReleasedCopyCarriesTheDraftsContentButNotItsVersion() {
        ChatDefinition draft = definition("order-discussion");
        draft.setDescription("d");
        draft.setReadRoles(new ArrayList<>(List.of("sailor")));
        draft.setIncludeSubjectOwner(true);
        draft.setRetentionDays(30);
        draft.setVersion(7L);

        ChatDefinition released = ChatDefinition.releasedCopyOf(draft);

        assertThat(released.getChannel()).isEqualTo(Channel.RELEASED);
        assertThat(released.getOrgKey()).isEqualTo("org-1");
        assertThat(released.getDefinitionKey()).isEqualTo("order-discussion");
        assertThat(released.getVersion()).isNull();
        assertThat(released.getReadRoles()).containsExactly("sailor");
        assertThat(released.isIncludeSubjectOwner()).isTrue();
        assertThat(released.getRetentionDays()).isEqualTo(30);
        released.getReadRoles().add("other");
        assertThat(draft.getReadRoles()).containsExactly("sailor");
    }

    @Test
    void identityIsOrganizationKeyAndChannel() {
        ChatDefinition draft = definition("order-discussion");
        ChatDefinition released = ChatDefinition.releasedCopyOf(draft);

        assertThat(draft).isNotEqualTo(released).isEqualTo(definition("order-discussion"));
        assertThat(new ChatDefinitionKey("org-1", "order-discussion", Channel.DRAFT))
                .isEqualTo(new ChatDefinitionKey("org-1", "order-discussion", Channel.DRAFT))
                .isNotEqualTo(new ChatDefinitionKey("org-1", "order-discussion", Channel.RELEASED))
                .hasToString("org-1/order-discussion/DRAFT");
    }
}
