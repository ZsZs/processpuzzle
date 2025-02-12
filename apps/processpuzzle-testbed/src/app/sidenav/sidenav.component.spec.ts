import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { SidenavComponent } from './sidenav.component';
import { LayoutService } from '@processpuzzle/util';
import { MockBreakpointObserver } from '@processpuzzle/test-util';

describe('SidenavComponent', () => {
  let component: SidenavComponent;
  let fixture: ComponentFixture<SidenavComponent>;
  let breakpointObserver: MockBreakpointObserver;
  let layoutService: LayoutService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SidenavComponent],
      providers: [provideRouter([]), { provide: LayoutService, useClass: LayoutService, deps: [BreakpointObserver] }, { provide: BreakpointObserver, useClass: MockBreakpointObserver }],
    }).compileComponents();

    fixture = TestBed.createComponent(SidenavComponent);
    component = fixture.componentInstance;
    layoutService = TestBed.inject(LayoutService);
    breakpointObserver = TestBed.inject(BreakpointObserver) as unknown as MockBreakpointObserver;
    breakpointObserver.resize(800);
    fixture.detectChanges();
  });

  describe('sanity test', () => {
    it('Should create component', () => {
      expect(component).toBeTruthy();
      expect(layoutService).toBeTruthy();
      expect(breakpointObserver).toBeTruthy();
    });
  });

  describe('template structure contains:', () => {
    it('mat-action-list:', () => {
      expect(layoutService.isSmallDevice()).toBeFalsy();
      const matActionList = fixture.debugElement.query(By.css('mat-nav-list')).nativeElement;
      expect(matActionList).toBeTruthy();
    });

    it('empty, if its small device:', () => {
      breakpointObserver.resize(599);
      fixture.detectChanges();
      const matActionList = fixture.debugElement.query(By.css('mat-nav-list'));
      expect(layoutService.isSmallDevice()).toBeTruthy();
      expect(matActionList).toBeNull();
    });
  });
});
