import { describe, expect, it } from 'vitest';
import { serviceRootOf } from './service-root';

describe('serviceRootOf', () => {
  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
  const configuration = {
    BASE_CONFIGURATION: {
      // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
      BACKEND_SERVICE_ROOT: 'http://localhost:8080/organizations/acme',
      // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP URLs is required
      ENTITY_SERVICE_ROOT: 'http://entities.acme/organizations/acme',
    },
  };

  it('returns the feature root when the configuration names one.', () => {
    // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
    expect(serviceRootOf(configuration, 'ENTITY_SERVICE_ROOT')).toBe('http://entities.acme/organizations/acme');
  });

  it('falls back to BACKEND_SERVICE_ROOT for a feature root the configuration leaves out.', () => {
    // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
    expect(serviceRootOf(configuration, 'WIDGET_SERVICE_ROOT')).toBe('http://localhost:8080/organizations/acme');
  });

  it('returns an empty string when there is no configuration, so the caller can skip the request.', () => {
    expect(serviceRootOf(undefined, 'ENTITY_SERVICE_ROOT')).toBe('');
    expect(serviceRootOf(null, 'ENTITY_SERVICE_ROOT')).toBe('');
  });

  it('returns an empty string when the configuration carries no BASE_CONFIGURATION.', () => {
    expect(serviceRootOf({ LOGGING_CONFIGURATION: {} }, 'ENTITY_SERVICE_ROOT')).toBe('');
  });

  it('returns an empty string when neither the feature root nor BACKEND_SERVICE_ROOT is set.', () => {
    expect(serviceRootOf({ BASE_CONFIGURATION: { THIRD_PARTY_ROOT: 'http://localhost:3000' } }, 'ENTITY_SERVICE_ROOT')).toBe('');
  });
});
