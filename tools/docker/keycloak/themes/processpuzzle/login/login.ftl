<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    ${msg("loginAccountTitle")}
  <#elseif section = "form">
    <#if !realm.loginWithEmailAllowed>
      <#assign usernameLabel = msg("username")>
    <#elseif !realm.registrationEmailAsUsername>
      <#assign usernameLabel = msg("usernameOrEmail")>
    <#else>
      <#assign usernameLabel = msg("email")>
    </#if>
    <#assign errorText = "">
    <#if message?has_content && message.type = "error">
      <#assign errorText = message.summary>
    </#if>

    <pp-login-form
      action="${url.loginAction}"
      heading="${msg("loginAccountTitle")}"
      username="${(login.username!'')}"
      username-label="${usernameLabel}"
      password-label="${msg("password")}"
      submit-label="${msg("doLogIn")}"
      error="${errorText}"
      <#if realm.resetPasswordAllowed>forgot-password-url="${url.loginResetCredentialsUrl}" forgot-password-label="${msg("doForgotPassword")}"</#if>
      <#if realm.password && realm.registrationAllowed && !registrationDisabled??>register-url="${url.registrationUrl}" register-label="${msg("doRegister")}"</#if>
    ></pp-login-form>

    <#-- Shown only until the bundle has registered the element; if the bundle is missing from the image
         it simply stays, so signing in never depends on the Angular Element. -->
    <form id="pp-fallback-form" action="${url.loginAction}" method="post">
      <#if errorText?has_content><p role="alert">${errorText}</p></#if>
      <label for="pp-fallback-username">${usernameLabel}</label>
      <input id="pp-fallback-username" name="username" type="text" value="${(login.username!'')}" autocomplete="username" required />
      <label for="pp-fallback-password">${msg("password")}</label>
      <input id="pp-fallback-password" name="password" type="password" autocomplete="current-password" required />
      <button type="submit">${msg("doLogIn")}</button>
    </form>
    <script src="${url.resourcesPath}/js/main.js" type="module"></script>
    <script>
      customElements.whenDefined('pp-login-form').then(function () {
        document.getElementById('pp-fallback-form').hidden = true;
      });
    </script>
  </#if>
</@layout.registrationLayout>
