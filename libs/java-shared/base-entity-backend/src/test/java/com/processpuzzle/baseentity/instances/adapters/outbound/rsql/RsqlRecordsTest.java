package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RsqlRecordsTest {

    @Test
    void pathSegmentRecord_propertiesAndEquality() {
        PathSegment segment1 = new PathSegment("name", false);
        PathSegment segment2 = new PathSegment("name", false);
        PathSegment segment3 = new PathSegment("addresses", true);

        assertThat(segment1.attributeCode()).isEqualTo("name");
        assertThat(segment1.array()).isFalse();
        assertThat(segment1).isEqualTo(segment2);
        assertThat(segment1.hashCode()).isEqualTo(segment2.hashCode());
        assertThat(segment1).isNotEqualTo(segment3);
        assertThat(segment1.toString()).contains("name");
    }

    @Test
    void resolvedAttributePathRecord_propertiesAndEquality() {
        PathSegment seg = new PathSegment("age", false);
        ResolvedAttributePath path1 = new ResolvedAttributePath(List.of(seg), ValueKindView.NUMBER);
        ResolvedAttributePath path2 = new ResolvedAttributePath(List.of(seg), ValueKindView.NUMBER);

        assertThat(path1.segments()).containsExactly(seg);
        assertThat(path1.valueKind()).isEqualTo(ValueKindView.NUMBER);
        assertThat(path1).isEqualTo(path2);
        assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
        assertThat(path1.toString()).contains("age").contains("NUMBER");
    }
}
