package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.model.OrganizationRole;
import com.processpuzzle.orgadmin.model.OrganizationUser;
import com.processpuzzle.orgadmin.model.OrganizationUserInvite;
import com.processpuzzle.orgadmin.model.OrganizationUserUpdate;
import com.processpuzzle.orgadmin.model.PageOfOrganizationUser;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Directory records to contract, and request payloads to the port's own value types. */
@Component
public class OrgAdminMapper {

    public OrganizationUser toModel(DirectoryUser user) {
        OrganizationUser model = new OrganizationUser(user.id(), user.username(), user.enabled());
        model.setEmail(user.email());
        model.setFirstName(user.firstName());
        model.setLastName(user.lastName());
        model.setEmailVerified(user.emailVerified());
        model.setCreatedAt(toOffsetDateTime(user.createdAt()));
        model.setRoles(user.roles());
        return model;
    }

    public PageOfOrganizationUser toModel(DirectoryPage page) {
        PageOfOrganizationUser model = new PageOfOrganizationUser();
        model.setContent(page.content().stream().map(this::toModel).toList());
        model.setTotalElements(page.totalElements());
        // Derived rather than reported: the directory gives no exact total, so the page count is
        // whatever the estimated total implies. See DirectoryPage on why the estimate is honest.
        model.setTotalPages(page.size() == 0 ? 0
                : (int) Math.ceil((double) page.totalElements() / page.size()));
        model.setNumber(page.number());
        model.setSize(page.size());
        return model;
    }

    public OrganizationRole toModel(DirectoryRole role) {
        OrganizationRole model = new OrganizationRole(role.name());
        model.setDescription(role.description());
        model.setPlatformManaged(role.platformManaged());
        return model;
    }

    public List<OrganizationRole> toRoleList(List<DirectoryRole> roles) {
        return roles.stream().map(this::toModel).toList();
    }

    public UserDirectoryPort.NewUser toNewUser(OrganizationUserInvite input) {
        return new UserDirectoryPort.NewUser(input.getUsername(), input.getEmail(),
                input.getFirstName(), input.getLastName());
    }

    /**
     * {@code enabled} stays {@code null} when the payload omits it, so a profile edit does not have
     * to know the current value to leave the flag alone — see {@code UserProfile}.
     */
    public UserDirectoryPort.UserProfile toProfile(OrganizationUserUpdate input) {
        return new UserDirectoryPort.UserProfile(input.getEmail(), input.getFirstName(),
                input.getLastName(), input.getEnabled());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
