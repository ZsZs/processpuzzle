export { authMatcher } from './auth-matcher';
export { AuthService } from './service/auth.service';
export { authTokenInterceptor } from './service/auth-token.interceptor';
export { authTokenInterceptorFor, provideAuthTokenInterceptor } from './service/provide-auth-token-interceptor';
export { currentOrgKey, firstPathSegment, looksLikeOrgKey, resolveTenantRealm } from './service/tenant-realm.resolver';
export { AUTHENTICATION_CONFIGURATION, AUTHENTICATION_SERVICE, type AuthenticationConfiguration, provideAuthenticationService } from './service/provide-authentication.service';
export type { FirebaseAuthConfig } from './service/firebase-auth.config';
export type { KeycloakAuthConfig } from './service/keycloak-auth.config';
export { User } from './user/user';
