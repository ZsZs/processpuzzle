import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { BaseCalendarComponent } from './base-calendar.component';
import { provideRouter } from '@angular/router';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';

describe('BaseCalendarComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: BaseCalendarComponent;
  let fixture: ComponentFixture<BaseCalendarComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(BaseCalendarComponent, testConfig, {
      providers: [provideRouter([])],
    });
    component = result.component;
    fixture = result.fixture;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(fixture).toBeTruthy();
  });

  it('offers the overview and the samples as tabs', () => {
    const links = Array.from(fixture.nativeElement.querySelectorAll('a[mat-tab-link]')) as HTMLAnchorElement[];

    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/overview', '/samples']);
  });
});
