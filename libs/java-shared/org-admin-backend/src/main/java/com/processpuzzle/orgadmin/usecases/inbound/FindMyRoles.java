package com.processpuzzle.orgadmin.usecases.inbound;

import org.springframework.stereotype.Service;

/** The caller's own realm roles, and the roles they may pick for themselves. */
@Service
public class FindMyRoles {

    private final SelfServiceRoleSupport support;

    FindMyRoles(SelfServiceRoleSupport support) {
        this.support = support;
    }

    public MyRoles execute(String orgKey) {
        return support.read(support.caller(orgKey));
    }
}
