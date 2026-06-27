import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { DesignContentComponent } from 'libs/js-shared/design/src/lib/content/design-content.component';

describe('ContentComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: DesignContentComponent;
  let fixture: ComponentFixture<DesignContentComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(DesignContentComponent, testConfig, {
      providers: [provideRouter([])],
    });
    component = result.component;
    fixture = result.fixture;
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-card', () => {
    const matCard = fixture.debugElement.query(By.css('mat-card')).nativeElement;
    expect(matCard).toBeTruthy();
  });
});
