import { describe, expect, it } from 'vitest';
import { getEnumValueByKey } from './base-entity.mapper';

enum NumericTestEnum {
  First,
  Second,
}

enum StringTestEnum {
  First = 'first',
  Second = 'second',
}

describe('getEnumValueByKey()', () => {
  it('returns numeric enum values by key', () => {
    expect(getEnumValueByKey<NumericTestEnum>(NumericTestEnum, 'Second')).toBe(NumericTestEnum.Second);
  });

  it('returns string enum values by key', () => {
    expect(getEnumValueByKey<StringTestEnum>(StringTestEnum, 'First')).toBe(StringTestEnum.First);
  });

  it('returns undefined when the key does not exist', () => {
    expect(getEnumValueByKey<NumericTestEnum>(NumericTestEnum, 'Missing')).toBeUndefined();
  });
});
