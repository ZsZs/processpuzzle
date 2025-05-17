import { Component, inject, signal } from '@angular/core';
import { Auth, GoogleAuthProvider, signInWithEmailAndPassword, signInWithPopup } from '@angular/fire/auth';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatError, MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatInput, MatLabel } from '@angular/material/input';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/divider';
import { NgIf } from '@angular/common';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FirebaseError } from 'firebase-admin/lib/utils/error';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'pp-login',
  templateUrl: 'login.component.html',
  styleUrls: ['login.component.css'],
  imports: [MatButton, MatDivider, MatError, MatFormField, MatIcon, MatIconButton, MatInput, MatLabel, MatSuffix, ReactiveFormsModule, NgIf, RouterLink, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class LoginComponent {
  private readonly auth: Auth = inject(Auth);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  loginForm: FormGroup;
  isLoading = signal(false);
  errorMessage = signal('');
  hidePassword = true;

  constructor(private readonly snackBar: MatSnackBar) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required],
    });
  }

  async onSubmit() {
    if (this.loginForm.invalid) return;

    this.isLoading.set(true);

    try {
      const { email, password } = this.loginForm.value;
      await signInWithEmailAndPassword(this.auth, email, password);
      await this.router.navigate(['/']);
    } catch (error: unknown) {
      this.snackBar.open(this.getErrorMessage((error as FirebaseError).code), 'Close', { duration: 5000, panelClass: ['error-snackbar'] });
    } finally {
      this.isLoading.set(false);
      this.errorMessage.set('');
    }
  }

  async signInWithGoogle() {
    this.isLoading.set(true);

    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(this.auth, provider);
      await this.router.navigate(['/']); // Navigate to home page after successful login
    } catch (error: unknown) {
      this.snackBar.open(this.getErrorMessage((error as FirebaseError).code), 'Close', { duration: 5000, panelClass: ['error-snackbar'] });
    } finally {
      this.isLoading.set(false);
      this.errorMessage.set('');
    }
  }

  private getErrorMessage(errorCode: string): string {
    switch (errorCode) {
      case 'auth/invalid-email':
        return 'Invalid email address';
      case 'auth/user-disabled':
        return 'This account has been disabled';
      case 'auth/user-not-found':
        return 'No account found with this email';
      case 'auth/wrong-password':
        return 'Invalid password';
      case 'auth/popup-closed-by-user':
        return 'Sign-in popup was closed before completion';
      default:
        return 'An error occurred during sign in';
    }
  }
}
