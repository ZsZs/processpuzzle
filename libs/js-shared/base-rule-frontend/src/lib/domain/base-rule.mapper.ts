import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { BaseRule, Severity } from './base-rule';

@Injectable({ providedIn: 'root' })
export class BaseRuleMapper implements BaseEntityMapper<BaseRule> {
  fromDto(dto: any): BaseRule {
    return new BaseRule(
      dto.id,
      dto.name,
      dto.description,
      dto.context,
      dto.expression,
      dto.severity as Severity,
      dto.message,
      dto.translocoId,
      dto.extendsRuleId,
      dto.override,
      dto.enabled,
      dto.version,
      dto.createdAt,
      dto.updatedAt,
      dto.fields,
    );
  }

  toDto(entity: BaseRule): any {
    return { ...entity };
  }
}
