import { setupZoneTestEnv } from 'jest-preset-angular/setup-env/zone';
import { BehaviorSubject, Observable, skip } from 'rxjs';
import { Breakpoints, BreakpointState } from '@angular/cdk/layout';

setupZoneTestEnv();

export class MockBreakpointObserver {
  private state: BehaviorSubject<BreakpointState> = new BehaviorSubject({ matches: true, breakpoints: { [Breakpoints.Web]: true } } as BreakpointState);

  resize(size: string) {
    if (size === 'handset') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Handset]: true } } as BreakpointState);
    } else if (size === 'small') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Small]: true } } as BreakpointState);
    } else if (size === 'tablet') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Tablet]: true } } as BreakpointState);
    } else if (size === 'medium') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Medium]: true } } as BreakpointState);
    } else if (size === 'web') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Web]: true } } as BreakpointState);
    } else if (size === 'large') {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Large]: true } } as BreakpointState);
    } else if (Number.parseInt(size) <= 400) {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Handset]: true } });
    } else if (Number.parseInt(size) < 700) {
      this.state.next({ matches: true, breakpoints: { [Breakpoints.Medium]: true } });
    } else this.state.next({ matches: Number.parseInt(size) >= 700, breakpoints: { [Breakpoints.Large]: true } });
  }

  observe(): Observable<BreakpointState> {
    return this.state.asObservable().pipe(skip(1));
  }
}
