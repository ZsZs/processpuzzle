package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.ai.assistant.DefinitionDrafts;
import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The design assistant's draft operations for rules and calculations, through the real participants and the real entity
 * catalog: the assistant can list, read, save and check them, and a save that the checks refuse writes nothing.
 */
@SpringBootTest
class DefinitionDraftsIntegrationTest {

    private static final String ORG = "definition-drafts";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: draft-order
                name: Draft Order
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: reference, name: Reference, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: total, name: Total, displayOrder: 2, valueKind: NUMBER, formControlType: NUMBER }
            """;

    private static final String RULE = """
        {"id":"positive-total","name":"Positive total","context":"Draft Order","expression":"entity.total > 0","severity":"ERROR"}""";

    private static final String CALCULATION = """
        {"source":"calculation: double-total\\nname: Double total\\ndescription: Doubles the total.\\nparameters:\\n  order: { entity: draft-order }\\ndecisions:\\n  doubled: { expr: \\"2\\" }\\noutput:\\n  entity: draft-order\\n  mapping: { total: doubled }\\n"}""";

    @MockitoBean private OrganizationGuard guard;
    @Autowired private List<DefinitionAuthoringParticipant> participants;
    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;

    private DefinitionDrafts drafts;

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEntities.execute(ORG, new ByteArrayInputStream(ENTITIES.getBytes(StandardCharsets.UTF_8))).errors()).isEmpty();
        entityRelease.release(ORG);
        drafts = new DefinitionDrafts(participants);
    }

    @Test
    void ruleAndCalculationAreAuthoredKinds() {
        assertThat(drafts.kinds()).contains(DefinitionKinds.RULE, DefinitionKinds.CALCULATION);
    }

    @Test
    void aRuleDraftIsSavedListedReadAndChecked() {
        assertThat(drafts.save(ORG, DefinitionKinds.RULE, "positive-total", RULE)).isEmpty();

        assertThat(drafts.list(ORG, DefinitionKinds.RULE)).contains("positive-total");
        assertThat(drafts.get(ORG, DefinitionKinds.RULE, "positive-total")).get().asString().contains("entity.total > 0");
        assertThat(drafts.validate(ORG, DefinitionKinds.RULE, "positive-total")).isEmpty();
    }

    @Test
    void aRuleWithAMisspeltAttributeIsRefusedAndNotWritten() {
        List<AuthoringIssue> issues = drafts.save(ORG, DefinitionKinds.RULE, "typo-rule",
            RULE.replace("positive-total", "typo-rule").replace("entity.total", "entity.totl"));

        assertThat(issues).isNotEmpty();
        assertThat(issues.get(0).text()).contains("entity.totl");
        assertThat(drafts.list(ORG, DefinitionKinds.RULE)).doesNotContain("typo-rule");
    }

    @Test
    void aCalculationDraftIsSavedAndCheckedAgainstTheEntities() {
        assertThat(drafts.save(ORG, DefinitionKinds.CALCULATION, "double-total", CALCULATION)).isEmpty();

        assertThat(drafts.list(ORG, DefinitionKinds.CALCULATION)).contains("double-total");
        assertThat(drafts.validate(ORG, DefinitionKinds.CALCULATION, "double-total")).isEmpty();
    }

    @Test
    void aCalculationWritingAnAttributeTheEntityLacksIsRefused() {
        // Its own id: the other tests share this database, so a refused draft must not be one another test saved.
        String bad = CALCULATION.replace("double-total", "refused-total").replace("mapping: { total: doubled }", "mapping: { totl: doubled }");

        List<AuthoringIssue> issues = drafts.save(ORG, DefinitionKinds.CALCULATION, "refused-total", bad);

        assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("output.mapping.totl"));
        assertThat(drafts.list(ORG, DefinitionKinds.CALCULATION)).doesNotContain("refused-total");
    }
}
