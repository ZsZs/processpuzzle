import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { computed, Injectable, signal } from '@angular/core';

export enum SidenavStatus {
  EXPAND,
  CLOSE,
}

@Injectable()
export class LayoutService {
  isSmallDevice = computed<boolean>(() => this.layoutClass() === 'handset-layout' || this.layoutClass() === '');
  layoutClass = signal<string>('');
  sidenavMode = signal<SidenavStatus>(SidenavStatus.EXPAND);

  constructor(private breakpointObserver: BreakpointObserver) {
    this.observeBreakpoint();
  }

  // region protected, private helper methods
  private observeBreakpoint() {
    this.breakpointObserver.observe([Breakpoints.Handset, Breakpoints.Tablet, Breakpoints.Web]).subscribe((result) => {
      if (result.matches) {
        for (const query of Object.keys(result.breakpoints)) {
          if (Breakpoints.Handset.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set('handset-layout');
          else if (Breakpoints.Tablet.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set('tablet-layout');
          else if (Breakpoints.Web.indexOf(query) > -1 && result.breakpoints[query]) this.layoutClass.set('web-layout');
        }
        this.sidenavMode.set(SidenavStatus.EXPAND);
      } else this.sidenavMode.set(SidenavStatus.CLOSE);
    });
  }

  // endregion
}
