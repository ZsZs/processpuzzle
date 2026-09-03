package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.domain.BillingInterval;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.domain.PlanLimit;
import com.processpuzzle.platformadmin.domain.PlanRepository;
import com.processpuzzle.platformadmin.domain.UsageMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The seeder for the plan catalog. Two of its properties are worth more than the happy path: it never
 * touches an existing plan — a restart must not reprice what customers are subscribed to — and
 * nothing in it can fail startup.
 */
class DefaultPlanLoaderTest {

    private PlanRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(PlanRepository.class);
        when(repository.existsById(anyString())).thenReturn(false);
        when(repository.save(any(Plan.class))).thenAnswer(call -> call.getArgument(0));
    }

    /** The bundled catalog: proves the shipped YAML actually parses, not merely that parsing works. */
    @Test
    void seedsTheBundledCatalog() throws IOException {
        loaderOver(new PathMatchingResourcePatternResolver()).loadDefaults();

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        verify(repository, org.mockito.Mockito.atLeast(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Plan::getCode)
                .contains("free", "team", "enterprise");
    }

    @Test
    void aPlanCarriesItsPriceIntervalAndLimits() {
        loaderOver(resolverFor("""
                plans:
                  - code: team
                    name: Team
                    interval: YEARLY
                    currency: USD
                    amountMinor: 4900
                    limits:
                      USERS: 25
                """)).loadDefaults();

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        verify(repository).save(saved.capture());
        Plan plan = saved.getValue();
        assertThat(plan.getInterval()).isEqualTo(BillingInterval.YEARLY);
        assertThat(plan.getCurrency()).isEqualTo("USD");
        assertThat(plan.getAmountMinor()).isEqualTo(4900L);
        assertThat(plan.getLimits()).extracting(PlanLimit::getMetric).containsExactly(UsageMetric.USERS);
    }

    /**
     * The behaviour an operator depends on: a price changed by hand, or a plan customers are already
     * on, survives a redeploy.
     */
    @Test
    void anExistingPlanIsLeftExactlyAsItIs() {
        when(repository.existsById("team")).thenReturn(true);

        loaderOver(resolverFor("plans:\n  - code: team\n    name: Renamed\n    amountMinor: 1\n"))
                .loadDefaults();

        verify(repository, never()).save(any());
    }

    @Test
    void anAbsentIntervalDefaultsToMonthlyAndAnUnrecognisedOneDoesNotFailThePlan() {
        loaderOver(resolverFor("""
                plans:
                  - code: a
                    name: A
                  - code: b
                    name: B
                    interval: FORTNIGHTLY
                """)).loadDefaults();

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Plan::getInterval)
                .containsExactly(BillingInterval.MONTHLY, BillingInterval.MONTHLY);
    }

    /**
     * One stale metric name must not cost a deployment its whole catalog, so the limit is dropped and
     * the plan is still created.
     */
    @Test
    void anUnknownMetricIsDroppedRatherThanRejectingThePlan() {
        loaderOver(resolverFor("""
                plans:
                  - code: team
                    name: Team
                    limits:
                      USERS: 25
                      TELEPATHY: 1
                """)).loadDefaults();

        ArgumentCaptor<Plan> saved = ArgumentCaptor.forClass(Plan.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLimits()).extracting(PlanLimit::getMetric)
                .containsExactly(UsageMetric.USERS);
    }

    @Test
    void aPlanWithoutACodeOrNameIsSkipped() {
        loaderOver(resolverFor("plans:\n  - name: Nameless\n  - code: ok\n")).loadDefaults();

        verify(repository, never()).save(any());
    }

    @Test
    void anUnparseableFileIsSkippedRatherThanFailingStartup() {
        assertThatCode(() -> loaderOver(resolverFor("plans: [ this is not a plan")).loadDefaults())
                .doesNotThrowAnyException();

        verify(repository, never()).save(any());
    }

    @Test
    void aFileDeclaringNoPlansIsSkipped() {
        loaderOver(resolverFor("plans: []\n")).loadDefaults();

        verify(repository, never()).save(any());
    }

    @Test
    void noFilesAtAllIsNotAnError() throws IOException {
        ResourcePatternResolver empty = mock(ResourcePatternResolver.class);
        when(empty.getResources(anyString())).thenReturn(new Resource[0]);

        assertThatCode(() -> loaderOver(empty).loadDefaults()).doesNotThrowAnyException();
    }

    @Test
    void anUnscannableClasspathIsNotAnError() throws IOException {
        ResourcePatternResolver failing = mock(ResourcePatternResolver.class);
        when(failing.getResources(anyString())).thenThrow(new IOException("no classpath"));

        assertThatCode(() -> loaderOver(failing).loadDefaults()).doesNotThrowAnyException();
    }

    private DefaultPlanLoader loaderOver(ResourcePatternResolver resolver) {
        return new DefaultPlanLoader(repository, resolver);
    }

    private static ResourcePatternResolver resolverFor(String yaml) {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource resource = new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "test-plans.yaml";
            }
        };
        try {
            when(resolver.getResources(anyString())).thenReturn(new Resource[]{resource});
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return resolver;
    }
}
