package com.processpuzzle.composition;

import com.processpuzzle.orgadmin.api.UserNameQuery;
import com.processpuzzle.workflow.execution.usecases.outbound.UserNamePort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * base-workflow's {@link UserNamePort} answered by org-admin's directory, so a task assigned to someone else is
 * recorded with that person's name too — not only one claimed for oneself, which the token already names.
 */
@Component
public class UserNameAdapter implements UserNamePort {

    private final UserNameQuery userNames;

    public UserNameAdapter(UserNameQuery userNames) {
        this.userNames = userNames;
    }

    @Override
    public Optional<String> displayNameOf(String orgKey, String userId) {
        return userNames.displayNameOf(orgKey, userId);
    }
}
