package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleDefinitionReferencesTest {

    private static final String ORG = "acme";

    private final RoleDefinitionRepository repository = mock(RoleDefinitionRepository.class);
    private final RoleDefinitionReferences references = new RoleDefinitionReferences(repository);

    @Test
    void everyRoleDefinitionNamesItsIdAsARoleDefinitionReference() {
        RoleDefinition reviewer = definition("reviewer", "Reviewer");
        when(repository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).thenReturn(List.of(reviewer));
        when(repository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of());

        Map<String, List<RoleReference>> referenced = references.referencedRoles(ORG);

        assertThat(referenced).containsOnlyKeys("reviewer");
        assertThat(referenced.get("reviewer")).containsExactly(
                new RoleReference("workflow", RoleReference.ROLE_DEFINITION, "reviewer", "Reviewer"));
        assertThat(referenced.get("reviewer")).allMatch(RoleReference::isRoleDefinition);
    }

    /** The released definition is what the running application uses once the draft has been deleted. */
    @Test
    void aDefinitionOnBothChannelsIsReportedOnce() {
        RoleDefinition draft = definition("reviewer", "Reviewer");
        RoleDefinition released = definition("reviewer", "Reviewer");
        when(repository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).thenReturn(List.of(draft));
        when(repository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(released));

        assertThat(references.referencedRoles(ORG).get("reviewer")).hasSize(1);
    }

    @Test
    void aDefinitionOnlyReleasedIsStillReported() {
        when(repository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).thenReturn(List.of());
        RoleDefinition approver = definition("approver", "Approver");
        when(repository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(approver));

        assertThat(references.referencedRoles(ORG)).containsOnlyKeys("approver");
    }

    private static RoleDefinition definition(String id, String name) {
        RoleDefinition definition = mock(RoleDefinition.class);
        when(definition.getId()).thenReturn(id);
        when(definition.getName()).thenReturn(name);
        return definition;
    }
}
