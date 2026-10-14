import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatError, MatFormField } from '@angular/material/form-field';
import { MatInput, MatLabel } from '@angular/material/input';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { RegistrationService } from '@processpuzzle/auth/domain';

@Component({
  selector: 'pp-registration',
  templateUrl: './registration.component.html',
  styleUrls: ['./registration.component.css'],
  imports: [ReactiveFormsModule, MatProgressBar, MatFormField, MatInput, MatIconButton, MatIcon, MatLabel, MatButton, RouterLink, MatError, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'auth' })],
})
export class RegistrationComponent {
  private readonly registrationService = inject(RegistrationService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject<MatSnackBar>(MatSnackBar);
  public registerForm: FormGroup;
  public isLoading = signal<boolean>(false);
  public errorMessage = signal<string>('');
  public hidePassword = true;
  public hideConfirmPassword = true;

  constructor() {
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
      await this.registrationService.register({ email, password });
      this.snackBar.open('Your account was created. Please sign in.', 'Close', { duration: 5000 });
      await this.router.navigateByUrl('/auth/login');
    } catch (error: unknown) {
      const errorCode = (error as { code?: string }).code ?? 'unknown';
      this.snackBar.open(this.getErrorMessage(errorCode), 'Close', {
        duration: 5000,
        panelClass: ['error-snackbar'],
      });
    } finally {
      this.isLoading.set(false);
    }
  }

  private getErrorMessage(errorCode: string): string {
    switch (errorCode) {
      case 'user.already-exists':
        return 'This email address is already registered';
      case 'registration.password-policy':
        return 'Please choose a stronger password';
      case 'registration.disabled':
        return 'Registration is not enabled';
      case 'registration.rate-limited':
        return 'Too many attempts. Please try again later';
      case 'registration.unreachable':
      case 'user-directory.unavailable':
        return 'Registration is temporarily unavailable. Please try again later';
      default:
        return 'An error occurred during registration. Please try again.';
    }
  }
}
