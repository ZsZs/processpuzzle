export interface BaseConfiguration {
  readonly PIPELINE_STAGE: 'dev' | 'ci' | 'stage' | 'prod';
  readonly APPLICATION_VERSION: string;
  /**
   * The topology the stack is deployed onto. Docker Compose is the only one built today — `'k8s'` is
   * kept because the key's purpose is to survive a second one.
   */
  readonly DEPLOYMENT_ENVIRONMENT: 'docker' | 'k8s';
  readonly BACKEND_SERVICE_PROVIDER: 'rest';
  /**
   * Organization-scoped root of the platform's own backend: `<host>/organizations/<orgKey>`. One Spring
   * Boot Modulith serves every feature and there is one deployment per application stack, so this is
   * the single root an application configures — and it is **the fallback** every per-feature root below
   * resolves through, via `serviceRootOf`.
   *
   * The organization is part of the root rather than of each caller's paths, so the tenant is a
   * deployment concern and no screen threads it through.
   */
  readonly BACKEND_SERVICE_ROOT: string;
  /**
   * Root of the *third-party* REST sources an application integrates with — not of the platform's own
   * features, which every deployment reaches through `BACKEND_SERVICE_ROOT` above. In dev and CI this
   * is the json-server mock (see `tools/mock-backend/README.md`), reverse-proxied under
   * `/third-party/` in a deployed stack.
   */
  readonly THIRD_PARTY_ROOT: string;
  /**
   * Root of the object endpoints, deliberately **not** org-scoped: `/objects/{bucket}/{id}/uri` and its
   * siblings carry no `/organizations/<orgKey>` segment, so this is the bare host.
   */
  readonly OBJECT_STORE_SERVICE_ROOT: string;
  /**
   * Organization-scoped roots of the individual features, same shape as `BACKEND_SERVICE_ROOT`.
   * Optional because one backend serves every feature today: absent, a caller falls back to
   * `BACKEND_SERVICE_ROOT`.
   *
   * They exist so that a feature can move to a host of its own — each backend library is meant to become
   * a service with its own database — without every caller changing. The translations resource is the
   * first to use them, since it is the first resource all seven features expose.
   */
  readonly APP_SERVICE_ROOT?: string;
  readonly DOCUMENT_SERVICE_ROOT?: string;
  readonly RULE_SERVICE_ROOT?: string;
  readonly ENTITY_SERVICE_ROOT?: string;
  readonly WIDGET_SERVICE_ROOT?: string;
  readonly STATE_SERVICE_ROOT?: string;
  readonly WORKFLOW_SERVICE_ROOT?: string;
  readonly AI_SERVICE_ROOT?: string;
  readonly STARTER_SERVICE_ROOT?: string;
  readonly CALCULATION_SERVICE_ROOT?: string;
  /**
   * Roots of the two administration surfaces.
   *
   * Unlike the per-feature roots above these are deliberately *not* org-scoped: every platform-admin
   * path starts with `/platform`, and org-admin carries the tenant inside its own paths
   * (`/organizations/{orgKey}/admin/...`). Falling back to `BACKEND_SERVICE_ROOT` would therefore append
   * those to a root that already ends in `/organizations/<orgKey>` and produce a doubled segment, so
   * an application hosting either surface configures the root explicitly.
   */
  readonly PLATFORM_ADMIN_SERVICE_ROOT?: string;
  readonly ORG_ADMIN_SERVICE_ROOT?: string;
  /**
   * Organization-scoped root of the self-service role endpoints (`<root>/me/roles`), same shape as
   * `BACKEND_SERVICE_ROOT`. Optional and **the switch** for the feature: My Profile offers role
   * selection only when it is set. The Testbed sets it; applications that leave role assignment to
   * administrators omit it, and it does not fall back to `BACKEND_SERVICE_ROOT` for that reason.
   */
  readonly SELF_SERVICE_ROLES_SERVICE_ROOT?: string;
  /**
   * Organization-scoped root of the role catalog (`<root>/role-catalog`), the roles a role field offers.
   * Optional and independent of the self-service switch: an application can offer the catalog without
   * letting its users choose their own roles. Without it the catalog falls back to
   * `SELF_SERVICE_ROLES_SERVICE_ROOT`, so the Testbed needs no change.
   */
  readonly ROLE_CATALOG_SERVICE_ROOT?: string;
}
