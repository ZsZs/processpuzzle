import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture } from '@angular/core/testing';
import { ContentComponent } from './content.component';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';

describe('ContentComponent', () => {
  const testConfig: TranslocoTestConfig = { translations: { en: {} } };
  let component: ContentComponent;
  let fixture: ComponentFixture<ContentComponent>;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(ContentComponent, testConfig, {
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
