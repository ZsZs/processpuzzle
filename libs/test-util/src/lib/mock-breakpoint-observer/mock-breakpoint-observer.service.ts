import { BehaviorSubject, Observable, skip } from 'rxjs';
import { Breakpoints, BreakpointState } from '@angular/cdk/layout';

export enum DeviceTypes {
  HANDSET,
  TABLET,
  WEB,
}

export class MockBreakpointObserver {
  private state: BehaviorSubject<BreakpointState> = new BehaviorSubject({ matches: true, breakpoints: { [Breakpoints.Web]: true } } as BreakpointState);

  resize(width: number, deviceType?: DeviceTypes) {
    let breakpoints = {};
    if (width < 600) {
      breakpoints = { [Breakpoints.XSmall]: true };
      if (deviceType === DeviceTypes.HANDSET) {
        breakpoints = { ...breakpoints, [Breakpoints.Handset]: true, [Breakpoints.HandsetPortrait]: true };
      }
      if (deviceType == undefined) {
        breakpoints = { ...breakpoints, [Breakpoints.HandsetPortrait]: true };
      }
    } else if (width >= 600 && width < 840) {
      breakpoints = { [Breakpoints.Small]: true };
      if (deviceType === DeviceTypes.HANDSET) {
        breakpoints = { ...breakpoints, [Breakpoints.Handset]: true, [Breakpoints.HandsetLandscape]: true };
      } else if (deviceType === DeviceTypes.TABLET) {
        breakpoints = { ...breakpoints, [Breakpoints.Tablet]: true, [Breakpoints.TabletPortrait]: true };
      } else if (deviceType == undefined) {
        breakpoints = { ...breakpoints, [Breakpoints.HandsetLandscape]: true };
      }
    } else if (width >= 840 && width < 960) {
      breakpoints = { [Breakpoints.Small]: true };
      if (deviceType === DeviceTypes.HANDSET) {
        breakpoints = { ...breakpoints, [Breakpoints.Handset]: true, [Breakpoints.HandsetLandscape]: true };
      } else if (deviceType === DeviceTypes.TABLET) {
        breakpoints = { ...breakpoints, [Breakpoints.Tablet]: true, [Breakpoints.TabletLandscape]: true };
      } else if (deviceType === DeviceTypes.WEB || deviceType === undefined) {
        breakpoints = { ...breakpoints, [Breakpoints.Web]: true, [Breakpoints.WebPortrait]: true };
      }
    } else if (width >= 960 && width < 1280) {
      breakpoints = { [Breakpoints.Medium]: true };
      if (deviceType === DeviceTypes.TABLET) {
        breakpoints = { ...breakpoints, [Breakpoints.Tablet]: true, [Breakpoints.TabletLandscape]: true };
      } else if (deviceType === DeviceTypes.WEB || deviceType === undefined) {
        breakpoints = { ...breakpoints, [Breakpoints.Web]: true, [Breakpoints.WebPortrait]: true };
      }
    } else if (width >= 1280 && width < 1920) {
      breakpoints = { [Breakpoints.Large]: true };
      if (deviceType === DeviceTypes.WEB || deviceType === undefined) {
        breakpoints = { ...breakpoints, [Breakpoints.Web]: true, [Breakpoints.WebLandscape]: true };
      }
    } else {
      breakpoints = { [Breakpoints.XLarge]: true, [Breakpoints.Web]: true, [Breakpoints.WebLandscape]: true };
    }
    this.state.next({ matches: true, breakpoints } as BreakpointState);
  }

  observe(): Observable<BreakpointState> {
    return this.state.asObservable().pipe(skip(1));
  }
}
