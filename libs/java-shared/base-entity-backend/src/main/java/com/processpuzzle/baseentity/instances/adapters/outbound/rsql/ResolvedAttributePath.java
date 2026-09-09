package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import java.util.List;

public record ResolvedAttributePath(List<PathSegment> segments, ValueKindView valueKind) {
}
