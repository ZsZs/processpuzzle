import { Injectable } from '@angular/core';
import type { BaseEntityMapper } from '../base-entity.mapper';
import type { ExchangeRateRecord } from '../base-form/fundamental-types/exchange-rate.service';
import { ExchangeRateEntry } from './exchange-rate-entry';

/**
 * Translates between the contract's `ExchangeRate` and the entry the generated screens work with. The fields are listed one by
 * one rather than spread, and the decimal and the date travel as text, trimmed — what the backend reads them from.
 */
@Injectable({ providedIn: 'root' })
export class ExchangeRateEntryMapper implements BaseEntityMapper<ExchangeRateEntry> {
  fromDto(dto: unknown): ExchangeRateEntry {
    const source = dto as Partial<ExchangeRateRecord>;
    return new ExchangeRateEntry({
      id: source.id,
      from: source.from,
      to: source.to,
      rate: source.rate,
      validFrom: source.validFrom,
      version: source.version,
    });
  }

  toDto(entity: ExchangeRateEntry): Partial<ExchangeRateRecord> {
    return {
      from: entity.from,
      to: entity.to,
      rate: entity.rate?.trim(),
      validFrom: entity.validFrom?.trim(),
      version: entity.version,
    };
  }
}
