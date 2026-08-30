import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of `ToolDefinition` — an external REST system a task's step may invoke. Defined
 * separately from a process, under `/tools`, so several processes can share one.
 */

/** Mirrors the contract's `AuthType`. */
export enum AuthType {
  NONE = 'NONE',
  BEARER_TOKEN = 'BEARER_TOKEN',
  BASIC = 'BASIC',
  API_KEY = 'API_KEY',
}

/** Mirrors the contract's `HttpMethod`. */
export enum HttpMethod {
  GET = 'GET',
  POST = 'POST',
  PUT = 'PUT',
  PATCH = 'PATCH',
  DELETE = 'DELETE',
}

/**
 * How base-workflow authenticates against the tool. An interface rather than a class, because it is
 * never edited as a row: it is a nested single *object*, and no descriptor can address one — a
 * descriptor names a property, not a path — so {@link ToolDefinition} carries its two fields
 * flattened and only the mapper touches this shape. Same arrangement as `AppDefinition.theme` in
 * base-app.
 */
export interface ToolAuthConfig {
  type?: AuthType;
  /**
   * Key name of the secret in the application's secret store — an environment variable name or a
   * Vault path. base-workflow never stores a credential value, only the reference.
   */
  secretRef?: string;
}

/** One callable operation of a tool: a method, a path relative to the tool's base URL, and a body template. */
export class ToolOperation implements BaseEntity {
  id: string;
  method: HttpMethod | undefined;
  path: string;
  description?: string;
  /** JSON template for the request body; `${...}` placeholders are PPCL over the process context. */
  payloadTemplate?: string;
  /**
   * HTTP status codes treated as success. `integer[]` by contract, `string[]` here: the workspace has
   * no numeric-array control, and `TagsComponent` — the only control that edits a flat list — emits
   * strings. `ToolDefinitionMapper` converts at the boundary, so the payload stays numeric.
   */
  expectedStatusCodes?: string[];

  constructor(init: Partial<ToolOperation> = {}) {
    this.id = init.id ?? '';
    this.method = init.method;
    this.path = init.path ?? '';
    this.description = init.description;
    this.payloadTemplate = init.payloadTemplate;
    this.expectedStatusCodes = init.expectedStatusCodes;
  }
}

/**
 * An external system and the operations base-workflow may call on it.
 *
 * {@link type} and {@link secretRef} are the two fields of the contract's nested `auth` object,
 * lifted onto this class under their own names so the generated form can address them. {@link auth}
 * itself is kept alongside them, not discarded: `PUT /tools/{toolId}` is a full replacement, so the
 * mapper merges the two edited fields *onto* the object they came from and a field a later contract
 * version adds survives a round trip through a form that knows nothing about it.
 */
export class ToolDefinition implements BaseEntity {
  id: string;
  name: string;
  description?: string;
  baseUrl: string;
  // region flattened auth
  type: AuthType | undefined;
  secretRef: string | undefined;
  // endregion
  operations: ToolOperation[];
  /** The object the two fields above were lifted out of, preserved so a save cannot drop a sibling. */
  auth: ToolAuthConfig | undefined;
  // region server-assigned
  version: number | undefined;
  createdAt: string | undefined;
  // endregion

  constructor(init: Partial<ToolDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.baseUrl = init.baseUrl ?? '';
    this.type = init.type;
    this.secretRef = init.secretRef;
    this.operations = init.operations ?? [];
    this.auth = init.auth;
    this.version = init.version;
    this.createdAt = init.createdAt;
  }
}
