/**
 * The shapes of base-starter-api.yaml. Hand-written like the other base-* libraries' domain types: the
 * contract generates server-side DTOs only.
 */

/** The definition kinds the importer knows, in import order. */
export type DefinitionKind = 'entity' | 'state' | 'rule' | 'widget' | 'document' | 'workflow' | 'app';

export type ImportStatus = 'applied' | 'would-apply' | 'rejected';

/**
 * `create` — new; `update` — existed before and the starter brings it again; `delete` — existed before and
 * the starter does not bring it. A starter replaces the organization's definitions rather than merging.
 */
export type ImportAction = 'create' | 'update' | 'delete';

export interface ImportReportItem {
  readonly kind: DefinitionKind;
  /** The definition's key within its kind — an entity code, a state machine's entity name, a rule id. */
  readonly key: string;
  readonly action: ImportAction;
}

export interface ImportError {
  /** Path of the offending file inside the bundle, when the problem is in one. */
  readonly file?: string;
  readonly message: string;
}

export interface ImportReport {
  readonly dryRun: boolean;
  readonly status: ImportStatus;
  readonly starterId?: string;
  readonly version?: string;
  readonly summary?: { readonly created?: number; readonly updated?: number; readonly deleted?: number };
  readonly items?: readonly ImportReportItem[];
  readonly errors?: readonly ImportError[];
}

export interface InstalledStarter {
  readonly starterId: string;
  readonly version: string;
  readonly installedAt: string;
  readonly installedBy?: string;
  /** Definitions of this starter whose current content no longer matches what was imported. */
  readonly customizedDefinitions?: number;
}

/** One installable version of a catalog starter. */
export interface CatalogStarterVersion {
  readonly version: string;
  readonly publishedAt?: string;
  readonly status: 'published' | 'deprecated';
}

/** A starter of the catalog, with its installable versions newest first. */
export interface CatalogStarter {
  readonly id: string;
  readonly name: string;
  readonly description?: string;
  readonly author?: string;
  readonly license?: string;
  readonly category?: string;
  readonly tags?: readonly string[];
  readonly versions: readonly CatalogStarterVersion[];
}

/** Which catalog starter, at which version, the user means to install. */
export interface StarterSelection {
  readonly starterId: string;
  readonly version: string;
}

/** True for anything the server answered with an `ImportReport` body — a 200, a 409, a 413 or a 422. */
export function isImportReport(value: unknown): value is ImportReport {
  return typeof value === 'object' && value !== null && 'status' in value && 'dryRun' in value;
}
