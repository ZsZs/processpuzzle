import { getEnvironment } from './get-environment';

describe('getEnvironment', () => {
  test('http://processpuzzle-testbed.eu-central-1.elasticbeanstalk.com/ should be aws', () => {
    expect(getEnvironment('http://processpuzzle-testbed.eu-central-1.elasticbeanstalk.com/')).toBe('aws');
  });

  test('http://localhost should be LOCAL', () => {
    expect(getEnvironment('http://localhost:4200')).toBe('local');
  });

  test('http://localhost:8080/ should be docker', () => {
    expect(getEnvironment('http://localhost:8080')).toBe('docker');
  });

  test('https://unknown should should throw error', () => {
    expect(() => getEnvironment('https://unknown.bmf.a2.cp.cna.at')).toThrow('Undefined base URL: https://unknown.bmf.a2.cp.cna.at');
  });
});
