import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_SNACK_BAR_DATA, MatSnackBarRef } from '@angular/material/snack-bar';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorSnackbarComponent } from './error-snackbar.component';

describe('ErrorSnackbarComponent', () => {
  let component: ErrorSnackbarComponent;
  let fixture: ComponentFixture<ErrorSnackbarComponent>;
  let snackBarRef: Pick<MatSnackBarRef<ErrorSnackbarComponent>, 'dismiss'>;

  beforeEach(async () => {
    snackBarRef = { dismiss: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ErrorSnackbarComponent],
      providers: [
        { provide: MAT_SNACK_BAR_DATA, useValue: { message: 'Unexpected failure' } },
        { provide: MatSnackBarRef, useValue: snackBarRef },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorSnackbarComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render the provided error message', () => {
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toHaveTextContent('Unexpected failure');
  });

  it('should dismiss the snackbar when close is clicked', () => {
    fixture.nativeElement.querySelector('button').click();

    expect(snackBarRef.dismiss).toHaveBeenCalled();
  });
});
