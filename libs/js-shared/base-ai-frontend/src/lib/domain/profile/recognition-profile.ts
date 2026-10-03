import { BaseEntity } from '@processpuzzle/base-entity';

/** Entity name of the routable profile; its URL segment is `snakeCaseName` of it, `recognition-profile`. */
export const RECOGNITION_PROFILE_ENTITY_NAME = 'Recognition Profile';

/** The contract's defaults for {@link RecognitionProfile}'s matching settings. */
export const MATCHING_DEFAULTS = { identifierWeight: 0.6, acceptScore: 0.75, acceptMargin: 0.1, sampleFps: 3 } as const;

/**
 * How subjects of one entity type are recognized — the knowledge layer of `ai-api.yaml`.
 *
 * One profile per entity type, addressed by `entityName` (the entity *definition code*, e.g. `boat`), so
 * `id` mirrors it, exactly as a state machine definition's does. The contract nests the four fusion and
 * acceptance thresholds in a `matching` object; they are flattened here because each is one number in one
 * text box, and the mapper nests them again on the way out.
 */
export class RecognitionProfile implements BaseEntity {
  /** Mirror of {@link entityName}; maintained by the mapper and never edited. */
  id: string;
  /** The entity type whose objects are the subjects — its definition code, e.g. `boat`. */
  entityName: string;
  name: string;
  description: string | undefined;
  /** The object class the detector looks for, e.g. `boat`, `person`, `car` (COCO names). */
  detectorClass: string;
  /** The TEXT attribute of the subject holding its registered identifier. Empty: appearance only. */
  identifierAttributeKey: string | undefined;
  /** Regular expression an OCR reading must match to count, e.g. `^[A-Z]{3} ?[0-9]{1,5}$`. */
  identifierPattern: string | undefined;
  // region matching
  identifierWeight: number;
  acceptScore: number;
  acceptMargin: number;
  sampleFps: number;
  // endregion
  // region server-assigned
  orgKey: string | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<RecognitionProfile> = {}) {
    this.entityName = init.entityName ?? '';
    this.id = init.id ?? this.entityName;
    this.name = init.name ?? '';
    this.description = init.description;
    this.detectorClass = init.detectorClass ?? '';
    this.identifierAttributeKey = init.identifierAttributeKey;
    this.identifierPattern = init.identifierPattern;
    this.identifierWeight = init.identifierWeight ?? MATCHING_DEFAULTS.identifierWeight;
    this.acceptScore = init.acceptScore ?? MATCHING_DEFAULTS.acceptScore;
    this.acceptMargin = init.acceptMargin ?? MATCHING_DEFAULTS.acceptMargin;
    this.sampleFps = init.sampleFps ?? MATCHING_DEFAULTS.sampleFps;
    this.orgKey = init.orgKey;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
