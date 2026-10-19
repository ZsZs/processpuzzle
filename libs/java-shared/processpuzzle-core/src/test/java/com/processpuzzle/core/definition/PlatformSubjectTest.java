package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformSubjectTest {

    @Test
    void keepsItsSubjectTypeAndAttributes() {
        PlatformSubject subject = new PlatformSubject("document", Set.of("slug", "title"));

        assertThat(subject.subjectType()).isEqualTo("document");
        assertThat(subject.attributes()).containsExactlyInAnyOrder("slug", "title");
    }

    @Test
    void copiesTheAttributesSoLaterChangesToTheArgumentDoNotReachIt() {
        Set<String> attributes = new HashSet<>(Set.of("slug"));
        PlatformSubject subject = new PlatformSubject("document", attributes);

        attributes.add("title");

        assertThat(subject.attributes()).containsExactly("slug");
        assertThatThrownBy(() -> subject.attributes().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void treatsMissingAttributesAsNone() {
        assertThat(new PlatformSubject("calculation-run", null).attributes()).isEmpty();
    }
}
