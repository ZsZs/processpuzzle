package com.processpuzzle.release.usecase;

import com.processpuzzle.release.domain.OrgRelease;
import com.processpuzzle.release.domain.OrgReleaseItem;
import java.util.List;

/** One release with the definitions it carried. */
public record ReleaseRecord(OrgRelease release, List<OrgReleaseItem> items) {

    public ReleaseRecord {
        items = List.copyOf(items);
    }
}
