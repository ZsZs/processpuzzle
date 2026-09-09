package com.processpuzzle.orgadmin.adapters.inbound;

import com.processpuzzle.orgadmin.OrgAdminTestFixtures;
import com.processpuzzle.orgadmin.model.OrganizationUserInvite;
import com.processpuzzle.orgadmin.model.OrganizationUserUpdate;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryPage;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryRole;
import com.processpuzzle.orgadmin.usecases.outbound.DirectoryUser;
import com.processpuzzle.orgadmin.usecases.outbound.UserDirectoryPort;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrgAdminMapperTest {

    private final OrgAdminMapper mapper = new OrgAdminMapper();

    @Test
    void aUserCarriesItsProfileFlagsAndRoles() {
        var model = mapper.toModel(OrgAdminTestFixtures.user("org-member", "claims-auditor"));

        assertThat(model.getId()).isEqualTo(OrgAdminTestFixtures.USER_ID);
        assertThat(model.getUsername()).isEqualTo("ada");
        assertThat(model.getEmail()).isEqualTo("ada@my-org.example");
        assertThat(model.getFirstName()).isEqualTo("Ada");
        assertThat(model.getEnabled()).isTrue();
        assertThat(model.getEmailVerified()).isFalse();
        assertThat(model.getRoles()).containsExactly("org-member", "claims-auditor");
    }

    @Test
    void aCreationTimestampMapsOntoTheContractAsAUtcOffset() {
        var model = mapper.toModel(OrgAdminTestFixtures.user());

        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    /** Keycloak does not always report one, and an epoch would be a plausible-looking lie. */
    @Test
    void anAbsentCreationTimestampMapsToNull() {
        var model = mapper.toModel(new DirectoryUser("id", "ada", null, null, null,
                true, false, null, List.of()));

        assertThat(model.getCreatedAt()).isNull();
    }

    /**
     * The page count is derived from the estimated total rather than reported, because the directory
     * reports none. Ceiling division, so a partial last page still counts.
     */
    @Test
    void thePageCountIsDerivedFromTheEstimatedTotal() {
        var model = mapper.toModel(new DirectoryPage(
                List.of(OrgAdminTestFixtures.user()), 21L, 0, 20));

        assertThat(model.getTotalElements()).isEqualTo(21L);
        assertThat(model.getTotalPages()).isEqualTo(2);
        assertThat(model.getNumber()).isZero();
        assertThat(model.getSize()).isEqualTo(20);
    }

    /** A zero page size would otherwise divide by zero on the way to the pager. */
    @Test
    void aZeroPageSizeYieldsNoPagesRatherThanADivisionByZero() {
        assertThat(mapper.toModel(new DirectoryPage(List.of(), 0L, 0, 0)).getTotalPages()).isZero();
    }

    @Test
    void aRoleCarriesItsPlatformManagedFlag() {
        assertThat(mapper.toModel(new DirectoryRole("org-admin", "Administers.", true)))
                .satisfies(model -> {
                    assertThat(model.getName()).isEqualTo("org-admin");
                    assertThat(model.getDescription()).isEqualTo("Administers.");
                    assertThat(model.getPlatformManaged()).isTrue();
                });
        assertThat(mapper.toModel(new DirectoryRole("claims-auditor", null, false))
                .getPlatformManaged()).isFalse();
    }

    @Test
    void anInvitePayloadBecomesThePortsNewUser() {
        OrganizationUserInvite input = new OrganizationUserInvite("ada", "ada@my-org.example");
        input.setFirstName("Ada");
        input.setLastName("Lovelace");

        assertThat(mapper.toNewUser(input)).isEqualTo(
                new UserDirectoryPort.NewUser("ada", "ada@my-org.example", "Ada", "Lovelace"));
    }

    /**
     * {@code enabled} stays null when the payload omits it, so editing a name does not silently
     * re-enable a disabled account.
     */
    @Test
    void anUpdateWithoutTheEnabledFlagLeavesItUnspecified() {
        OrganizationUserUpdate input = new OrganizationUserUpdate();
        input.setFirstName("Augusta");

        assertThat(mapper.toProfile(input)).isEqualTo(
                new UserDirectoryPort.UserProfile(null, "Augusta", null, null));
    }

    @Test
    void anUpdateCarryingTheEnabledFlagPreservesIt() {
        OrganizationUserUpdate input = new OrganizationUserUpdate();
        input.setEnabled(false);

        assertThat(mapper.toProfile(input).enabled()).isFalse();
    }

    @Test
    void theRoleListMapsEveryEntry() {
        assertThat(mapper.toRoleList(List.of(
                OrgAdminTestFixtures.role("org-admin"), OrgAdminTestFixtures.role("claims-auditor"))))
                .extracting(com.processpuzzle.orgadmin.model.OrganizationRole::getName)
                .containsExactly("org-admin", "claims-auditor");
    }
}
