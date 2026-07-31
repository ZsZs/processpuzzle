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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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

        AppDefinitionValidator validator = new AppDefinitionValidator(entityRegistryProvider);
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

    @Test
    void publishingAnUnknownDefinition_is404() {
        when(repository.findByOrgKeyAndId("my-org", "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publishAppDefinition.execute("my-org", "nope"))
                .isInstanceOf(AppDefinitionNotFoundException.class);
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
