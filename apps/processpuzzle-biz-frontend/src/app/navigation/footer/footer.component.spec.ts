import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { FooterComponent } from './footer.component';

describe('FooterComponent', () => {
  let component: FooterComponent;
  let fixture: ComponentFixture<FooterComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FooterComponent],
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APPLICATION_VERSION: '0.1.0' } } }],
    }).compileComponents();

    fixture = TestBed.createComponent(FooterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-toolbar with the version button', () => {
    expect(fixture.debugElement.query(By.css('mat-toolbar')).nativeElement).toBeTruthy();
    expect(fixture.debugElement.query(By.css('pp-version-button'))).toBeTruthy();
  });
});
