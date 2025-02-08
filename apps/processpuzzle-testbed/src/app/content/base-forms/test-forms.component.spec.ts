import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestFormsComponent } from './test-forms.component';
import { provideRouter } from '@angular/router';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

describe('BaseFormsComponent', () => {
  let component: TestFormsComponent;
  let fixture: ComponentFixture<TestFormsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarkdownComponent, TestFormsComponent],
      providers: [provideAnimations(), provideHttpClient(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TestFormsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
