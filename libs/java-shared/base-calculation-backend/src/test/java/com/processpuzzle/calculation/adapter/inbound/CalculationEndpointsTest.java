package com.processpuzzle.calculation.adapter.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationRun;
import com.processpuzzle.calculation.domain.RunStatus;
import com.processpuzzle.calculation.domain.RunTrigger;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.engine.Calculations;
import com.processpuzzle.calculation.engine.EvaluationResult;
import com.processpuzzle.calculation.engine.TraceEntry;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.CreateCalculationDefinition;
import com.processpuzzle.calculation.usecase.DeleteCalculationDefinition;
import com.processpuzzle.calculation.usecase.EvaluateCalculation;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.FindCalculationRuns;
import com.processpuzzle.calculation.usecase.FreezeRun;
import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.calculation.usecase.ImportOutcome;
import com.processpuzzle.calculation.usecase.ReplaceCalculationDefinition;
import com.processpuzzle.calculation.usecase.RunCalculation;
import com.processpuzzle.calculation.usecase.exception.CalculationAlreadyExistsException;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.calculation.usecase.exception.InvalidParametersException;
import com.processpuzzle.calculation.usecase.exception.RunNotFoundException;
import com.processpuzzle.calculation.usecase.exception.RunNotFreezableException;
import com.processpuzzle.calculation.usecase.exception.StaleCalculationException;
import com.processpuzzle.calculation.usecase.exception.SubjectFrozenException;
import com.processpuzzle.core.definition.Channel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CalculationEndpointsTest {

    private static final String DEFINITIONS = "/organizations/org/calculation-definitions";

    private CreateCalculationDefinition create;
    private ReplaceCalculationDefinition replace;
    private DeleteCalculationDefinition deleteUseCase;
    private FindCalculationDefinition find;
    private ImportCalculationDefinitions importer;
    private EvaluateCalculation evaluate;
    private RunCalculation run;
    private FreezeRun freeze;
    private FindCalculationRuns runs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        create = mock(CreateCalculationDefinition.class);
        replace = mock(ReplaceCalculationDefinition.class);
        deleteUseCase = mock(DeleteCalculationDefinition.class);
        find = mock(FindCalculationDefinition.class);
        importer = mock(ImportCalculationDefinitions.class);
        evaluate = mock(EvaluateCalculation.class);
        run = mock(RunCalculation.class);
        freeze = mock(FreezeRun.class);
        runs = mock(FindCalculationRuns.class);
        CalculationMapper mapper = new CalculationMapper();
        CalculationValidator validator = new CalculationValidator(new CamundaFeelExpressionEngine());
        mvc = MockMvcBuilders.standaloneSetup(
                        new CalculationDefinitionsEndpoint(create, replace, deleteUseCase, find, importer, validator, mapper,
                                mock(CalculationReleaseParticipant.class)),
                        new CalculationsEndpoint(evaluate, run, runs, find, mapper),
                        new CalculationRunsEndpoint(freeze, runs, mapper))
                .setControllerAdvice(new CalculationApiExceptionHandler())
                .build();
    }

    private static CalculationDefinition stored() {
        String source = Calculations.source("order-total");
        CalculationDefinition definition = CalculationDefinition.builder().orgKey("org").id("order-total").version(3L).build();
        definition.applySource(source, CalculationParser.parse(source).model());
        definition.setCreatedAt(Instant.parse("2026-10-18T10:00:00Z"));
        return definition;
    }

    private static CalculationRun storedRun() {
        return CalculationRun.builder().id(UUID.fromString("00000000-0000-0000-0000-000000000001")).orgKey("org")
                .calculationId("order-total").definitionVersion(2).channel(Channel.RELEASED).definitionSource("calculation: x")
                .triggeredBy(RunTrigger.ON_CHANGE).subjectType("order").subjectId("o-1").status(RunStatus.COMPLETED)
                .result(Map.of("values", Map.of("total", 10), "order", List.of("total")))
                .trace(List.of(Map.of("decision", "total", "kind", "EXPR", "scope", "scalar", "value", 10)))
                .written(1).startedAt(Instant.parse("2026-10-18T10:00:00Z")).build();
    }

    @Test
    void listsAndReadsDefinitionsWithTheirParameters() throws Exception {
        when(find.findAll("org", Channel.DRAFT)).thenReturn(List.of(stored()));
        when(find.find("org", "order-total", Channel.RELEASED)).thenReturn(stored());

        mvc.perform(get(DEFINITIONS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-total"))
                .andExpect(jsonPath("$[0].name").value("Order total"))
                .andExpect(jsonPath("$[0].definitionVersion").value(1))
                .andExpect(jsonPath("$[0].outputEntity").value("order"))
                .andExpect(jsonPath("$[0].parameters[0].name").value("order"))
                .andExpect(jsonPath("$[0].parameters[0].entity").value("order"))
                .andExpect(jsonPath("$[0].parameters[1].type").value("string"));
        mvc.perform(get(DEFINITIONS + "/order-total").param("revision", "released"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.releaseState").doesNotExist());
    }

    @Test
    void createsReplacesAndDeletes() throws Exception {
        when(create.create(eq("org"), any())).thenReturn(stored());
        when(replace.replace(eq("org"), eq("order-total"), any(), eq(3L))).thenReturn(stored());
        when(find.find("org", "order-total", Channel.DRAFT)).thenReturn(stored());

        mvc.perform(post(DEFINITIONS).contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"calculation: x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put(DEFINITIONS + "/order-total").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"calculation: x\",\"version\":3}"))
                .andExpect(status().isOk());
        mvc.perform(get(DEFINITIONS + "/order-total")).andExpect(status().isOk());
        mvc.perform(delete(DEFINITIONS + "/order-total")).andExpect(status().isNoContent());
        verify(deleteUseCase).delete("org", "order-total");
    }

    @Test
    void validatesWithTheDependencyGraph() throws Exception {
        mvc.perform(post(DEFINITIONS + "/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":" + quoted(Calculations.source("order-total")) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.order[0]").value("lineTotal"))
                .andExpect(jsonPath("$.decisions[0].scope").value("row:lines"))
                .andExpect(jsonPath("$.decisions[0].kind").value("EXPR"))
                .andExpect(jsonPath("$.dependencies[0].from").value("lineTotal"));
        mvc.perform(post(DEFINITIONS + "/validate").contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"x: [\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").exists());
    }

    @Test
    void importsAFile() throws Exception {
        when(importer.execute("org", "x")).thenReturn(new ImportOutcome(1, 0, List.of(), List.of("a")));

        mvc.perform(multipart(DEFINITIONS + "/import").file(new MockMultipartFile("file", "x.yaml", "text/yaml", "x".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void evaluatesRunsAndFreezes() throws Exception {
        EvaluationResult result = new EvaluationResult(Map.of("total", 10), Map.of(), Map.of(), List.of("total"),
                List.of(new TraceEntry("total", com.processpuzzle.calculation.domain.definition.DecisionKind.EXPR, "scalar", 10, null)));
        when(evaluate.evaluate(eq("org"), eq("order-total"), eq(Channel.DRAFT), anyMap(), isNull(), anyMap()))
                .thenReturn(new EvaluateCalculation.Evaluation(result, "stopped"));
        when(run.run(eq("org"), eq("order-total"), eq(Channel.RELEASED), anyMap(), eq(RunTrigger.MANUAL))).thenReturn(storedRun());
        when(runs.findAll("org", "order-total", null)).thenReturn(List.of(storedRun()));
        when(runs.findAll("org", null, "o-1")).thenReturn(List.of(storedRun()));
        when(runs.find(eq("org"), any())).thenReturn(storedRun());
        when(freeze.freeze(eq("org"), any())).thenReturn(storedRun());

        mvc.perform(post("/organizations/org/calculations/order-total/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":{\"order\":\"o-1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.total").value(10))
                .andExpect(jsonPath("$.trace[0].kind").value("EXPR"))
                .andExpect(jsonPath("$.error").value("stopped"));
        mvc.perform(post("/organizations/org/calculations/order-total/runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parameters\":{\"order\":\"o-1\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.triggeredBy").value("ON_CHANGE"))
                .andExpect(jsonPath("$.result.values.total").value(10))
                .andExpect(jsonPath("$.result.order[0]").value("total"));
        mvc.perform(get("/organizations/org/calculations/order-total/runs")).andExpect(jsonPath("$[0].subjectId").value("o-1"));
        mvc.perform(get("/organizations/org/calculation-runs").param("subjectId", "o-1")).andExpect(jsonPath("$[0].written").value(1));
        String runPath = "/organizations/org/calculation-runs/00000000-0000-0000-0000-000000000001";
        mvc.perform(get(runPath)).andExpect(jsonPath("$.definitionSource").value("calculation: x"));
        mvc.perform(get(runPath + "/trace")).andExpect(jsonPath("$[0].decision").value("total"));
        mvc.perform(post(runPath + "/freeze")).andExpect(status().isOk());
    }

    @Test
    void mapsTheModulesExceptions() throws Exception {
        String body = "{\"source\":\"calculation: x\"}";
        when(create.create(eq("org"), any()))
                .thenThrow(new InvalidCalculationException("x", List.of("bad")))
                .thenThrow(new CalculationAlreadyExistsException("org", "x"));
        mvc.perform(post(DEFINITIONS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorId").value("calculation.invalid"));
        mvc.perform(post(DEFINITIONS).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());

        when(replace.replace(any(), any(), any(), any())).thenThrow(new StaleCalculationException("x"));
        mvc.perform(put(DEFINITIONS + "/x").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        when(find.find("org", "x", Channel.DRAFT)).thenThrow(new CalculationNotFoundException("org", "x"));
        mvc.perform(get(DEFINITIONS + "/x")).andExpect(status().isNotFound());

        when(run.run(any(), any(), any(), any(), any()))
                .thenThrow(new SubjectFrozenException("x", "order", "o"))
                .thenThrow(new InvalidParametersException("missing"));
        String runsPath = "/organizations/org/calculations/x/runs";
        mvc.perform(post(runsPath).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").value("calculation.subject-frozen"));
        mvc.perform(post(runsPath).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        UUID runId = UUID.randomUUID();
        when(runs.find("org", runId)).thenThrow(new RunNotFoundException("org", runId));
        mvc.perform(get("/organizations/org/calculation-runs/" + runId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorId").value("calculation-run.not-found"));
        when(freeze.freeze("org", runId)).thenThrow(new RunNotFreezableException("failed"));
        mvc.perform(post("/organizations/org/calculation-runs/" + runId + "/freeze")).andExpect(status().isConflict());
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
