package com.processpuzzle.calculation.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.calculation.adapter.inbound.CalculationReleaseParticipant;
import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.adapter.outbound.JsonColumnMappingAutoConfiguration;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.engine.Calculations;
import com.processpuzzle.calculation.event.CalculationCompleted;
import com.processpuzzle.calculation.event.CalculationFailed;
import com.processpuzzle.calculation.event.CalculationFrozen;
import com.processpuzzle.calculation.port.AttributeType;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.calculation.usecase.exception.InvalidParametersException;
import com.processpuzzle.calculation.usecase.exception.RunNotFreezableException;
import com.processpuzzle.calculation.usecase.exception.SubjectFrozenException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.event.PlatformEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Definitions, runs, evaluation and freezing over H2, with the entity layer in memory. Not transactional per test:
 * a run computes in a transaction of its own, which would not see a definition the test had not committed.
 */
@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:calculation-runs;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.calculation.domain")
@EnableJpaRepositories("com.processpuzzle.calculation.domain")
// A slice does not run every auto-configuration; this one is what keeps the jsonb maps Java maps.
@ImportAutoConfiguration(JsonColumnMappingAutoConfiguration.class)
@RecordApplicationEvents
class CalculationRunsIntegrationTest {

    private static final String ORG = "org-1";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CamundaFeelExpressionEngine.class, CalculationValidator.class, CalculationEngineConfiguration.class,
            CreateCalculationDefinition.class, ReplaceCalculationDefinition.class, FindCalculationDefinition.class,
            ImportCalculationDefinitions.class, InputResolver.class, OutputWriter.class, RunEvents.class,
            RunCalculation.class, EvaluateCalculation.class, FreezeRun.class, FindCalculationRuns.class,
            CalculationReleaseParticipant.class})
    static class TestConfig {

        @Bean
        InMemoryEntities entities() {
            return new InMemoryEntities();
        }
    }

    @Autowired private InMemoryEntities entities;
    @Autowired private CreateCalculationDefinition create;
    @Autowired private ReplaceCalculationDefinition replace;
    @Autowired private ImportCalculationDefinitions importer;
    @Autowired private CalculationReleaseParticipant release;
    @Autowired private RunCalculation run;
    @Autowired private EvaluateCalculation evaluate;
    @Autowired private FreezeRun freeze;
    @Autowired private FindCalculationRuns runs;
    @Autowired private FindCalculationDefinition find;
    @Autowired private CalculationDefinitionRepository definitionRepository;
    @Autowired private CalculationRunRepository runRepository;
    @Autowired private ApplicationEvents events;

    @BeforeEach
    void setUp() {
        runRepository.deleteAll();
        definitionRepository.deleteAll();
        entities.clear();
        entities.type("order-line", "quantity", AttributeType.NUMBER);
    }

    private String order(Object... lines) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < lines.length; index += 2) {
            items.add(Map.of("productName", "P" + index, "quantity", lines[index], "unitPrice", lines[index + 1]));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNumber", "ORD-1");
        payload.put("lineItems", items);
        return entities.add("order", payload);
    }

    private void seedOrderTotal() {
        create.create(ORG, Calculations.source("order-total"));
        release.release(ORG);
    }

    @Test
    void savingMovesTheDefinitionVersionOnlyWhenTheSourceChanges() {
        String source = Calculations.source("order-total");
        var created = create.create(ORG, source);
        assertThat(created.getDefinitionVersion()).isEqualTo(1);
        assertThat(created.getOutputEntity()).isEqualTo("order");
        assertThat(created.getModel()).containsEntry("calculation", "order-total");

        var unchanged = replace.replace(ORG, "order-total", source, null);
        assertThat(unchanged.getDefinitionVersion()).isEqualTo(1);
        var changed = replace.replace(ORG, "order-total", source + "# a comment\n", unchanged.getVersion());
        assertThat(changed.getDefinitionVersion()).isEqualTo(2);
        assertThat(changed.getSource()).endsWith("# a comment\n");

        assertThatThrownBy(() -> replace.replace(ORG, "order-total", source, 99L))
                .isInstanceOf(com.processpuzzle.calculation.usecase.exception.StaleCalculationException.class);
        String renamed = source.replace("order-total", "other");
        assertThatThrownBy(() -> replace.replace(ORG, "order-total", renamed, null))
                .isInstanceOf(InvalidCalculationException.class).hasMessageContaining("the id cannot change");
    }

    @Test
    void runWritesTheTotalAndKeepsTheRun() {
        seedOrderTotal();
        String orderId = order(2, 19.99, 1, 49.90);

        CalculationRun stored = run.run(ORG, "order-total", Channel.RELEASED, Map.of("order", orderId), RunTrigger.MANUAL);

        assertThat(stored.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(stored.getWritten()).isEqualTo(1);
        assertThat(stored.getSubjectType()).isEqualTo("order");
        assertThat(stored.getSubjectId()).isEqualTo(orderId);
        assertThat(stored.getDefinitionSource()).contains("calculation: order-total");
        assertThat(stored.getParameters()).containsEntry("order", orderId).containsEntry("customerTier", "STANDARD");
        assertThat(entities.payload("order", orderId)).containsEntry("total", new BigDecimal("89.88"));
        assertThat(runs.find(ORG, stored.getId()).getTrace()).extracting(entry -> entry.get("decision"))
                .containsExactly("lineTotal", "subtotal", "discountRate", "total");
        assertThat(events.stream(CalculationCompleted.class)).singleElement()
                .satisfies(event -> assertThat(event.values()).containsKey("total"));
        assertThat(events.stream(PlatformEvent.class)).singleElement()
                .satisfies(fact -> assertThat(fact.state()).isEqualTo("COMPLETED"));
    }

    @Test
    void aFrozenSubjectIsNotRecalculated() {
        seedOrderTotal();
        String orderId = order(1, 10);
        CalculationRun first = run.run(ORG, "order-total", Channel.RELEASED, Map.of("order", orderId), RunTrigger.MANUAL);

        CalculationRun frozen = freeze.freeze(ORG, first.getId());
        assertThat(frozen.isFrozen()).isTrue();
        assertThat(freeze.freeze(ORG, first.getId()).isFrozen()).isTrue();
        assertThat(events.stream(CalculationFrozen.class)).hasSize(1);

        Map<String, Object> theOrder = Map.of("order", orderId);
        assertThatThrownBy(() -> run.run(ORG, "order-total", Channel.RELEASED, theOrder, RunTrigger.ON_CHANGE))
                .isInstanceOf(SubjectFrozenException.class);
        assertThat(runs.findAll(ORG, "order-total", orderId)).hasSize(1);
    }

    @Test
    void aRunWhoseOutputCannotLandFailsAndWritesNothing() {
        seedOrderTotal();
        String orderId = order(1, 10);
        entities.moveBeforeWrite = true;

        CalculationRun moved = run.run(ORG, "order-total", Channel.RELEASED, Map.of("order", orderId), RunTrigger.MANUAL);
        assertThat(moved.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(moved.getError()).contains("is at version 1, not 0");
        assertThat(entities.payload("order", orderId)).doesNotContainKey("total");

        CalculationRun failed = run.run(ORG, "order-total", Channel.RELEASED, Map.of("order", "missing"), RunTrigger.ON_CHANGE);
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getError()).contains("there is no order 'missing'");
        assertThat(events.stream(CalculationFailed.class)).hasSize(2);
        java.util.UUID failedId = failed.getId();
        assertThatThrownBy(() -> freeze.freeze(ORG, failedId)).isInstanceOf(RunNotFreezableException.class);
        Map<String, Object> missing = Map.of("order", "missing");
        assertThatThrownBy(() -> run.run(ORG, "order-total", Channel.RELEASED, missing, RunTrigger.MANUAL))
                .isInstanceOf(InvalidParametersException.class);
    }

    @Test
    void aFailingDecisionIsAFailedRunWithItsTrace() {
        create.create(ORG, """
                calculation: strict
                parameters: { order: { entity: order } }
                tables:
                  t: { hitPolicy: UNIQUE, inputs: [orderNumber], rules: [ { when: ['-'], then: 1 }, { when: ['-'], then: 2 } ] }
                decisions:
                  first: { expr: "1" }
                  second: { table: t }
                output: { entity: order, mapping: { total: second } }
                """);
        String orderId = order(1, 1);

        CalculationRun failed = run.run(ORG, "strict", Channel.DRAFT, Map.of("order", orderId), RunTrigger.MANUAL);

        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getTrace()).extracting(entry -> entry.get("decision")).containsExactly("first", "second");
        assertThat(entities.payload("order", orderId)).doesNotContainKey("total");
    }

    @Test
    void evaluatesAnUnsavedSourceWithoutWriting() {
        String orderId = order(3, 2);

        var evaluation = evaluate.evaluate(ORG, "order-total", Channel.DRAFT, Map.of("order", orderId),
                Calculations.source("order-total"));

        assertThat(evaluation.error()).isNull();
        assertThat(evaluation.result().values()).containsEntry("total", new BigDecimal("6.00"));
        assertThat(evaluation.result().rows().get("lines")).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("lineTotal", new BigDecimal("6")).containsEntry("index", BigDecimal.ZERO));
        assertThat(entities.payload("order", orderId)).doesNotContainKey("total");
        assertThat(runRepository.count()).isZero();
        assertThatThrownBy(() -> evaluate.evaluate(ORG, "x", Channel.DRAFT, Map.of(), "calculation: x\n"))
                .isInstanceOf(InvalidCalculationException.class);
    }

    @Test
    void runsTheSeriesAndUpsertsAResultPerBoat() {
        importer.execute(ORG, Calculations.source("sail-series-ranking"));
        String series = entities.add("series", Map.of("name", "Autumn Cup"));
        entities.type("finish", "startTime", AttributeType.DATE_TIME);
        entities.type("finish", "finishTime", AttributeType.DATE_TIME);
        for (String boat : List.of("Alpha", "Bravo")) {
            for (int round = 1; round <= 2; round++) {
                entities.add("finish", Map.of("series", series, "boat", boat, "round", round, "status", "OK",
                        "startTime", "2026-06-0%dT10:00:00Z".formatted(round),
                        "finishTime", "2026-06-0%dT10:%d0:00Z".formatted(round, boat.equals("Alpha") ? 1 : 2)));
            }
        }

        CalculationRun stored = run.run(ORG, "sail-series-ranking", Channel.DRAFT, Map.of("series", series), RunTrigger.MANUAL);

        assertThat(stored.getStatus()).as(stored.getError()).isEqualTo(RunStatus.COMPLETED);
        assertThat(stored.isFrozen()).as("freeze: onCompletion").isTrue();
        assertThat(entities.queries).containsExactly("finish?series==" + series);
        assertThat(entities.all("series-result")).extracting(result -> result.payload().get("boat") + "=" + result.payload().get("rank"))
                .containsExactlyInAnyOrder("Alpha=1", "Bravo=2");

        Map<String, Object> theSeries = Map.of("series", series);
        assertThatThrownBy(() -> run.run(ORG, "sail-series-ranking", Channel.DRAFT, theSeries, RunTrigger.MANUAL))
                .isInstanceOf(SubjectFrozenException.class);
    }

    @Test
    void knowsWhichAttributesAReleasedCalculationStores() {
        seedOrderTotal();
        assertThat(find.storedAttributesOf(ORG, "order")).containsExactly("total");
        assertThat(find.storedAttributesOf(ORG, "order-line")).isEmpty();
    }

    @Test
    void evaluatesAnUnsavedObjectInPlaceOfTheStoredOne() {
        String orderId = order(1, 1);
        Map<String, Object> draft = Map.of("orderNumber", "ORD-1", "lineItems",
                List.of(Map.of("productName", "A", "quantity", 4, "unitPrice", 5)));

        var withId = evaluate.evaluate(ORG, "order-total", Channel.DRAFT, Map.of("order", orderId),
                Calculations.source("order-total"), Map.of("order", draft));
        var withoutId = evaluate.evaluate(ORG, "order-total", Channel.DRAFT, Map.of(),
                Calculations.source("order-total"), Map.of("order", draft));

        assertThat(withId.result().values()).containsEntry("total", new BigDecimal("20.00"));
        assertThat(withoutId.error()).isNull();
        assertThat(withoutId.result().values()).containsEntry("total", new BigDecimal("20.00"));
        assertThat(entities.payload("order", orderId)).doesNotContainKey("total");
    }

    @Test
    void listsWhatAReleasedCalculationStores() {
        seedOrderTotal();
        assertThat(find.storedOutputsOf(ORG, "order")).singleElement().satisfies(output -> {
            assertThat(output.calculationId()).isEqualTo("order-total");
            assertThat(output.attribute()).isEqualTo("total");
            assertThat(output.parameter()).isEqualTo("order");
        });
    }

    @Test
    void importsAllOrNothing() {
        ImportOutcome outcome = importer.execute(ORG, Calculations.source("order-total") + "---\ncalculation: broken\n");
        assertThat(outcome.errors()).singleElement().asString().startsWith("'broken'");
        assertThat(definitionRepository.count()).isZero();

        ImportOutcome good = importer.execute(ORG, Calculations.source("order-total") + "---\n" + Calculations.source("sail-series-ranking"));
        assertThat(good.created()).isEqualTo(2);
        assertThat(good.written()).containsExactly("order-total", "sail-series-ranking");
        assertThat(find.find(ORG, "order-total", Channel.DRAFT).getSource()).startsWith("# The proposal's sales example");
        assertThat(importer.execute(ORG, Calculations.source("order-total")).updated()).isEqualTo(1);
        assertThat(importer.execute(ORG, Calculations.source("order-total") + "---\n" + Calculations.source("order-total")).errors())
                .singleElement().asString().contains("Duplicate");
        assertThat(importer.removeAll(ORG)).containsExactly("order-total", "sail-series-ranking");
    }
}
