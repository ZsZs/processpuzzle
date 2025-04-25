import { ApplicationProperty } from './app-property';
import { BaseEntityMapper } from '@processpuzzle/base-entity';

export class ApplicationPropertyMapper implements BaseEntityMapper<ApplicationProperty> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fromDto(dto: any): ApplicationProperty {
    return new ApplicationProperty(dto.name, dto.value);
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  toDto(entity: ApplicationProperty): any {
    return { name: entity.name, value: entity.value };
  }
}
