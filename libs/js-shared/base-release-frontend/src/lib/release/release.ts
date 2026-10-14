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
  /** The migration steps that would resolve the problem. */
  resolutions?: ProblemResolution[];
}

export type ResolutionInputKind = 'text' | 'number' | 'boolean' | 'date' | 'dateTime';

/** A migration step prepared to resolve a problem, and the one value the designer still has to give it. */
export interface ProblemResolution {
  step: MigrationStep;
  /** The parameter the value goes to: a name such as `value`, or a path into a map parameter such as `mapping.PENDING`. */
  input?: string;
  inputKind?: ResolutionInputKind;
  /** The values the input may take; none when any value of its kind will do. */
  choices?: string[];
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

/** The value as the input's kind stores it: a number for `number`, a boolean for `boolean`, the text otherwise. */
export function inputValue(kind: ResolutionInputKind | undefined, value: string | boolean): unknown {
  if (kind === 'boolean') return value === true || value === 'true';
  if (kind === 'number') return Number(value);
  return value;
}

/** The step a resolution makes once the designer has given its input `value`. */
export function resolvedStep(resolution: ProblemResolution, value: string | boolean): MigrationStep {
  const step: MigrationStep = { ...resolution.step, id: undefined };
  const parameters: Record<string, unknown> = { ...(step.parameters ?? {}) };
  if (resolution.input) {
    const [name, ...path] = resolution.input.split('.');
    if (path.length) {
      const nested = typeof parameters[name] === 'object' && parameters[name] !== null ? { ...(parameters[name] as Record<string, unknown>) } : {};
      nested[path.join('.')] = inputValue(resolution.inputKind, value);
      parameters[name] = nested;
    } else {
      parameters[name] = inputValue(resolution.inputKind, value);
    }
  }
  return { ...step, parameters };
}

/** Whether a value has been given for the resolution's input, so that it can be applied. */
export function isComplete(resolution: ProblemResolution, value: string | boolean | undefined): boolean {
  if (!resolution.input || resolution.inputKind === 'boolean') return true;
  if (value === undefined || value === '') return false;
  return resolution.inputKind !== 'number' || !Number.isNaN(Number(value));
}

export function isPendingRelease(value: unknown): value is PendingRelease {
  return typeof value === 'object' && value !== null && Array.isArray((value as PendingRelease).changes) && Array.isArray((value as PendingRelease).problems);
}
