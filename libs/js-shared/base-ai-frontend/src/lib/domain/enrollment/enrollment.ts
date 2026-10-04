/**
 * The enrollment of one subject — its gallery — as `ai-api.yaml` returns it. Plain interfaces: an
 * enrollment is read and acted on, never edited through a generic form. Its photos are the subject's own,
 * held in the attribute the profile's `galleryAttributeKey` names; the gallery is what recognition made of
 * them.
 */

/** NOT_ENROLLED: no photos. PROCESSING: a photo is PENDING. READY: one is ENROLLED, none PENDING. FAILED: none usable. */
export type EnrollmentStatus = 'NOT_ENROLLED' | 'PROCESSING' | 'READY' | 'FAILED';

/** NO_SUBJECT: nothing of the profile's detector class found. AMBIGUOUS: several of similar size. */
export type EnrollmentPhotoStatus = 'PENDING' | 'ENROLLED' | 'NO_SUBJECT' | 'AMBIGUOUS' | 'FAILED';

export interface EnrollmentPhoto {
  photoId: string;
  /** Which of the subject's photos this entry was derived from; opaque. */
  photoRef: string;
  status: EnrollmentPhotoStatus;
  /** Short-lived signed URL of the original. */
  photoUrl?: string;
  /** Short-lived signed URL of the subject's crop, once ENROLLED. */
  cropUrl?: string;
  /** What OCR read on this photo, if anything matched the profile's pattern. */
  observedIdentifierText?: string;
  /** The reading differs from the registered identifier: a wrong photo, or a stale registration. */
  identifierMismatch: boolean;
  failureReason?: string;
  addedAt: string;
}

export interface Enrollment {
  entityName: string;
  objectId: string;
  status: EnrollmentStatus;
  /** The registered identifier, read from the subject's own attribute. */
  identifierText?: string;
  embeddingModel?: string;
  photos: EnrollmentPhoto[];
  updatedAt?: string;
}
