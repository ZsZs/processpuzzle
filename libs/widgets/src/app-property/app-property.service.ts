import { ApplicationProperty } from './app-property';
import { BaseEntityFirestoreService } from '@processpuzzle/base-entity';
import { ApplicationPropertyMapper } from './app-property.mapper';

export class ApplicationPropertyService extends BaseEntityFirestoreService<ApplicationProperty> {
  constructor(protected override entityMapper: ApplicationPropertyMapper) {
    super(entityMapper, 'application-properties');
  }
}
