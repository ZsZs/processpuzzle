import { TestBed } from '@angular/core/testing';
import { BreakpointObserver } from '@angular/cdk/layout';
import { LayoutService, SidenavStatus } from './layout.service';
import { DeviceTypes, MockBreakpointObserver } from '@processpuzzle/test-util';

describe('LayoutService', () => {
  let service: LayoutService;
  let breakpointObserver: MockBreakpointObserver;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LayoutService, { provide: BreakpointObserver, useClass: MockBreakpointObserver }],
    });
    service = TestBed.inject(LayoutService);
    breakpointObserver = TestBed.inject(BreakpointObserver) as unknown as MockBreakpointObserver;
  });

  it('If screen width is under size < 600px then is handset-layout', () => {
    breakpointObserver.resize(599, DeviceTypes.HANDSET);

    expect(service.sidenavMode()).toBe(SidenavStatus.CLOSE);
    expect(service.layoutClass()).toBe('handset-layout');
    expect(service.isSmallDevice()).toBeTruthy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeFalsy();
  });

  it('If screen width is under 600 <= size < 840 pixel then is tablet-layout', () => {
    breakpointObserver.resize(839, DeviceTypes.HANDSET);

    expect(service.sidenavMode()).toBe(SidenavStatus.SHRINK);
    expect(service.layoutClass()).toBe('tablet-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeTruthy();
    expect(service.isLargeDevice()).toBeFalsy();

    breakpointObserver.resize(839, DeviceTypes.TABLET);

    expect(service.sidenavMode()).toBe(SidenavStatus.SHRINK);
    expect(service.layoutClass()).toBe('tablet-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeTruthy();
    expect(service.isLargeDevice()).toBeFalsy();
  });

  it('If screen width is under 840 <= size < 960 pixel then is tablet-layout', () => {
    breakpointObserver.resize(959, DeviceTypes.HANDSET);

    expect(service.sidenavMode()).toBe(SidenavStatus.SHRINK);
    expect(service.layoutClass()).toBe('tablet-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeTruthy();
    expect(service.isLargeDevice()).toBeFalsy();

    breakpointObserver.resize(959, DeviceTypes.TABLET);

    expect(service.sidenavMode()).toBe(SidenavStatus.SHRINK);
    expect(service.layoutClass()).toBe('tablet-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeTruthy();
    expect(service.isLargeDevice()).toBeFalsy();

    breakpointObserver.resize(959, DeviceTypes.WEB);

    expect(service.sidenavMode()).toBe(SidenavStatus.SHRINK);
    expect(service.layoutClass()).toBe('tablet-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeTruthy();
    expect(service.isLargeDevice()).toBeFalsy();
  });

  it('If screen width is under 960 <= size < 1280 pixel then is web-layout', () => {
    breakpointObserver.resize(1279, DeviceTypes.TABLET);

    expect(service.sidenavMode()).toBe(SidenavStatus.EXPAND);
    expect(service.layoutClass()).toBe('web-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeTruthy();

    breakpointObserver.resize(1279, DeviceTypes.WEB);

    expect(service.sidenavMode()).toBe(SidenavStatus.EXPAND);
    expect(service.layoutClass()).toBe('web-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeTruthy();
  });

  it('If screen width is under 1280 <= size < 1920 pixel then is web-layout', () => {
    breakpointObserver.resize(1279, DeviceTypes.WEB);

    expect(service.sidenavMode()).toBe(SidenavStatus.EXPAND);
    expect(service.layoutClass()).toBe('web-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeTruthy();
  });

  it('If screen width is under 1920 <= size then is web-layout', () => {
    breakpointObserver.resize(1920, DeviceTypes.WEB);

    expect(service.sidenavMode()).toBe(SidenavStatus.EXPAND);
    expect(service.layoutClass()).toBe('web-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeTruthy();
  });
});
