package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import com.processpuzzle.core.definition.Channel;
import java.util.Collection;
import java.util.Set;

/** Which commons an organization reads shared types from, for the code that has the directory only as an optional bean. */
public final class SharedTypes {

    private SharedTypes() {
    }

    /** The commons {@code orgKey} is a member of; empty without a directory, and for an organization without a common. */
    public static Set<String> commonsOf(ObjectProvider<DataSpaceDirectory> dataSpaces, String orgKey) {
        DataSpaceDirectory directory = dataSpaces.getIfAvailable();
        return directory == null ? Set.of() : directory.commonsOf(orgKey);
    }

    /**
     * The released shared definition of {@code code} in the first of {@code commons} that has one. A code is shared by at
     * most one common — the keys of two starters never meet — so which comes first does not matter.
     */
    public static Optional<BaseEntityDefinition> sharedIn(EntityDefinitionRepository repository, Collection<String> commons, String code) {
        return commons.stream()
            .map(common -> repository.findByOrgKeyAndCodeAndChannel(common, code, Channel.RELEASED).filter(BaseEntityDefinition::isShared))
            .flatMap(Optional::stream)
            .findFirst();
    }
}
