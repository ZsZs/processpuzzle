package com.processpuzzle.app.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleMount;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.usecase.service.DefaultShell;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.ModuleMountPort.Launcher;
import com.processpuzzle.core.definition.ModuleMountPort.MountHint;
import com.processpuzzle.core.definition.ModuleMountPort.MountOutcome;
import com.processpuzzle.core.definition.ModuleMountPort.Nav;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rule of business-starters design decision 11a: mounts are added to the application's draft, and released at once
 * only when the application is still the untouched default shell.
 */
class AppModuleMountAdapterTest {

    private static final MountHint STOCK = new MountHint("inv-stock", "stock", new Nav("inventory", "nav.title"),
            new Launcher("module:launcher.svg", "launcher.title", null, List.of(), "overview"));

    private AppDefinitionRepository apps;
    private ModuleDefinitionRepository modules;
    private AppModuleMountAdapter adapter;

    @BeforeEach
    void setUp() {
        apps = mock(AppDefinitionRepository.class);
        modules = mock(ModuleDefinitionRepository.class);
        adapter = new AppModuleMountAdapter(apps, modules);
        ModuleDefinition stock = new ModuleDefinition("acme", "inv-stock", "Stock", Channel.DRAFT);
        stock.setRoutes(List.of(new AppRoute("overview", "Overview", null, null, List.of(), RouteTarget.ofWidgets(List.of()))));
        when(modules.findByOrgKeyAndKeyAndChannel("acme", "inv-stock", Channel.DRAFT)).thenReturn(Optional.of(stock));
    }

    private AppDefinition shell() {
        AppDefinition app = new AppDefinition("acme", "app", "Acme", null, null, DefaultShell.graph());
        app.markDefaultShell();
        app.publish();
        when(apps.findByOrgKeyAndId("acme", "app")).thenReturn(Optional.of(app));
        return app;
    }

    @Test
    void anUntouchedDefaultShellGetsTheMountsAndIsReleasedWithThem() {
        AppDefinition app = shell();

        MountOutcome outcome = adapter.mount("acme", List.of(STOCK));

        assertThat(outcome.done()).containsExactly("inv-stock");
        assertThat(outcome.released()).isTrue();
        assertThat(app.getDraftGraph().modules()).extracting(ModuleMount::moduleKey).containsExactly("inv-stock");
        assertThat(app.getPublishedGraph()).isEqualTo(app.getDraftGraph());
        assertThat(app.isPublished()).isTrue();
        assertThat(app.isUntouchedShell()).as("a mount is the platform's doing, not the customer's").isTrue();
        verify(apps).save(app);
    }

    @Test
    void anApplicationTheCustomerHasSavedGetsTheMountsInItsDraftOnly() {
        AppDefinition app = shell();
        // the designer saves the application: its drafts are now the customer's to release
        app.replaceDraft(app.getName(), null, null, app.getDraftGraph());
        AppGraph publishedBefore = app.getPublishedGraph();

        MountOutcome outcome = adapter.mount("acme", List.of(STOCK));

        assertThat(outcome.done()).containsExactly("inv-stock");
        assertThat(outcome.released()).isFalse();
        assertThat(app.getDraftGraph().modules()).hasSize(1);
        assertThat(app.getPublishedGraph()).isEqualTo(publishedBefore);
        assertThat(app.getPublishedGraph().modules()).isEmpty();
        assertThat(app.isPublished()).isFalse();
    }

    @Test
    void aRowThatPredatesTheFlagCountsAsTouchedAndIsNotReleased() {
        AppDefinition app = new AppDefinition("acme", "app", "Acme", null, null, DefaultShell.graph());
        app.publish();
        when(apps.findByOrgKeyAndId("acme", "app")).thenReturn(Optional.of(app));

        assertThat(adapter.mount("acme", List.of(STOCK)).released()).isFalse();
        assertThat(app.getPublishedGraph().modules()).isEmpty();
    }

    @Test
    void aSkippedHintChangesAndSavesNothing() {
        AppDefinition app = shell();

        MountOutcome outcome = adapter.mount("acme", List.of(new MountHint("missing", "missing", null, null)));

        assertThat(outcome.done()).isEmpty();
        assertThat(outcome.skipped()).containsEntry("missing", "the module does not exist");
        assertThat(outcome.released()).isFalse();
        assertThat(app.getRevision()).isEqualTo(1L);
        verify(apps, never()).save(any());
    }

    @Test
    void anOrganizationWithoutAnApplicationSkipsEveryHintWithThatReason() {
        when(apps.findByOrgKeyAndId("acme", "app")).thenReturn(Optional.empty());
        when(apps.findByOrgKey("acme")).thenReturn(List.of());

        MountOutcome outcome = adapter.mount("acme", List.of(STOCK));

        assertThat(outcome.done()).isEmpty();
        assertThat(outcome.skipped()).containsEntry("inv-stock", "the organization has no application");
        verify(apps, never()).save(any());
    }

    @Test
    void anOrganizationsOnlyApplicationIsUsedWhateverItIsCalled() {
        AppDefinition app = new AppDefinition("acme", "claims-app", "Claims", null, null, DefaultShell.graph());
        when(apps.findByOrgKeyAndId("acme", "app")).thenReturn(Optional.empty());
        when(apps.findByOrgKey("acme")).thenReturn(List.of(app));

        assertThat(adapter.mount("acme", List.of(STOCK)).done()).containsExactly("inv-stock");
    }

    @Test
    void twoApplicationsAndNoneCalledAppIsAmbiguousSoNothingIsMounted() {
        when(apps.findByOrgKeyAndId("acme", "app")).thenReturn(Optional.empty());
        when(apps.findByOrgKey("acme")).thenReturn(List.of(
                new AppDefinition("acme", "one", "One", null, null, null), new AppDefinition("acme", "two", "Two", null, null, null)));

        assertThat(adapter.mount("acme", List.of(STOCK)).done()).isEmpty();
    }

    @Test
    void unmountingFollowsTheSameRule() {
        AppDefinition app = shell();
        adapter.mount("acme", List.of(STOCK));

        MountOutcome outcome = adapter.unmount("acme", List.of("inv-stock"));

        assertThat(outcome.done()).containsExactly("inv-stock");
        assertThat(outcome.released()).isTrue();
        assertThat(app.getDraftGraph().modules()).isEmpty();
        assertThat(app.getPublishedGraph().modules()).isEmpty();
    }

    @Test
    void unmountingInACustomersApplicationLeavesTheLiveOneAlone() {
        AppDefinition app = shell();
        adapter.mount("acme", List.of(STOCK));
        app.replaceDraft(app.getName(), null, null, app.getDraftGraph());

        MountOutcome outcome = adapter.unmount("acme", List.of("inv-stock"));

        assertThat(outcome.released()).isFalse();
        assertThat(app.getDraftGraph().modules()).isEmpty();
        assertThat(app.getPublishedGraph().modules()).hasSize(1);
    }
}
