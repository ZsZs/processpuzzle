package com.processpuzzle.starter.usecase;

import com.processpuzzle.starter.domain.DataSpace;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Names the organization that is the common of a starter. Idempotent; a starter has one common, for good. */
@Component
@RequiredArgsConstructor
@Transactional
public class RegisterDataSpace {

    private final DataSpaceRepository dataSpaces;

    public DataSpace register(String starterId, String commonKey) {
        return dataSpaces.findByStarterId(starterId).map(existing -> {
            if (!existing.getCommonKey().equals(commonKey)) {
                throw new IllegalStateException("The common of starter '%s' is '%s' and stays so.".formatted(starterId, existing.getCommonKey()));
            }
            return existing;
        }).orElseGet(() -> {
            if (dataSpaces.existsById(commonKey)) {
                throw new IllegalStateException("'%s' is the common of another starter.".formatted(commonKey));
            }
            return dataSpaces.save(new DataSpace(commonKey, starterId));
        });
    }
}
