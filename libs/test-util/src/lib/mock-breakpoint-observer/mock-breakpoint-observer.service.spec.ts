import { Breakpoints } from '@angular/cdk/layout';
import { DeviceTypes, MockBreakpointObserver } from './mock-breakpoint-observer.service';

describe('MockBreakpointObserver', () => {
  let service: MockBreakpointObserver;

  beforeEach(() => {
    service = new MockBreakpointObserver();
  });

  describe('initial state', () => {
    it('should have initial state with Web breakpoint', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.Web]).toBe(true);
        expect(state.breakpoints[Breakpoints.WebLandscape]).toBe(true);
        expect(state.breakpoints[Breakpoints.XLarge]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(1920);
    }, 10000);
  });

  describe('resize method', () => {
    it('should set XSmall breakpoint for width < 600', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.XSmall]).toBe(true);
        expect(state.breakpoints[Breakpoints.HandsetPortrait]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(599);
    }, 10000);

    it('should set Small breakpoint for width between 600 and 840', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.Small]).toBe(true);
        expect(state.breakpoints[Breakpoints.HandsetLandscape]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(700);
    }, 10000);

    it('should set Small breakpoint for width between 840 and 960', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.Small]).toBe(true);
        expect(state.breakpoints[Breakpoints.Web]).toBe(true);
        expect(state.breakpoints[Breakpoints.WebPortrait]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(900);
    }, 10000);

    it('should set Medium breakpoint for width between 960 and 1280', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.Medium]).toBe(true);
        expect(state.breakpoints[Breakpoints.Web]).toBe(true);
        expect(state.breakpoints[Breakpoints.WebPortrait]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(1000);
    }, 10000);

    it('should set Large breakpoint for width between 1280 and 1920', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.Large]).toBe(true);
        expect(state.breakpoints[Breakpoints.Web]).toBe(true);
        expect(state.breakpoints[Breakpoints.WebLandscape]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(1500);
    }, 10000);

    it('should set XLarge breakpoint for width >= 1920', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.matches).toBe(true);
        expect(state.breakpoints[Breakpoints.XLarge]).toBe(true);
        expect(state.breakpoints[Breakpoints.Web]).toBe(true);
        expect(state.breakpoints[Breakpoints.WebLandscape]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(1920);
    }, 10000);
  });

  describe('resize method with device types', () => {
    it('should set Handset breakpoints for XSmall with HANDSET device type', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.breakpoints[Breakpoints.XSmall]).toBe(true);
        expect(state.breakpoints[Breakpoints.Handset]).toBe(true);
        expect(state.breakpoints[Breakpoints.HandsetPortrait]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(599, DeviceTypes.HANDSET);
    }, 10000);

    it('should set Handset breakpoints for Small with HANDSET device type', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.breakpoints[Breakpoints.Small]).toBe(true);
        expect(state.breakpoints[Breakpoints.Handset]).toBe(true);
        expect(state.breakpoints[Breakpoints.HandsetLandscape]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(700, DeviceTypes.HANDSET);
    }, 10000);

    it('should set Tablet breakpoints for Small with TABLET device type', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.breakpoints[Breakpoints.Small]).toBe(true);
        expect(state.breakpoints[Breakpoints.Tablet]).toBe(true);
        expect(state.breakpoints[Breakpoints.TabletPortrait]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(700, DeviceTypes.TABLET);
    }, 10000);

    it('should set Tablet breakpoints for Medium with TABLET device type', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.breakpoints[Breakpoints.Medium]).toBe(true);
        expect(state.breakpoints[Breakpoints.Tablet]).toBe(true);
        expect(state.breakpoints[Breakpoints.TabletLandscape]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(1000, DeviceTypes.TABLET);
    }, 10000);

    it('should set Web breakpoints for Medium with WEB device type', (done) => {
      const subscription = service.observe().subscribe(state => {
        expect(state.breakpoints[Breakpoints.Medium]).toBe(true);
        expect(state.breakpoints[Breakpoints.Web]).toBe(true);
        expect(state.breakpoints[Breakpoints.WebPortrait]).toBe(true);

        subscription.unsubscribe();
        done();
      });

      // Call resize to trigger an emission
      service.resize(1000, DeviceTypes.WEB);
    }, 10000);
  });

  describe('observe method', () => {
    it('should emit when resize is called', (done) => {
      const emittedValues: any[] = [];
      const subscription = service.observe().subscribe(state => {
        emittedValues.push(state);

        // Check if we've received all 3 emissions
        if (emittedValues.length === 3) {
          expect(emittedValues[0].breakpoints[Breakpoints.XSmall]).toBe(true);
          expect(emittedValues[1].breakpoints[Breakpoints.Medium]).toBe(true);
          expect(emittedValues[2].breakpoints[Breakpoints.Large]).toBe(true);

          subscription.unsubscribe();
          done();
        }
      });

      service.resize(599);
      service.resize(1000);
      service.resize(1500);
    }, 10000);

    it('should skip the initial value', (done) => {
      const emittedValues: any[] = [];
      const subscription = service.observe().subscribe(state => {
        emittedValues.push(state);
      });

      // Wait a bit to ensure no emissions occur
      setTimeout(() => {
        // No values should be emitted without calling resize
        expect(emittedValues.length).toBe(0);

        subscription.unsubscribe();
        done();
      }, 100);
    }, 10000);
  });
});
