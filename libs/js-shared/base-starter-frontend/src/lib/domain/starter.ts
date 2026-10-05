/**
 * The shapes of base-starter-api.yaml. Hand-written like the other base-* libraries' domain types: the
 * contract generates server-side DTOs only.
 */

/** The definition kinds the importer knows, in import order. */
export type DefinitionKind = 'entity' | 'state' | 'rule' | 'widget' | 'document' | 'workflow' | 'app';

export type ImportStatus = 'applied' | 'would-apply' | 'rejected';

export interface ImportReportItem {
  readonly kind: DefinitionKind;
  /** The definition's key within its kind — an entity code, a state machine's entity name, a rule id. */
  readonly key: string;
  readonly action: 'create' | 'update';
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
  readonly summary?: { readonly created?: number; readonly updated?: number };
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

/** True for anything the server answered with an `ImportReport` body — a 200, a 413 or a 422. */
export function isImportReport(value: unknown): value is ImportReport {
  return typeof value === 'object' && value !== null && 'status' in value && 'dryRun' in value;
}
