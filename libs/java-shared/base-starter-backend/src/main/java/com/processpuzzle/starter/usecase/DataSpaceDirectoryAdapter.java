package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership of a common is what an organization has installed: it is a member of the common of each starter it
 * installed that has one. Nothing is stored for it beyond the starter's own record.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSpaceDirectoryAdapter implements DataSpaceDirectory {

    private final DataSpaceRepository dataSpaces;

    /** An organization has zero or one starter, so zero or one common; with several, the first by key. */
    @Override
    public Optional<String> commonOf(String orgKey) {
        return dataSpaces.findCommonKeysOf(orgKey).stream().filter(common -> !common.equals(orgKey)).findFirst();
    }

    @Override
    public boolean isCommon(String orgKey) {
        return dataSpaces.existsById(orgKey);
    }
}
