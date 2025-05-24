import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Auth, createUserWithEmailAndPassword } from '@angular/fire/auth';
import { RouterLink } from '@angular/router';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatError, MatFormField } from '@angular/material/form-field';
import { MatInput, MatLabel } from '@angular/material/input';
import { NgIf } from '@angular/common';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NavigateBackService } from '@processpuzzle/widgets';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FirebaseError } from 'firebase-admin/lib/utils/error';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'pp-registration',
  templateUrl: 'registration.component.html',
  styleUrls: ['registration.component.css'],
  imports: [ReactiveFormsModule, MatProgressBar, MatFormField, MatInput, NgIf, MatIconButton, MatIcon, MatLabel, MatButton, RouterLink, MatError, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class RegistrationComponent {
  private readonly auth = inject(Auth);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly navigateBack = inject(NavigateBackService);

  protected registerForm: FormGroup;
  protected isLoading = signal<boolean>(false);
  protected errorMessage = signal<string>('');
  protected hidePassword = true;
  protected hideConfirmPassword = true;

  constructor(private readonly snackBar: MatSnackBar) {
    this.registerForm = this.fb.group(
      {
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: ['', Validators.required],
      },
      {
        validators: [this.passwordMatchValidator],
      },
    );
  }

  private passwordMatchValidator(): ValidatorFn {
    return (form: AbstractControl): ValidationErrors | null => {
      const password = form.get('password')?.value;
      const confirmPassword = form.get('confirmPassword')?.value;

      if (password !== confirmPassword) {
        return { passwordMismatch: true };
      }
      return null;
    };
  }

  async onSubmit(): Promise<void> {
    if (this.registerForm.invalid) return;

    this.isLoading.set(true);

    try {
      const { email, password } = this.registerForm.value;
      await createUserWithEmailAndPassword(this.auth, email, password);
    } catch (error: unknown) {
      this.snackBar.open(this.getErrorMessage((error as FirebaseError).code), 'Close', {
        duration: 5000,
        panelClass: ['error-snackbar'],
      });
    } finally {
      this.isLoading.set(false);
      this.errorMessage.set('');
      this.navigateBack.goBack();
    }
  }

  private getErrorMessage(errorCode: string): string {
    switch (errorCode) {
      case 'auth/email-already-in-use':
        return 'This email address is already registered';
      case 'auth/invalid-email':
        return 'Please enter a valid email address';
      case 'auth/operation-not-allowed':
        return 'Email/password registration is not enabled';
      case 'auth/weak-password':
        return 'Please choose a stronger password';
      default:
        return 'An error occurred during registration. Please try again.';
    }
  }
}
