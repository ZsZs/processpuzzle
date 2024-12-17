import {getEnvironment} from "./get-environment";

describe('getEnvironment', () => {
    const oldWindowLocation = window.location

    beforeAll(() => {
//        delete window.location

        const dummyStringList: DOMStringList = {
            length: 0,
            contains(): boolean {return false;},
            item(): string | null {return null;}
        }

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
        window.location = Object.defineProperties<Location>(
          dummyLocation,
          {
              ...Object.getOwnPropertyDescriptors(oldWindowLocation),
              assign: {
                  configurable: true,
                  value: jest.fn(),
              },
              origin: {
                  configurable: true,
                  value: jest.fn(),
              }
          },
        )
    })
    afterAll(() => {
        window.location = oldWindowLocation
    })

    test('http://localhost should be LOCAL', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('http://localhost:4200');
        expect( getEnvironment()).toBe('local');
    });

    test('https://t0-ef-qk-ng-bmf-ef-test should be T0', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://t0-ef-qk-ng-bmf-ef-test.bmf.a2.cp.cna.at');
        expect( getEnvironment()).toBe('t0');
    });

    test('https://t1-ef-ics-services-bmf-ef-test should be T1', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://t1-ef-ics-services-bmf-ef-test.bmf.a2.cp.cna.at');
        expect( getEnvironment()).toBe('t1');
    });

    test('https://t2-ef-ics-services-bmf-ef-test should be T2', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://t2-ef-ics-services-bmf-ef-test.bmf.a2.cp.cna.at');
        expect( getEnvironment()).toBe('t2');
    });

    test('https://q-ef-ics-gui-bmf-ef-qs should be Q', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://q-ef-ics-gui-bmf-ef-qs.bmf.a2.cp.cna.at');
        expect( getEnvironment()).toBe('q');
    });

    test('https://p-ef-ics-services-bmf-ef-prod should be P', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://p-ef-ics-services-bmf-ef-prod.bmf.a2.cp.cna.at');
        expect( getEnvironment()).toBe('p');
    });

    test('https://unknown should should throw error', () => {
        jest.spyOn(window.location, 'origin', 'get').mockReturnValue('https://unknown.bmf.a2.cp.cna.at');
        expect( () => getEnvironment() ).toThrow('Unexpected base URL');
    });
});
