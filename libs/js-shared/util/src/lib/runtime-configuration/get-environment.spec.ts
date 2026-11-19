import { getEnvironment } from './get-environment';
import { describe, expect, it } from 'vitest';

describe('getEnvironment', () => {
  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost/AWS URLs is required
  it('http://processpuzzle-testbed.eu-central-1.elasticbeanstalk.com/ should be aws', () => {
    // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost/AWS URLs is required
    expect(getEnvironment('http://processpuzzle-testbed.eu-central-1.elasticbeanstalk.com/')).toBe('aws');
  });

  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
  it('http://localhost should be LOCAL', () => {
    // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
    expect(getEnvironment('http://localhost:4200')).toBe('local');
  });

  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
  it('http://localhost:8080/ should be docker', () => {
    // eslint-disable-next-line sonarjs/no-clear-text-protocols -- testing HTTP localhost URLs is required
    expect(getEnvironment('http://localhost:8080')).toBe('docker');
  });

  it('https://unknown should should throw error', () => {
    expect(() => getEnvironment('https://unknown.bmf.a2.cp.cna.at')).toThrow('Undefined base URL: https://unknown.bmf.a2.cp.cna.at');
  });
});
