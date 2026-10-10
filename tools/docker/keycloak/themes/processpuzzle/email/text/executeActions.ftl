<#ftl output_format="plainText">
<#-- Activation (verify the address AND set the first password) is the one execute-actions mail this
     platform sends, and it is a welcome, not an administrator's request. Any other combination of
     actions keeps Keycloak's own wording. -->
<#if requiredActions?? && requiredActions?seq_contains("VERIFY_EMAIL") && requiredActions?seq_contains("UPDATE_PASSWORD")>
<#if (user.firstName)?has_content>${msg("activationGreeting", user.firstName)}<#else>${msg("activationGreetingNoName")}</#if>

${msg("activationBody", link, linkExpirationFormatter(linkExpiration))}
<#else>
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></#list><#else></#if></#assign>

${msg("executeActionsBody",link, linkExpiration, realmName, requiredActionsText, linkExpirationFormatter(linkExpiration))}
</#if>
