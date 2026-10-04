/**
 * A recognition as `ai-api.yaml` returns it: a few camera frames identified against a candidate list. The
 * whole answer base-ai gives — which candidate is in front of the camera, and how sure it is. What the
 * answer is used for is the caller's business.
 */

/** QUEUED until the vision server has answered. */
export type RecognitionStatus = 'QUEUED' | 'DONE' | 'FAILED';

/**
 * MATCHED: `objectId` is the subject. NEEDS_REVIEW: a subject was seen, but a person has to choose among
 * `candidates`. NO_SUBJECT: nothing of the profile's detector class in any frame.
 */
export type RecognitionOutcome = 'MATCHED' | 'NEEDS_REVIEW' | 'NO_SUBJECT';

export interface RecognitionCandidate {
  objectId: string;
  /** Fused score, 0..1. */
  score: number;
  identifierScore?: number;
  embeddingScore?: number;
}

export interface Recognition {
  recognitionId: string;
  entityName: string;
  status: RecognitionStatus;
  outcome?: RecognitionOutcome;
  /** The recognized subject, when MATCHED. */
  objectId?: string;
  score?: number;
  observedIdentifierText?: string;
  observedIdentifierConfidence?: number;
  /** Short-lived signed URL of the best crop of the subject seen. */
  cropUrl?: string;
  /** The top three, best first. */
  candidates: RecognitionCandidate[];
  /** Candidates without an enrolled photo, which could only be matched by identifier. */
  candidatesWithoutGallery: string[];
  failureReason?: string;
  createdAt: string;
  finishedAt?: string;
}

/** An upload slot: where to PUT the file, and the key that names it afterwards. */
export interface MediaUploadSlot {
  mediaKey: string;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
  expiresAt: string;
}

/** The content types the backend accepts for a camera frame. */
export const RECOGNITION_FRAME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/** At most this many frames per recognition, as the contract allows. */
export const RECOGNITION_MAX_FRAMES = 5;
