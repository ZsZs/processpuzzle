// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { inject, Injectable } from '@angular/core';
import { EmbeddedComponent } from './embedded-component';
import { EmbeddedDetailMapper } from '../embedded-detail/embedded-detail.mapper';

/**
 * Mapped by the parent's mapper rather than by a service of its own: an embedded component arrives as part of
 * the `Test Entity` payload.
 */
@Injectable({ providedIn: 'root' })
export class EmbeddedComponentMapper implements BaseEntityMapper<EmbeddedComponent> {
  private readonly embeddedDetailMapper = inject(EmbeddedDetailMapper);

  fromDto(dto: any): EmbeddedComponent {
    // `embeddedDetails` has to be carried through: a mapper that rebuilds a row from a fixed list of fields
    // drops whatever it does not name, and here that would silently empty the next level of containment.
    return new EmbeddedComponent(
      dto.id,
      dto.name,
      dto.description,
      dto.embeddedDetails?.map((embeddedDetail: any) => this.embeddedDetailMapper.fromDto(embeddedDetail)),
    );
  }

  toDto(entity: EmbeddedComponent): any {
    return entity;
  }
}
