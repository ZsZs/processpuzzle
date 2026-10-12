<#ftl output_format="plainText">
<#-- The platform invites only an EXISTING account: a second subscription with an e-mail that already
     has a login. So the mail says "accept and keep your password" rather than Keycloak's generic
     "join the organization", and offers no account creation. -->
<#if firstName?has_content>${msg("activationGreeting", firstName)}<#else>${msg("activationGreetingNoName")}</#if>

${msg("orgInviteBody", link, linkExpiration, realmName, organization.name, linkExpirationFormatter(linkExpiration))}
