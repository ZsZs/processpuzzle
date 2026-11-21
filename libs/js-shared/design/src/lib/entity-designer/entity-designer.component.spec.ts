import { ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EntityDesignerComponent } from './entity-designer.component';
import { ENTITY_DESIGNER_TABS } from './entity-designer.tabs';

describe('EntityDesignerComponent', () => {
  const testConfig: TranslocoTestConfig = {
    scope: 'design',
    translations: {
      en: {},
      'design/en': {
        'entity-definitions': 'Entity definitions',
        units: 'Units',
      },
    },
  };
  let component: EntityDesignerComponent;
  let fixture: ComponentFixture<EntityDesignerComponent>;

  beforeEach(async () => {
    // No `provideTranslocoScope` here, as in production: the component names its scope on the directive, so
    // that nothing it declares can shadow the `base_entity` scope the routes rendered
    // into its outlet register for themselves.
    const result = await setUpTranslocoTestBed(EntityDesignerComponent, testConfig, { providers: [provideRouter([])] });
    component = result.component;
    fixture = result.fixture;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(component.tabs).toBe(ENTITY_DESIGNER_TABS);
  });

  it('renders one route button per declared tab, in order', () => {
    const links = fixture.debugElement.queryAll(By.css('a[mat-stroked-button]'));

    expect(links).toHaveLength(ENTITY_DESIGNER_TABS.length);
    expect(links.map((link) => link.nativeElement.getAttribute('href'))).toEqual(ENTITY_DESIGNER_TABS.map((tab) => `/${tab.path}`));
  });

  it('labels each route button from the design scope and shows its icon', () => {
    const links = fixture.debugElement.queryAll(By.css('a[mat-stroked-button]'));
    const texts = links.map((link) => (link.nativeElement as HTMLElement).textContent?.trim());

    // The icon renders as the ligature text of a material-symbols span, so it precedes the label.
    ENTITY_DESIGNER_TABS.forEach((tab, index) => {
      expect(texts[index]).toContain(tab.icon);
      expect(texts[index]).not.toContain(tab.label);
    });
    expect(texts[0]).toContain('Entity definitions');
  });

  it('hosts the child routes below the route buttons', () => {
    expect(fixture.debugElement.query(By.css('router-outlet'))).toBeTruthy();
  });
});
