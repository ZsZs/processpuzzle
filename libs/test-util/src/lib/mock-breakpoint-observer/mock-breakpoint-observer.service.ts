import { Observable, Subject } from 'rxjs';
import { Breakpoints, BreakpointObserver, BreakpointState } from '@angular/cdk/layout';

export enum DeviceTypes {
  HANDSET,
  TABLET,
  WEB,
}

export class MockBreakpointObserver implements Partial<BreakpointObserver> {
  private readonly state$ = new Subject<BreakpointState>();

  /** Simulate a viewport width and device type */
  resize(width: number, deviceType?: DeviceTypes) {
    const breakpoints: Record<string, boolean> = {};

    // Determine size breakpoint
    if (width < 600) {
      // XSmall
      breakpoints[Breakpoints.XSmall] = true;
      breakpoints['(max-width: 599.98px)'] = true;
    } else if (width < 960) {
      // Small
      breakpoints[Breakpoints.Small] = true;
      breakpoints['(min-width: 600px) and (max-width: 959.98px)'] = true;
    } else if (width < 1280) {
      // Medium
      breakpoints[Breakpoints.Medium] = true;
      breakpoints['(min-width: 960px) and (max-width: 1279.98px)'] = true;
    } else if (width < 1920) {
      // Large
      breakpoints[Breakpoints.Large] = true;
      breakpoints['(min-width: 1280px) and (max-width: 1919.98px)'] = true;
    } else {
      // XLarge
      breakpoints[Breakpoints.XLarge] = true;
      breakpoints['(min-width: 1920px)'] = true;
    }

    // Determine device type and orientation breakpoints
    if (width < 600) {
      // XSmall - Handset Portrait
      if (deviceType === DeviceTypes.HANDSET || deviceType === undefined) {
        breakpoints[Breakpoints.Handset] = true;
        breakpoints[Breakpoints.HandsetPortrait] = true;
        breakpoints['(max-width: 599.98px) and (orientation: portrait)'] = true;
      }
    } else if (width < 840) {
      // Small (600-839)
      if (deviceType === DeviceTypes.HANDSET || deviceType === undefined) {
        // Handset Landscape
        breakpoints[Breakpoints.Handset] = true;
        breakpoints[Breakpoints.HandsetLandscape] = true;
        breakpoints['(max-width: 959.98px) and (orientation: landscape)'] = true;
      } else if (deviceType === DeviceTypes.TABLET) {
        // Tablet Portrait
        breakpoints[Breakpoints.Tablet] = true;
        breakpoints[Breakpoints.TabletPortrait] = true;
        breakpoints['(min-width: 600px) and (max-width: 839.98px) and (orientation: portrait)'] = true;
      }
    } else if (width < 960) {
      // Small (840-959) - Web Portrait
      breakpoints[Breakpoints.Web] = true;
      breakpoints[Breakpoints.WebPortrait] = true;
      breakpoints['(min-width: 840px) and (orientation: portrait)'] = true;
    } else if (width < 1280) {
      // Medium
      if (deviceType === DeviceTypes.TABLET) {
        // Tablet Landscape
        breakpoints[Breakpoints.Tablet] = true;
        breakpoints[Breakpoints.TabletLandscape] = true;
        breakpoints['(min-width: 960px) and (max-width: 1279.98px) and (orientation: landscape)'] = true;
      } else {
        // Web Portrait
        breakpoints[Breakpoints.Web] = true;
        breakpoints[Breakpoints.WebPortrait] = true;
        breakpoints['(min-width: 840px) and (orientation: portrait)'] = true;
      }
    } else {
      // Large and XLarge - Web Landscape
      breakpoints[Breakpoints.Web] = true;
      breakpoints[Breakpoints.WebLandscape] = true;
      breakpoints['(min-width: 1280px) and (orientation: landscape)'] = true;
    }

    this.state$.next({ matches: Object.values(breakpoints).some(Boolean), breakpoints });
  }

  /** Mock of BreakpointObserver.observe() */
  observe(): Observable<BreakpointState> {
    return this.state$.asObservable();
  }
}
