package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Membership of a common is what an organization has installed: it is a member of the common of each starter it
 * installed that has one. Nothing is stored for it beyond the starter's own record.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSpaceDirectoryAdapter implements DataSpaceDirectory {

    private final DataSpaceRepository dataSpaces;

    /** The common of each installed starter that has one, by key; a common is not a member of itself. */
    @Override
    public Set<String> commonsOf(String orgKey) {
        return dataSpaces.findCommonKeysOf(orgKey).stream().filter(common -> !common.equals(orgKey))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean isCommon(String orgKey) {
        return dataSpaces.existsById(orgKey);
    }

    @Override
    public java.util.Set<String> membersOf(String commonKey) {
        return new java.util.LinkedHashSet<>(dataSpaces.findMemberKeysOf(commonKey));
    }
}
