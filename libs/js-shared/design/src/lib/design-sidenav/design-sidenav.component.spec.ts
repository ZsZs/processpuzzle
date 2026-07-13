import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { LayoutService } from '@processpuzzle/util';
import { MockBreakpointObserver, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { DesignSidenavComponent } from './design-sidenav.component';

describe('DesignSidenavComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: DesignSidenavComponent;
  let fixture: ComponentFixture<DesignSidenavComponent>;
  let breakpointObserver: MockBreakpointObserver;
  let layoutService: LayoutService;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(DesignSidenavComponent, testConfig, {
      providers: [
        provideRouter([]),
        { provide: BreakpointObserver, useClass: MockBreakpointObserver },
        { provide: LayoutService, useClass: LayoutService, deps: [BreakpointObserver] },
      ],
    });
    component = result.component;
    fixture = result.fixture;
    layoutService = TestBed.inject(LayoutService);
    breakpointObserver = TestBed.inject(BreakpointObserver) as unknown as MockBreakpointObserver;
    breakpointObserver.resize(800);
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
    expect(layoutService).toBeTruthy();
    expect(breakpointObserver).toBeTruthy();
  });

  it('template structure contains: mat-nav-list:', () => {
    expect(layoutService.isSmallDevice()).toBeFalsy();
    const matNavList = fixture.debugElement.query(By.css('mat-nav-list')).nativeElement;
    expect(matNavList).toBeTruthy();
  });

  it('empty, if its small device', () => {
    breakpointObserver.resize(599);
    fixture.detectChanges();
    const matNavList = fixture.debugElement.query(By.css('mat-nav-list'));
    expect(layoutService.isSmallDevice()).toBeTruthy();
    expect(matNavList).toBeNull();
  });
});
