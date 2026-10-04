import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { RecognitionProfile } from './recognition-profile';
import { RecognitionProfileMapper } from './recognition-profile.mapper';

/**
 * REST access to `/organizations/{orgKey}/recognition-profiles`. The organization is part of the configured
 * service root; `AI_SERVICE_ROOT` is optional and falls back to `BACKEND_SERVICE_ROOT`.
 */
@Injectable({ providedIn: 'root' })
export class RecognitionProfileService extends BaseEntityRestService<RecognitionProfile> {
  constructor(protected override entityMapper: RecognitionProfileMapper) {
    super(entityMapper, 'AI_SERVICE_ROOT', 'recognition-profiles');
  }
}
