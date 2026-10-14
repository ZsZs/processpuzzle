package com.processpuzzle.release.usecase;

import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgRelease;
import com.processpuzzle.release.domain.OrgReleaseItem;
import java.util.List;

/** One release with the definitions it carried and the migration steps it ran. */
public record ReleaseRecord(OrgRelease release, List<OrgReleaseItem> items, List<OrgMigrationStep> steps) {

    public ReleaseRecord {
        items = List.copyOf(items);
        steps = List.copyOf(steps);
    }

    public ReleaseRecord(OrgRelease release, List<OrgReleaseItem> items) {
        this(release, items, List.of());
    }
}
