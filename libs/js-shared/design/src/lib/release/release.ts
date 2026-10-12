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
}

/** What a release would carry, and what would refuse or qualify it. Also the body of a refused release. */
export interface PendingRelease {
  changes: PendingChange[];
  problems: ReleaseProblem[];
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
}

export function isPendingRelease(value: unknown): value is PendingRelease {
  return typeof value === 'object' && value !== null && Array.isArray((value as PendingRelease).changes) && Array.isArray((value as PendingRelease).problems);
}
