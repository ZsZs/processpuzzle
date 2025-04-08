import { ApplicationProperty } from './app-property';
import { BaseEntityMapper } from '@processpuzzle/base-entity';

export class ApplicationPropertyMapper implements BaseEntityMapper<ApplicationProperty> {
  fromDto(dto: any): ApplicationProperty {
    return new ApplicationProperty(dto.name, dto.value);
  }

  toDto(entity: ApplicationProperty) {
    return { name: entity.name, value: entity.value };
  }
}
