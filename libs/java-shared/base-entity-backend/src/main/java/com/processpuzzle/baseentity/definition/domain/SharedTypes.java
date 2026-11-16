package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;

/** Which common an organization reads shared types from, for the code that has the directory only as an optional bean. */
public final class SharedTypes {

    private SharedTypes() {
    }

    /** The common {@code orgKey} is a member of; empty without a directory, and for an organization without a common. */
    public static Optional<String> commonOf(ObjectProvider<DataSpaceDirectory> dataSpaces, String orgKey) {
        DataSpaceDirectory directory = dataSpaces.getIfAvailable();
        return directory == null ? Optional.empty() : directory.commonOf(orgKey);
    }
}
