import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CiCdComponent } from './ci-cd.component';
import { MarkdownComponent, provideMarkdown } from 'ngx-markdown';
import { provideAnimations } from '@angular/platform-browser/animations';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('CiCdComponent', () => {
  let component: CiCdComponent;
  let fixture: ComponentFixture<CiCdComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CiCdComponent, MarkdownComponent],
      providers: [provideAnimations(), provideHttpClient(), provideMarkdown({ loader: HttpClient }), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(CiCdComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
