package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.baseentity.common.JpaAuditingConfig;
import com.processpuzzle.baseentity.common.SecurityAuditorAware;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.Multiplicity;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceDataPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.MigrationStep;
import com.processpuzzle.core.definition.MigrationStepFailedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The migration operations over real definitions — whether a step can run is judged against the released row and the
 * draft — and objects held in memory, so that what each operation does to a payload is plain to see.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:entity-migration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.baseentity.definition.domain")
@EnableJpaRepositories("com.processpuzzle.baseentity.definition.domain")
@Import({EntityMigrationParticipant.class, JpaAuditingConfig.class, EntityMigrationParticipantTest.Objects.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EntityMigrationParticipantTest {

    private static final String ORG = "my-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        SecurityAuditorAware securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }

    /** The objects of each definition code, rewritten in place as the instances module would. */
    static class Objects implements EntityInstanceDataPort {
        final Map<String, List<Map<String, Object>>> payloads = new HashMap<>();

        @Override
        public List<Map<String, Object>> payloadsOf(String orgKey, String entityDefinitionCode) {
            return payloads.getOrDefault(entityDefinitionCode, List.of());
        }

        @Override
        public long rewritePayloads(String orgKey, String entityDefinitionCode, UnaryOperator<Map<String, Object>> rewrite) {
            List<Map<String, Object>> objects = payloads.getOrDefault(entityDefinitionCode, new ArrayList<>());
            long written = 0;
            for (int index = 0; index < objects.size(); index++) {
                Map<String, Object> rewritten = rewrite.apply(new LinkedHashMap<>(objects.get(index)));
                if (!rewritten.equals(objects.get(index))) {
                    objects.set(index, rewritten);
                    written++;
                }
            }
            return written;
        }
    }

    @Autowired
    private EntityMigrationParticipant participant;
    @Autowired
    private EntityDefinitionRepository repository;
    @Autowired
    private Objects objects;
    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        objects.payloads.clear();
    }

    @Test
    void runsTheThreeEntityOperations() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.ENTITY);
        assertThat(participant.operations()).containsExactlyInAnyOrder("renameAttribute", "fillAttribute", "mapValues");
    }

    @Test
    void renameMovesEachValueToTheNewAttribute() {
        releaseAndDraft(definition("customer", attribute("customerName", ValueKind.TEXT)),
            draft -> {
                draft.getAttributes().clear();
                draft.addAttribute(attribute("name", ValueKind.TEXT));
            });
        holding("customer", Map.of("customerName", "Acme GmbH"), Map.of("other", 1));
        MigrationStep step = step("renameAttribute", "customer", "customerName", Map.of("to", "name"));

        assertThat(participant.unusable(ORG, step)).isEmpty();
        assertThat(participant.apply(ORG, step)).isEqualTo(1);

        assertThat(objects.payloads.get("customer")).containsExactly(Map.of("name", "Acme GmbH"), Map.of("other", 1));
    }

    @Test
    void renameRefusesToOverwriteAValueTheNewAttributeHoldsAlready() {
        releaseAndDraft(definition("customer", attribute("customerName", ValueKind.TEXT)),
            draft -> {
                draft.getAttributes().clear();
                draft.addAttribute(attribute("name", ValueKind.TEXT));
            });
        holding("customer", Map.of("customerName", "Acme GmbH", "name", "Acme"));

        assertThatThrownBy(() -> participant.apply(ORG, step("renameAttribute", "customer", "customerName", Map.of("to", "name"))))
            .isInstanceOf(MigrationStepFailedException.class)
            .hasMessageContaining("1 object(s)");
    }

    @Test
    void renameIsUnusableUnlessTheDraftRemovesTheOldAttributeAndAddsTheNewOne() {
        releaseAndDraft(definition("customer", attribute("customerName", ValueKind.TEXT), attribute("name", ValueKind.TEXT)),
            draft -> { });

        assertThat(participant.unusable(ORG, step("renameAttribute", "customer", "customerName", Map.of("to", "name"))))
            .hasValueSatisfying(reason -> assertThat(reason).contains("customer.customerName"));
        assertThat(participant.unusable(ORG, step("renameAttribute", "unknown", "customerName", Map.of("to", "name"))))
            .isPresent();
    }

    @Test
    void fillSetsTheValueWhereThereIsNone_orEverywhereWhenAsked() {
        releaseAndDraft(definition("invoice", attribute("note", ValueKind.TEXT)),
            draft -> draft.addAttribute(attribute("currency", ValueKind.TEXT)));
        holding("invoice", Map.of("currency", "HUF"), Map.of("note", "x"));

        assertThat(participant.apply(ORG, step("fillAttribute", "invoice", "currency", Map.of("value", "EUR")))).isEqualTo(1);
        assertThat(objects.payloads.get("invoice")).extracting(payload -> payload.get("currency")).containsExactly("HUF", "EUR");

        assertThat(participant.apply(ORG, step("fillAttribute", "invoice", "currency", Map.of("value", "EUR", "only", "always"))))
            .isEqualTo(1);
        assertThat(objects.payloads.get("invoice")).extracting(payload -> payload.get("currency")).containsExactly("EUR", "EUR");
    }

    @Test
    void fillIsUnusableWithAValueThatDoesNotFitTheAttribute() {
        releaseAndDraft(definition("invoice", attribute("note", ValueKind.TEXT)),
            draft -> draft.addAttribute(attribute("total", ValueKind.NUMBER)));

        assertThat(participant.unusable(ORG, step("fillAttribute", "invoice", "total", Map.of("value", "lots")))).isPresent();
        assertThat(participant.unusable(ORG, step("fillAttribute", "invoice", "total", Map.of("value", 0)))).isEmpty();
        assertThat(participant.unusable(ORG, step("fillAttribute", "invoice", "missing", Map.of("value", 0)))).isPresent();
    }

    @Test
    void mapValuesReplacesMappedValues_elementByElement_andKeepsTheRest() {
        BaseEntityAttribute tags = attribute("tags", ValueKind.TEXT);
        tags.setMultiplicity(Multiplicity.ZERO_TO_N);
        releaseAndDraft(definition("order", attribute("status", ValueKind.TEXT), tags), draft -> { });
        holding("order", Map.of("status", "Pending", "tags", List.of("a", "b")), Map.of("status", "Approved"));

        long written = participant.apply(ORG, step("mapValues", "order", "status", Map.of("mapping", Map.of("Pending", "Submitted"))));
        participant.apply(ORG, step("mapValues", "order", "tags", Map.of("mapping", Map.of("a", "A"))));

        assertThat(written).isEqualTo(1);
        assertThat(objects.payloads.get("order")).extracting(payload -> payload.get("status"))
            .containsExactly("Submitted", "Approved");
        assertThat(objects.payloads.get("order").getFirst()).containsEntry("tags", List.of("A", "b"));
    }

    @Test
    void mapValuesFailsOnAnUnmappedValue_whenAskedTo() {
        releaseAndDraft(definition("order", attribute("status", ValueKind.TEXT)), draft -> { });
        holding("order", Map.of("status", "Pending"), Map.of("status", "OnHold"));

        assertThatThrownBy(() -> participant.apply(ORG, step("mapValues", "order", "status",
                Map.of("mapping", Map.of("Pending", "Submitted"), "unmapped", "fail"))))
            .isInstanceOf(MigrationStepFailedException.class)
            .hasMessageContaining("1 object(s)");
        assertThat(objects.payloads.get("order")).extracting(payload -> payload.get("status")).containsExactly("Pending", "OnHold");
    }

    @Test
    void mapValuesIsUnusableWithoutAMapping() {
        releaseAndDraft(definition("order", attribute("status", ValueKind.TEXT)), draft -> { });

        assertThat(participant.unusable(ORG, step("mapValues", "order", "status", Map.of()))).isPresent();
    }

    private void releaseAndDraft(BaseEntityDefinition released, Consumer<BaseEntityDefinition> changeDraft) {
        transaction.executeWithoutResult(status -> {
            repository.save(released);
            repository.save(BaseEntityDefinition.releasedCopyOf(released));
        });
        transaction.executeWithoutResult(status -> {
            BaseEntityDefinition draft = repository.findByOrgKeyAndCodeAndChannel(ORG, released.getCode(), Channel.DRAFT).orElseThrow();
            changeDraft.accept(draft);
            repository.save(draft);
        });
    }

    @SafeVarargs
    private void holding(String code, Map<String, Object>... payloads) {
        objects.payloads.put(code, new ArrayList<>(List.of(payloads)));
    }

    private static MigrationStep step(String operation, String key, String subject, Map<String, Object> parameters) {
        return new MigrationStep("s1", DefinitionKinds.ENTITY, key, operation, subject, parameters);
    }

    private static BaseEntityDefinition definition(String code, BaseEntityAttribute... attributes) {
        BaseEntityDefinition definition = BaseEntityDefinition.builder().orgKey(ORG).code(code).name(code).build();
        for (int index = 0; index < attributes.length; index++) {
            attributes[index].setDisplayOrder(index);
            definition.addAttribute(attributes[index]);
        }
        return definition;
    }

    private static BaseEntityAttribute attribute(String code, ValueKind kind) {
        return BaseEntityAttribute.builder().code(code).name(code).valueKind(kind)
            .formControlType(FormControlType.TEXT_BOX).multiplicity(Multiplicity.ZERO_TO_ONE).build();
    }
}
