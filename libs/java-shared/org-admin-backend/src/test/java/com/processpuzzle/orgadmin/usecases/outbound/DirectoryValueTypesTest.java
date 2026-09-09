package com.processpuzzle.orgadmin.usecases.outbound;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The port's value types defend their own collections. A caller that mutated a {@code DirectoryUser}'s
 * role list afterwards would be editing what the adapter reported, which is the kind of bug that
 * shows up two layers away from its cause.
 */
class DirectoryValueTypesTest {

    @Test
    void aUsersRolesAreCopiedAndUnmodifiable() {
        List<String> mutable = new ArrayList<>(List.of("org-member"));
        DirectoryUser user = new DirectoryUser("id", "ada", null, null, null, true, false, null, mutable);

        mutable.add("org-admin");

        assertThat(user.roles()).containsExactly("org-member");
        assertThatThrownBy(() -> user.roles().add("sneaky"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void absentRolesBecomeAnEmptyListRatherThanNull() {
        assertThat(new DirectoryUser("id", "ada", null, null, null, true, false, null, null).roles())
                .isEmpty();
    }

    @Test
    void aPagesContentIsCopiedAndAbsentContentBecomesEmpty() {
        List<DirectoryUser> mutable = new ArrayList<>();
        DirectoryPage page = new DirectoryPage(mutable, 0L, 0, 20);

        mutable.add(new DirectoryUser("id", "ada", null, null, null, true, false, null, List.of()));

        assertThat(page.content()).isEmpty();
        assertThat(new DirectoryPage(null, 0L, 0, 20).content()).isEmpty();
    }
}
