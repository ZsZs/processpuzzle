import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatError, MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatInput, MatLabel } from '@angular/material/input';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatDivider } from '@angular/material/divider';
import { NgIf } from '@angular/common';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { Auth, GoogleAuthProvider, signInWithPopup } from '@angular/fire/auth';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE, AuthService } from '@processpuzzle/auth/domain';
import { NavigateBackService } from '@processpuzzle/widgets';

@Component({
  selector: 'pp-login',
  templateUrl: 'login.component.html',
  styleUrls: ['login.component.css'],
  imports: [MatButton, MatDivider, MatError, MatFormField, MatIcon, MatIconButton, MatInput, MatLabel, MatSuffix, ReactiveFormsModule, NgIf, RouterLink, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class LoginComponent implements OnInit {
  errorMessage = signal('');
  hidePassword = true;
  isLoading = signal(false);
  loginForm!: FormGroup;
  private readonly authService: AuthService = inject(AUTHENTICATION_SERVICE);
  private readonly auth = inject(Auth);
  private readonly fb = inject(FormBuilder);
  private readonly navigateBackService = inject(NavigateBackService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snackBar = inject<MatSnackBar>(MatSnackBar);

  // region public event handlers
  ngOnInit(): void {
    const user = this.route.snapshot.data['signedInUser'];
    if (!user) {
      this.loginForm = this.buildLoginForm();
    }
  }

  async onSubmit() {
    if (this.loginForm.invalid) return;
    this.errorMessage.set('');
    this.isLoading.set(true);

    try {
      const { email, password } = this.loginForm.value;
      await this.authService.login(this.navigateBackService.getRouteStack().pop(), email, password);
      await this.router.navigate(['/']);
    } catch (error: any) {
      const message = this.getErrorMessage(error.code || error.message);
      this.errorMessage.set(message);
    } finally {
      this.isLoading.set(false);
    }
  }

  protected async signInWithGoogle() {
    this.errorMessage.set('');
    this.isLoading.set(true);
    try {
      await signInWithPopup(this.auth, new GoogleAuthProvider());
      await this.router.navigate(['/']);
    } catch (error: any) {
      this.errorMessage.set(this.getErrorMessage(error.code || error.message));
    } finally {
      this.isLoading.set(false);
    }
  }
  // endregion

  // region protected, private helper methods
  private buildLoginForm() {
    return this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required],
    });
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
  // endregion
}
