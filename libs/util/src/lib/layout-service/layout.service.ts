import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { computed, Injectable, signal } from '@angular/core';

export enum SidenavStatus {
  EXPAND,
  CLOSE,
  SHRINK,
}

@Injectable()
export class LayoutService {
  private readonly HANDSET_LAYOUT = 'handset-layout';
  private readonly TABLET_LAYOUT = 'medium-layout';
  private readonly WEB_LAYOUT = 'web-layout';
  isSmallDevice = computed<boolean>(() => this.layoutClass() === this.HANDSET_LAYOUT);
  isLargeDevice = computed<boolean>(() => this.layoutClass() === this.WEB_LAYOUT || this.layoutClass() === '');
  isMediumDevice = computed<boolean>(() => this.layoutClass() === this.TABLET_LAYOUT);
  layoutClass = signal<string>('');
  sidenavMode = computed<SidenavStatus>(() => {
    let sideNavMode = SidenavStatus.EXPAND;
    if (this.isSmallDevice()) sideNavMode = SidenavStatus.CLOSE;
    else if (this.isMediumDevice()) sideNavMode = SidenavStatus.SHRINK;
    else if (this.isLargeDevice()) sideNavMode = SidenavStatus.EXPAND;
    return sideNavMode;
  });

  constructor(private breakpointObserver: BreakpointObserver) {
    this.observeBreakpoint();
  }

  // region protected, private helper methods
  private observeBreakpoint() {
    this.breakpointObserver.observe([Breakpoints.Handset, Breakpoints.Tablet, Breakpoints.Web]).subscribe((result) => {
      if (result.matches) {
        for (const query of Object.keys(result.breakpoints)) {
          if (Breakpoints.HandsetPortrait.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.HANDSET_LAYOUT);
          else if (Breakpoints.XSmall.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.HANDSET_LAYOUT);
          else if (Breakpoints.Small.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.TABLET_LAYOUT);
          else if (Breakpoints.HandsetLandscape.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.TABLET_LAYOUT);
          else if (Breakpoints.WebLandscape.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
          else if (Breakpoints.WebPortrait.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
          else if (Breakpoints.Medium.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
          else if (Breakpoints.Large.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
          else if (Breakpoints.XLarge.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set(this.WEB_LAYOUT);
        }
      }
    });
  }

  // endregion
}
