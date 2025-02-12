import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SampleFormsComponent } from './sample-forms.component';
import { provideRouter } from '@angular/router';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

describe('BaseFormsComponent', () => {
  let component: SampleFormsComponent;
  let fixture: ComponentFixture<SampleFormsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarkdownComponent, SampleFormsComponent],
      providers: [provideAnimations(), provideHttpClient(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(SampleFormsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
