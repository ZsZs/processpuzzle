/**
 * The shapes of base-starter-api.yaml. Hand-written like the other base-* libraries' domain types: the
 * contract generates server-side DTOs only.
 */

/** The definition kinds the importer knows, in import order. */
export type DefinitionKind =
  | 'entity'
  | 'state'
  | 'rule'
  | 'calculation'
  | 'widget'
  | 'document'
  | 'event'
  | 'artifact'
  | 'role'
  | 'tool'
  | 'task'
  | 'workflow'
  | 'module'
  | 'app'
  | 'translation'
  | 'asset';

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
  /** The version the platform offers, when it is newer than the installed one. */
  readonly offeredVersion?: string;
  /** Whether a newer version is on offer; {@link StarterService.upgrade} applies it. */
  readonly upgradeAvailable?: boolean;
}

/**
 * What an upgrade does to one definition. `add` — new in the starter; `replace` — in the starter and untouched here;
 * `remove` — dropped by the starter and untouched here; `keep-customized` — changed here, so this organization's
 * version stays; `keep-deleted` — deleted here, not brought back; `keep-in-use` — dropped by the starter, but
 * something still depends on it.
 */
export type UpgradeAction = 'add' | 'replace' | 'remove' | 'keep-customized' | 'keep-deleted' | 'keep-in-use';

export interface UpgradeReportItem {
  readonly kind: DefinitionKind;
  readonly key: string;
  readonly action: UpgradeAction;
  /** Why a definition was kept, when there is something to say. */
  readonly note?: string;
}

/** The outcome of an upgrade, or of its dry run: drafts the organization's administrator then reviews and releases. */
export interface UpgradeReport {
  readonly dryRun: boolean;
  readonly status: ImportStatus;
  readonly starterId?: string;
  readonly fromVersion?: string;
  readonly toVersion?: string;
  readonly items?: readonly UpgradeReportItem[];
  /** Kinds the starter brings that cannot be upgraded definition by definition yet; left as they are. */
  readonly skippedKinds?: readonly string[];
  readonly errors?: readonly ImportError[];
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

/** True for anything the server answered with an `UpgradeReport` body — a 200 or a 422. */
export function isUpgradeReport(value: unknown): value is UpgradeReport {
  return typeof value === 'object' && value !== null && 'status' in value && 'dryRun' in value;
}

/** True for anything the server answered with an `ImportReport` body — a 200, a 409, a 413 or a 422. */
export function isImportReport(value: unknown): value is ImportReport {
  return typeof value === 'object' && value !== null && 'status' in value && 'dryRun' in value;
}
