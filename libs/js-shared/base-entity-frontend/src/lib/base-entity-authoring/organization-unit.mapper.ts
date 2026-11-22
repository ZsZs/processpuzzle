import { Injectable } from '@angular/core';
import type { BaseEntityMapper } from '../base-entity.mapper';
import type { OrganizationUnit } from '../base-form/fundamental-types/organization-unit.service';
import { OrganizationUnitRecord, type OrganizationUnitStatus } from './organization-unit';

/**
 * Translates between the contract's `OrganizationUnit` and the record the generated screens work with.
 *
 * The unit is addressed by its `code` (`PUT /units/{code}`), so the record's `id` mirrors it. `toDto` lists the fields one by
 * one rather than spreading, and sends a blank divisor as absent — the backend then takes one. The decimals travel as text.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationUnitMapper implements BaseEntityMapper<OrganizationUnitRecord> {
  fromDto(dto: unknown): OrganizationUnitRecord {
    const source = dto as Partial<OrganizationUnit>;
    const code = source.code ?? '';
    return new OrganizationUnitRecord({
      id: code,
      code,
      symbol: source.symbol,
      name: source.name,
      kind: source.kind,
      factor: source.factor,
      divisor: source.divisor,
      status: source.status as OrganizationUnitStatus | undefined,
      version: source.version,
    });
  }

  toDto(entity: OrganizationUnitRecord): Partial<OrganizationUnit> {
    const divisor = entity.divisor?.trim();
    return {
      code: entity.code,
      symbol: entity.symbol,
      name: entity.name,
      kind: entity.kind,
      factor: entity.factor?.trim(),
      divisor: divisor ? divisor : undefined,
      status: entity.status,
      version: entity.version,
    };
  }
}
