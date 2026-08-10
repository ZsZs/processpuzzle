import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Artifact, ArtifactInputPort, ArtifactOutputPort } from './base-artifact';

@Injectable({ providedIn: 'root' })
export class BaseArtifactMapper implements BaseEntityMapper<Artifact> {
  fromDto(dto: any): Artifact {
    return new Artifact(
      dto.id,
      dto.orgKey,
      dto.title,
      dto.description,
      (dto.inputPorts ?? []).map((p: any) => new ArtifactInputPort(
        p.name, p.type, p.required, p.description, p.defaultValue,
        p.entityType, p.attributeVisibility, p.defaultRsqlFilter,
      )),
      (dto.outputPorts ?? []).map((p: any) => new ArtifactOutputPort(
        p.name, p.type, p.description, p.entityType, p.attributeVisibility,
      )),
      dto.blocks ?? [],
      dto.version,
      dto.createdAt,
      dto.updatedAt,
    );
  }

  // Used only by the generic BaseEntityStore's create() path (there is no artifact create
  // form in this sketch, but BaseEntityRestService requires the mapper regardless) and, more
  // relevantly, is deliberately NOT what the Properties form submits — see
  // BaseArtifactService.updateProperties, which builds an ArtifactPropertiesInput-shaped body
  // by hand rather than routing through this toDto/PUT-the-whole-entity path.
  toDto(entity: Artifact): any {
    return { ...entity };
  }
}
