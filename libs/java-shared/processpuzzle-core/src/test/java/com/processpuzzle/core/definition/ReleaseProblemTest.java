package com.processpuzzle.core.definition;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseProblemTest {

    private static final MigrationStep FILL =
        new MigrationStep(null, DefinitionKinds.ENTITY, "invoice", "fillAttribute", "currency", Map.of());

    @Test
    void aProblemStartsWithoutSubjectCountOrResolutions() {
        ReleaseProblem problem = ReleaseProblem.warning(DefinitionKinds.ENTITY, "invoice", "entity.release.x", "Message.");

        assertThat(problem.isError()).isFalse();
        assertThat(problem.subject()).isNull();
        assertThat(problem.affectedObjects()).isNull();
        assertThat(problem.resolutions()).isEmpty();
    }

    /** Each step of building a problem keeps what the earlier ones added. */
    @Test
    void subjectCountAndResolutionsAddUp() {
        ProblemResolution resolution = ProblemResolution.asking(FILL, "value", "text", List.of("EUR", "HUF"));

        ReleaseProblem problem = ReleaseProblem.error(DefinitionKinds.ENTITY, "invoice", "entity.release.x", "Message.")
            .about("currency", 4L)
            .resolvableBy(List.of(resolution))
            .about("currency", 5L);

        assertThat(problem.isError()).isTrue();
        assertThat(problem.affectedObjects()).isEqualTo(5L);
        assertThat(problem.resolutions()).containsExactly(resolution);
    }

    @Test
    void aResolutionWithoutInputAsksForNothing() {
        ProblemResolution resolution = ProblemResolution.of(FILL);

        assertThat(resolution.input()).isNull();
        assertThat(resolution.inputKind()).isNull();
        assertThat(resolution.choices()).isEmpty();
        assertThat(ProblemResolution.asking(FILL, "value", "text", null).choices()).isEmpty();
    }
}
