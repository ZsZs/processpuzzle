/** The types of base-release-api.yaml. */

export type DefinitionChange = 'added' | 'changed' | 'removed';

/** One definition whose draft differs from its released row. */
export interface PendingChange {
  kind: string;
  key: string;
  change: DefinitionChange;
}

/** An `error` refuses the release; a `warning` lets it through once confirmed. */
export interface ReleaseProblem {
  severity: 'error' | 'warning';
  kind: string;
  key: string;
  errorId: string;
  message: string;
  /** The part of the definition the problem is about — an attribute's code, a state's key. */
  subject?: string;
  /** How many existing objects the problem is about. */
  affectedObjects?: number;
}

/** What a release would carry, and what would refuse or qualify it. Also the body of a refused release. */
export interface PendingRelease {
  changes: PendingChange[];
  /** Found on the data as the pending migration steps leave it. */
  problems: ReleaseProblem[];
  /** What each pending migration step did in the dry run, in order. */
  steps?: MigrationStepOutcome[];
}

/** A change a release makes to existing objects so that they fit the definitions it releases. */
export interface MigrationStep {
  /** Assigned when the step is stored. */
  id?: string;
  kind: string;
  key: string;
  /** For entities: `renameAttribute`, `fillAttribute` or `mapValues`. */
  operation: string;
  subject?: string;
  parameters?: Record<string, unknown>;
}

/** `skipped`: the step cannot run against the definitions as they stand. `failed`: it refuses the release. */
export interface MigrationStepOutcome {
  step: MigrationStep;
  outcome: 'applied' | 'skipped' | 'failed';
  affectedObjects?: number;
  reason?: string;
}

export interface ReleaseItem {
  kind: string;
  key: string;
  change: DefinitionChange;
  contentHash?: string;
}

export interface Release {
  releaseNumber: number;
  releasedAt: string;
  releasedBy?: string;
  items: ReleaseItem[];
  /** The migration steps the release ran, in order. */
  steps?: MigrationStepOutcome[];
}

export function isPendingRelease(value: unknown): value is PendingRelease {
  return typeof value === 'object' && value !== null && Array.isArray((value as PendingRelease).changes) && Array.isArray((value as PendingRelease).problems);
}
