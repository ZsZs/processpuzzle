package com.processpuzzle.calendar.entry.adapters.inbound;

import com.processpuzzle.calendar.api.DirectoryApi;
import com.processpuzzle.calendar.entry.usecases.inbound.SearchDirectory;
import com.processpuzzle.calendar.model.DirectoryMatch;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the generated {@code DirectoryApi}: finding the people to invite. */
@RestController
public class DirectoryEndpoint implements DirectoryApi {

    private final SearchDirectory search;

    public DirectoryEndpoint(SearchDirectory search) {
        this.search = search;
    }

    @Override
    public ResponseEntity<List<DirectoryMatch>> searchDirectory(String orgKey, String q) {
        return ResponseEntity.ok(search.search(orgKey, q).stream()
                .map(match -> new DirectoryMatch().sub(match.sub()).displayName(match.displayName()))
                .toList());
    }
}
