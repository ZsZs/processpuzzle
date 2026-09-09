import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { ApplicationProperty } from './app-property';
import { ApplicationPropertyMapper } from './app-property.mapper';

export class ApplicationPropertyService extends BaseEntityRestService<ApplicationProperty> {
  constructor(protected override entityMapper: ApplicationPropertyMapper) {
    super(entityMapper, 'BACKEND_SERVICE_ROOT', 'application-properties');
  }
}
