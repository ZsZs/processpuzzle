import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { RecognitionProfile } from '../../domain/profile/recognition-profile';
import { createRecognitionProfileDescriptor } from '../../domain/profile/recognition-profile.descriptors';
import { RecognitionProfileMapper } from '../../domain/profile/recognition-profile.mapper';
import { RecognitionProfileService } from '../../domain/profile/recognition-profile.service';
import { RecognitionProfileStore } from '../../domain/profile/recognition-profile.store';

@Injectable()
export class RecognitionProfileFacade extends BaseEntityFacade<RecognitionProfile> {
  readonly entityType = RecognitionProfile;

  private readonly mapperRef = inject(RecognitionProfileMapper);
  private readonly serviceRef = inject(RecognitionProfileService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return RecognitionProfileStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createRecognitionProfileDescriptor();
  }
}
