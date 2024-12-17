import { TestBed } from '@angular/core/testing';
import { BreakpointObserver, Breakpoints, BreakpointState } from '@angular/cdk/layout';
import { BehaviorSubject, Observable, skip } from 'rxjs';
import { LayoutService, SidenavStatus } from './layout.service';

class MockBreakpointObserver {
  private state: BehaviorSubject<BreakpointState> = new BehaviorSubject({ matches: true, breakpoints: { [Breakpoints.Web]: true } } as BreakpointState);

  resize(size: string) {
    if (size === 'handset') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Handset]: true } } as BreakpointState);
    } else if (size === 'tablet') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Tablet]: true } } as BreakpointState);
    } else if (size === 'web') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Web]: true } } as BreakpointState);
    } else this.state.next({ matches: Number.parseInt(size) >= 700, breakpoints: {} });
  }

  observe(): Observable<BreakpointState> {
    return this.state.asObservable().pipe(skip(1));
  }
}

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

    breakpointObserver.resize('700');
    expect(service.sidenavMode()).toBe(SidenavStatus.EXPAND);
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
    expect(service.isSmallDevice()).toBeTruthy();

    breakpointObserver.resize('tablet');
    expect(service.isSmallDevice()).toBeFalsy();

    breakpointObserver.resize('handset');
    expect(service.isSmallDevice()).toBeTruthy();
  });
});
