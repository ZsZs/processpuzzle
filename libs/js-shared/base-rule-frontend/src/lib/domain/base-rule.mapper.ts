import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { BaseRule, Severity } from './base-rule';

type BaseRuleDto = Partial<BaseRule>;

@Injectable({ providedIn: 'root' })
export class BaseRuleMapper implements BaseEntityMapper<BaseRule> {
  fromDto(dto: unknown): BaseRule {
    const source = dto as BaseRuleDto;
    return new BaseRule(
      source.id,
      source.name,
      source.description,
      source.context,
      source.expression,
      source.severity as Severity | undefined,
      source.message,
      source.translocoId,
      source.extendsRuleId,
      source.override,
      source.enabled,
      source.version,
      source.createdAt,
      source.updatedAt,
      source.fields,
    );
  }

  toDto(entity: BaseRule): unknown {
    return { ...entity };
  }
}
