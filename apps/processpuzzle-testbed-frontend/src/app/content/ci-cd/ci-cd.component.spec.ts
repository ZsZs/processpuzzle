import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, input, output } from '@angular/core';
import { CiCdComponent } from './ci-cd.component';
import { MarkdownComponent } from 'ngx-markdown';

@Component({ selector: 'markdown', template: '' })
class MockMarkdownComponent {
  readonly src = input<string>();
  readonly load = output<string>();
  readonly error = output<string | Error>();
}

describe('CiCdComponent', () => {
  let component: CiCdComponent;
  let fixture: ComponentFixture<CiCdComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CiCdComponent],
    })
      .overrideComponent(CiCdComponent, {
        remove: { imports: [MarkdownComponent] },
        add: { imports: [MockMarkdownComponent] },
      })
      .compileComponents();

    fixture = TestBed.createComponent(CiCdComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
