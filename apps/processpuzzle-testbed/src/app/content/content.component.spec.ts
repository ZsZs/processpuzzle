import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ContentComponent } from './content.component';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';

describe('ContentComponent', () => {
  let component: ContentComponent;
  let fixture: ComponentFixture<ContentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContentComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ContentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('Should create component', () => {
    expect(component).toBeTruthy();
  });

  it('template structure contains: mat-card', () => {
    const matCard = fixture.debugElement.query(By.css('mat-card')).nativeElement;
    expect(matCard).toBeTruthy();
  });
});
