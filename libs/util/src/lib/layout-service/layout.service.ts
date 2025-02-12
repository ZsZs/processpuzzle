import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { computed, inject, Injectable, signal } from '@angular/core';

export enum SidenavStatus {
  EXPAND,
  CLOSE,
  SHRINK,
}

@Injectable({ providedIn: 'root' })
export class LayoutService {
  private readonly HANDSET_LAYOUT = 'handset-layout';
  private readonly TABLET_LAYOUT = 'tablet-layout';
  private readonly WEB_LAYOUT = 'web-layout';
  isSmallDevice = computed<boolean>(() => this.layoutClass() === this.HANDSET_LAYOUT);
  isLargeDevice = computed<boolean>(() => this.layoutClass() === this.WEB_LAYOUT || this.layoutClass() === '');
  isMediumDevice = computed<boolean>(() => this.layoutClass() === this.TABLET_LAYOUT);
  layoutClass = signal<string>(this.WEB_LAYOUT);
  sidenavMode = computed<SidenavStatus>(() => {
    let sideNavMode = SidenavStatus.EXPAND;
    if (this.isSmallDevice()) sideNavMode = SidenavStatus.CLOSE;
    else if (this.isMediumDevice()) sideNavMode = SidenavStatus.SHRINK;
    else if (this.isLargeDevice()) sideNavMode = SidenavStatus.EXPAND;
    return sideNavMode;
  });
  private readonly breakpointObserver = inject(BreakpointObserver);

  constructor() {
    this.observeBreakpoints();
  }

  // region protected, private helper methods
  private observeBreakpoints() {
    this.breakpointObserver.observe([Breakpoints.XSmall, Breakpoints.Small, Breakpoints.Medium, Breakpoints.Large, Breakpoints.XLarge]).subscribe((result) => {
      if (result.matches) {
        for (const query of Object.keys(result.breakpoints)) {
          if (Breakpoints.XSmall.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.HANDSET_LAYOUT);
          else if (Breakpoints.Small.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.TABLET_LAYOUT);
          else if (Breakpoints.Medium.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
          else if (Breakpoints.Large.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
          else if (Breakpoints.XLarge.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
        }
      }
    });
  }

  // endregion
}
