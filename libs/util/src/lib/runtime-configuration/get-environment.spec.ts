import { getEnvironment } from './get-environment';

describe('getEnvironment', () => {
  const oldWindowLocation = window.location;

  beforeAll(() => {
    //        delete window.location

    const dummyStringList: DOMStringList = {
      length: 0,
      contains(): boolean {
        return false;
      },
      item(): string | null {
        return null;
      },
    };

    const dummyLocation = {
      ancestorOrigins: dummyStringList,
      hash: '',
      host: 'dummy.com',
      port: '80',
      protocol: 'http:',
      hostname: 'localhost',
      href: 'http://dummy.com?page=1&name=testing',
      origin: 'http://localhost',
      pathname: '',
      search: '',
      assign: jest.fn(),
      reload: jest.fn(),
      replace: jest.fn(),
    };
    window.location = Object.defineProperties<Location>(dummyLocation, {
      ...Object.getOwnPropertyDescriptors(oldWindowLocation),
      assign: {
        configurable: true,
        value: jest.fn(),
      },
      origin: {
        configurable: true,
        value: jest.fn(),
      },
    });
  });
  afterAll(() => {
    window.location = oldWindowLocation;
  });

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
