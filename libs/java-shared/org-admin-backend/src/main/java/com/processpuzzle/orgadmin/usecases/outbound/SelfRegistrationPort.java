package com.processpuzzle.orgadmin.usecases.outbound;

/**
 * Creates an account for someone registering themselves, with a password they chose.
 *
 * <p>Distinct from {@link UserDirectoryPort#inviteUser}, which deliberately creates accounts with no
 * credentials so an administrator never learns a password.
 */
public interface SelfRegistrationPort {

    /**
     * Creates an enabled account holding the realm's default roles.
     *
     * @param verifyEmail whether the account must verify its email address before first login
     * @throws com.processpuzzle.orgadmin.usecases.inbound.exception.UserAlreadyExistsException
     *         when the email is taken
     * @throws com.processpuzzle.orgadmin.usecases.inbound.exception.PasswordPolicyViolationException
     *         when the provider rejects the password or the profile
     * @throws com.processpuzzle.orgadmin.usecases.inbound.exception.DirectoryUnavailableException
     *         when the provider cannot be reached
     */
    void register(String realm, Registration registration, boolean verifyEmail);

    record Registration(String email, String password, String firstName, String lastName) {
    }
}
