package com.processpuzzle.app.usecase;

import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.AppDefinitionStatus;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.app.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.AppRuleValidator;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the two decisions the draft/publish design rests on: publishing must not advance the
 * revision counter (or {@code status} stops meaning anything), and editing must not disturb the
 * published snapshot (or serving the previous revision to end users stops working).
 */
class PublishAppDefinitionTest {

    private AppDefinitionRepository repository;
    private PublishAppDefinition publishAppDefinition;
    private UpdateAppDefinition updateAppDefinition;
    private AppMapper mapper;
    private EvaluateObject evaluateObject;
    private ObjectProvider<EvaluateObject> evaluateObjectProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        when(repository.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));

        ObjectProvider<EntityNameRegistry> entityRegistryProvider = mock(ObjectProvider.class);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<OrganizationAccessPolicy> policyProvider = mock(ObjectProvider.class);
        when(policyProvider.getIfUnique(any())).thenReturn(new com.processpuzzle.app.usecase.port
                .PermitAllOrganizationAccessPolicy());

        // No rule module wired by default; the rule-aware tests below opt in.
        evaluateObject = mock(EvaluateObject.class);
        evaluateObjectProvider = mock(ObjectProvider.class);
        when(evaluateObjectProvider.getIfAvailable()).thenReturn(null);

        AppDefinitionValidator validator = new AppDefinitionValidator(
                entityRegistryProvider, new AppRuleValidator(evaluateObjectProvider));
        OrganizationGuard guard = new OrganizationGuard(policyProvider);
        mapper = new AppMapper();

        publishAppDefinition = new PublishAppDefinition(repository, validator, guard, mapper);
        updateAppDefinition = new UpdateAppDefinition(repository, validator, guard, mapper);
    }

    @Test
    void publish_doesNotAdvanceTheRevisionCounter() {
        AppDefinition definition = stored(validGraph());
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));

        AppDefinition published = publishAppDefinition.execute("my-org", "claims-app");

        assertThat(published.getRevision()).isEqualTo(1L);
        assertThat(published.getPublishedRevision()).isEqualTo(1L);
        assertThat(published.isPublished()).isTrue();
        assertThat(mapper.toModelStatus(published)).isEqualTo(AppDefinitionStatus.PUBLISHED);
    }

    @Test
    void editAfterPublish_returnsToDraftButKeepsServingThePublishedGraph() {
        AppDefinition definition = stored(validGraph());
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));
        publishAppDefinition.execute("my-org", "claims-app");

        AppDefinition edited = updateAppDefinition.execute("my-org", "claims-app", emptyishInput());

        assertThat(edited.getRevision()).isEqualTo(2L);
        assertThat(edited.getPublishedRevision()).isEqualTo(1L);
        assertThat(edited.isPublished()).isFalse();
        assertThat(mapper.toModelStatus(edited)).isEqualTo(AppDefinitionStatus.DRAFT);

        assertThat(edited.graphFor(true).pages()).isEmpty();
        assertThat(edited.graphFor(false).pages()).hasSize(1);
    }

    @Test
    void republishAfterEdit_promotesTheNewGraph() {
        AppDefinition definition = stored(validGraph());
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));
        publishAppDefinition.execute("my-org", "claims-app");
        updateAppDefinition.execute("my-org", "claims-app", emptyishInput());

        AppDefinition republished = publishAppDefinition.execute("my-org", "claims-app");

        assertThat(republished.getRevision()).isEqualTo(2L);
        assertThat(republished.getPublishedRevision()).isEqualTo(2L);
        assertThat(republished.isPublished()).isTrue();
        assertThat(republished.graphFor(false).pages()).isEmpty();
    }

    @Test
    void publishingTwiceWithoutEditing_isIdempotent() {
        AppDefinition definition = stored(validGraph());
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));

        publishAppDefinition.execute("my-org", "claims-app");
        AppDefinition again = publishAppDefinition.execute("my-org", "claims-app");

        assertThat(again.getRevision()).isEqualTo(1L);
        assertThat(again.getPublishedRevision()).isEqualTo(1L);
    }

    @Test
    void publishingAnInvalidDefinition_isRejectedSoItCannotGoLive() {
        AppGraph brokenGraph = new AppGraph(null, null,
                List.of(new Region("sidenav",
                        List.of(new NavNode("nav-1", "Broken", null, null, "page-missing", List.of(), List.of())),
                        List.of())),
                List.of());
        AppDefinition definition = stored(brokenGraph);
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> publishAppDefinition.execute("my-org", "claims-app"))
                .isInstanceOf(AppDefinitionInvalidException.class);
        assertThat(definition.hasPublishedRevision()).isFalse();
    }

    /**
     * The reason severity is threaded through at all: several of the rules ProcessPuzzle ships are
     * WARNING or INFO, and a draft is expected to trip them. If any problem blocked the write, an app
     * could not be saved on its way to conforming — and a tenant that wants a convention enforced
     * strictly says so by giving its rule ERROR severity.
     */
    @Test
    void aDefinitionTrippingOnlyAWarningRule_isStillSavedAndPublished() {
        givenRuleViolations(new RuleViolation("app-declares-a-populated-sidenav", "App has navigation",
                Severity.WARNING, "This app declares no sidenav navigation.", null));
        AppDefinition definition = stored(validGraph());
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));

        assertThat(updateAppDefinition.execute("my-org", "claims-app", emptyishInput())).isNotNull();
        assertThat(publishAppDefinition.execute("my-org", "claims-app").isPublished()).isTrue();
    }

    @Test
    void aDefinitionTrippingAnErrorRule_isRejected() {
        givenRuleViolations(new RuleViolation("app-id-is-route-safe", "App id is route-safe",
                Severity.ERROR, "An app id is lowercase letters, digits and single hyphens.", null));
        AppDefinition definition = stored(validGraph());
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> updateAppDefinition.execute("my-org", "claims-app", emptyishInput()))
                .isInstanceOf(AppDefinitionInvalidException.class);
        assertThatThrownBy(() -> publishAppDefinition.execute("my-org", "claims-app"))
                .isInstanceOf(AppDefinitionInvalidException.class);
        assertThat(definition.hasPublishedRevision()).isFalse();
    }

    @Test
    void publishingAnUnknownDefinition_is404() {
        when(repository.findByOrgKeyAndId("my-org", "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publishAppDefinition.execute("my-org", "nope"))
                .isInstanceOf(AppDefinitionNotFoundException.class);
    }

    /** Wires the rule module in for this test, with the given violations on every evaluation. */
    private void givenRuleViolations(RuleViolation... violations) {
        when(evaluateObjectProvider.getIfAvailable()).thenReturn(evaluateObject);
        boolean passed = Arrays.stream(violations).noneMatch(v -> v.severity() == Severity.ERROR);
        when(evaluateObject.execute(any(), any(), any()))
                .thenReturn(new EvaluationOutcome(passed, List.of(violations)));
    }

    private static AppDefinition stored(AppGraph graph) {
        return new AppDefinition("my-org", "claims-app", "Claims Management", null, null, graph);
    }

    private static AppGraph validGraph() {
        AppPage page = new AppPage("page-claims-list", "Claims", null, List.of());
        NavNode nav = new NavNode("nav-claims", "Claims", null, null, "page-claims-list", List.of(), List.of());
        return new AppGraph(null, null, List.of(new Region("sidenav", List.of(nav), List.of())), List.of(page));
    }

    /** A valid but page-less revision, so draft and published snapshots are distinguishable. */
    private static AppDefinitionInput emptyishInput() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setRegions(List.of(new RegionDefinition(RegionType.CONTENT)));
        input.setPages(List.<PageDefinition>of());
        input.setTheme(null);
        return input;
    }
}
