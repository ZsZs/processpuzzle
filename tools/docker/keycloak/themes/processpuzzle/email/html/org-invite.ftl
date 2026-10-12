<#-- See text/org-invite.ftl for why the invitation gets its own wording. -->
<#import "template.ftl" as layout>
<@layout.emailLayout>
<p><#if firstName?has_content>${msg("activationGreeting", firstName)}<#else>${msg("activationGreetingNoName")}</#if></p>
${kcSanitize(msg("orgInviteBodyHtml", link, linkExpiration, realmName, organization.name, linkExpirationFormatter(linkExpiration)))?no_esc}
</@layout.emailLayout>
