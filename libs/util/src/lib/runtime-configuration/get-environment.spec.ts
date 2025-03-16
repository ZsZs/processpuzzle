import { getEnvironment } from './get-environment';

describe('getEnvironment', () => {
  test('http://processpuzzle-testbed.eu-central-1.elasticbeanstalk.com/ should be aws', () => {
    jest.spyOn(window.location, 'origin', 'get').mockReturnValue('http://processpuzzle-testbed.eu-central-1.elasticbeanstalk.com/');
    expect(getEnvironment()).toBe('aws');
  });

  test('http://localhost should be LOCAL', () => {
    jest.spyOn(window.location, 'origin', 'get').mockReturnValue('http://localhost:4200');
    expect(getEnvironment()).toBe('local');
  });

  test('http://localhost:8080/ should be docker', () => {
    jest.spyOn(window.location, 'origin', 'get').mockReturnValue('http://localhost:8080');
    expect(getEnvironment()).toBe('docker');
  });

  test('https://unknown should should throw error', () => {
    jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://unknown.bmf.a2.cp.cna.at');
    expect(() => getEnvironment()).toThrow('Undefined base URL: https://unknown.bmf.a2.cp.cna.at');
  });
});
