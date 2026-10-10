<#-- See text/executeActions.ftl for why activation gets its own wording. -->
<#import "template.ftl" as layout>
<@layout.emailLayout>
<#if requiredActions?? && requiredActions?seq_contains("VERIFY_EMAIL") && requiredActions?seq_contains("UPDATE_PASSWORD")>
<p><#if (user.firstName)?has_content>${msg("activationGreeting", user.firstName)}<#else>${msg("activationGreetingNoName")}</#if></p>
${kcSanitize(msg("activationBodyHtml", link, linkExpirationFormatter(linkExpiration)))?no_esc}
<#else>
<#outputformat "plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>
${kcSanitize(msg("executeActionsBodyHtml",link, linkExpiration, realmName, requiredActionsText, linkExpirationFormatter(linkExpiration)))?no_esc}
</#if>
</@layout.emailLayout>
