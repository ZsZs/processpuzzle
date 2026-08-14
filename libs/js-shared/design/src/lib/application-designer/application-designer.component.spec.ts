import { ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApplicationDesignerComponent } from './application-designer.component';
import { APPLICATION_DESIGNER_TABS } from './application-designer.tabs';

describe('ApplicationDesignerComponent', () => {
  const testConfig: TranslocoTestConfig = {
    scope: 'design',
    translations: { en: {}, 'design/en': { applications: 'Applications', modules: 'Modules', widgets: 'Widgets' } },
  };
  let component: ApplicationDesignerComponent;
  let fixture: ComponentFixture<ApplicationDesignerComponent>;

  beforeEach(async () => {
    // No `provideTranslocoScope` here, as in production: the component names its scope on the directive, so
    // that nothing it declares can shadow the scopes the routes rendered into its outlet register.
    const result = await setUpTranslocoTestBed(ApplicationDesignerComponent, testConfig, { providers: [provideRouter([])] });
    component = result.component;
    fixture = result.fixture;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(component.tabs).toBe(APPLICATION_DESIGNER_TABS);
  });

  it('renders one tab link per declared tab, in order', () => {
    const links = fixture.debugElement.queryAll(By.css('a[mat-tab-link]'));

    expect(links).toHaveLength(APPLICATION_DESIGNER_TABS.length);
    expect(links.map((link) => link.nativeElement.getAttribute('href'))).toEqual(APPLICATION_DESIGNER_TABS.map((tab) => `/${tab.path}`));
  });

  it('labels each tab from the design scope and shows its icon', () => {
    const links = fixture.debugElement.queryAll(By.css('a[mat-tab-link]'));
    const texts = links.map((link) => (link.nativeElement as HTMLElement).textContent?.trim());

    // The icon renders as the ligature text of a material-symbols span, so it precedes the label.
    APPLICATION_DESIGNER_TABS.forEach((tab, index) => {
      expect(texts[index]).toContain(tab.icon);
      expect(texts[index]).not.toContain(tab.label);
    });
    expect(texts[0]).toContain('Applications');
  });

  it('hosts the child routes in a tab panel', () => {
    expect(fixture.debugElement.query(By.css('mat-tab-nav-panel router-outlet'))).toBeTruthy();
  });
});
