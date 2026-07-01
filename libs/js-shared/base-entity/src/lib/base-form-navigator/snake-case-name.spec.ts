import { describe, expect, it } from 'vitest';
import { snakeCaseName } from './base-form-navigator.store';

describe('snakeCaseName', () => {
  it('should convert a string to snake case', () => {
    expect(snakeCaseName('Assembly Type')).toBe('assembly-type');
    expect(snakeCaseName('Configuration Item Type')).toBe('configuration-item-type');
    expect(snakeCaseName('IT Variant')).toBe('it-variant');
    expect(snakeCaseName('API')).toBe('api');
    expect(snakeCaseName('RESTEndpoint')).toBe('rest-endpoint');
    expect(snakeCaseName('IOStream')).toBe('io-stream');
    expect(snakeCaseName('Device')).toBe('device');
  });
});
