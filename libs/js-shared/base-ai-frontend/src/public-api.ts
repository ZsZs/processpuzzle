/*
 * Public API Surface of @processpuzzle/base-ai
 */

export {
  BASE_AI_TRANSLATION_SOURCE,
  BASE_AI_TRANSLOCO_SCOPE,
  ENTITY_ENROLLMENT_I18N_KEY,
  ENTITY_ENROLLMENT_I18N_SCOPE,
  RECOGNITION_PROFILE_I18N_SCOPE,
} from './lib/base-ai.i18n';
export { MATCHING_DEFAULTS, RECOGNITION_PROFILE_ENTITY_NAME, RecognitionProfile } from './lib/domain/profile/recognition-profile';
export { DETECTOR_CLASSES, createRecognitionProfileDescriptor } from './lib/domain/profile/recognition-profile.descriptors';
export { RecognitionProfileMapper } from './lib/domain/profile/recognition-profile.mapper';
export { RecognitionProfileService } from './lib/domain/profile/recognition-profile.service';
export { RecognitionProfileStore } from './lib/domain/profile/recognition-profile.store';
export { ProfiledEntityRegistry } from './lib/domain/profile/profiled-entity.registry';
export { ENROLLMENT_PHOTO_TYPES, type Enrollment, type EnrollmentPhoto, type EnrollmentPhotoStatus, type EnrollmentStatus, type MediaUploadSlot } from './lib/domain/enrollment/enrollment';
export { EnrollmentService } from './lib/domain/enrollment/enrollment.service';
export { RecognitionProfileFacade } from './lib/feature/profile/recognition-profile.facade';
export { ENTITY_ENROLLMENT_TAB, ENTITY_ENROLLMENT_TAB_SEGMENT } from './lib/feature/enrollment/entity-enrollment-tab';
export { ENROLLMENT_MAX_POLLS, ENROLLMENT_POLL_MS, EntityEnrollmentTabComponent } from './lib/feature/enrollment/entity-enrollment-tab.component';
export { EntityEnrollmentTabContributor, provideEntityEnrollmentTab } from './lib/feature/enrollment/entity-enrollment-tab.contributor';
export { BASE_AI_ENTITY_FACADES, BASE_AI_FACADE_PROVIDERS } from './lib/base-ai.providers';
export { BASE_AI_ROUTES } from './lib/base-ai.routes';
