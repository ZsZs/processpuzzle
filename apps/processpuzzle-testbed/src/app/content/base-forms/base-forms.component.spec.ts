import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BaseFormsComponent } from 'apps/processpuzzle-testbed/src/app/content/base-forms/base-forms.component';
import { provideRouter } from '@angular/router';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

describe('BaseFormsComponent', () => {
  let component: BaseFormsComponent;
  let fixture: ComponentFixture<BaseFormsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarkdownComponent, BaseFormsComponent],
      providers: [provideAnimations(), provideHttpClient(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(BaseFormsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
