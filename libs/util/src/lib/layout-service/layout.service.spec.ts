import { TestBed } from '@angular/core/testing';
import { BreakpointObserver } from '@angular/cdk/layout';
import { LayoutService, SidenavStatus } from './layout.service';
import { MockBreakpointObserver } from '../../test-setup';

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

  it('should push sidenavMode side when window width >= 700', () => {
    breakpointObserver.resize('400');
    expect(service.sidenavMode()).toBe(SidenavStatus.CLOSE);
    expect(service.layoutClass()).toBe('handset-layout');
    expect(service.isSmallDevice()).toBeTruthy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeFalsy();

    breakpointObserver.resize('500');
    expect(service.sidenavMode()).toBe(SidenavStatus.SHRINK);
    expect(service.layoutClass()).toBe('tablet-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeTruthy();
    expect(service.isLargeDevice()).toBeFalsy();

    breakpointObserver.resize('700');
    expect(service.sidenavMode()).toBe(SidenavStatus.EXPAND);
    expect(service.layoutClass()).toBe('web-layout');
    expect(service.isSmallDevice()).toBeFalsy();
    expect(service.isMediumDevice()).toBeFalsy();
    expect(service.isLargeDevice()).toBeTruthy();
  });

  it('should set layoutClass differently when breakpoint is Handset || Table || Web', () => {
    expect(service.layoutClass()).toBe('');

    breakpointObserver.resize('handset');
    expect(service.layoutClass()).toBe('handset-layout');

    breakpointObserver.resize('tablet');
    expect(service.layoutClass()).toBe('tablet-layout');

    breakpointObserver.resize('web');
    expect(service.layoutClass()).toBe('web-layout');
  });

  it('should determine if isSmallDevice when this.layoutClass === "handset-layout" || layoutClass === ""', () => {
    expect(service.isSmallDevice()).toBeFalsy();

    breakpointObserver.resize('tablet');
    expect(service.isSmallDevice()).toBeFalsy();

    breakpointObserver.resize('handset');
    expect(service.isSmallDevice()).toBeTruthy();
  });
});
