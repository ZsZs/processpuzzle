# @processpuzzle/auth
![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-auth.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_auth&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_auth)
[![Node version](https://img.shields.io/npm/v/%40processpuzzle%2Fauth?style=flat)](https://www.npmjs.com/package/@processpuzzle/auth)

The Auth library provides widgets and services for authentication and authorization. It offers:
- [authentication menu](#auth-button)
- [login](#login-component)
- [logout](#logout-component)
- [registration](#registration-component)
- [user profile](#my-profile-component)
- [auth service](#authentication-service)
- [auth guard](#authentication-guard)

## Auth Button

The `AuthButtonComponent` is a versatile authentication button that displays different options based on the user's authentication status. It provides a clean, intuitive interface for users to access authentication-related features.

### Features

- Displays a person icon button that opens a dropdown menu
- Dynamically shows different menu options based on authentication status
- When not authenticated, shows Login and Register options
- When authenticated, shows My Profile and Logout options
- Integrates with Angular Router for navigation to auth pages
- Uses Angular Material components for a consistent UI experience

### Usage

Simply add the component to your template:

```html
<pp-auth-button></pp-auth-button>
```

The component automatically detects the user's authentication status using the `AuthService` and displays the appropriate menu options.

#### Example: Adding to a toolbar

```html
<mat-toolbar>
  <span>My App</span>
  <span class="spacer"></span>
  <pp-auth-button></pp-auth-button>
</mat-toolbar>
```

### API Reference

#### Selector
`pp-auth-button`

#### Dependencies
- `AuthService` - Used to determine the user's authentication status
- Angular Material components (MatIconButton, MatMenu, MatMenuItem)
- Angular Router - For navigation to auth-related routes

#### Properties
- `isAuthenticated` - A computed signal that reflects the current authentication status
- `routes` - The filtered list of authentication routes with titles

### Styling

The component uses Angular Material styling by default. You can target the component with CSS using the `.auth-button` class:

```css
.auth-button {
  /* Your custom styles */
}
```

### Customization

The component uses routes defined in `authRoutes` from the auth library. Each route can have the following properties:

- `path` - The route path
- `component` - The component to render
- `title` - The display name in the menu
- `data.icon` - The Material icon to display
- `data.authToggle` - Whether to show the item when authenticated (false) or not authenticated (true)


## Login Component

The `LoginComponent` provides a user-friendly interface for authentication, allowing users to sign in with their email and password or through Google authentication. It includes form validation, error handling, and visual feedback during the authentication process.

### Features

- Clean, responsive login form with Material Design styling
- Email and password authentication with Firebase
- Google sign-in integration
- Real-time form validation with error messages
- Password visibility toggle
- Loading state indication during authentication
- Error handling with user-friendly messages
- Redirect to home page after successful login
- Link to registration page for new users

### Usage

Add the component to your routes configuration:

```typescript
const routes: Routes = [
  { path: 'login', component: LoginComponent }
];
```

Then navigate to the login route:

```typescript
// In your component
constructor(private router: Router) {}

navigateToLogin() {
  this.router.navigate(['/login']);
}
```

Or create a link in your template:

```html
<a routerLink="/login">Login</a>
```

### API Reference

#### Selector
`pp-login`

#### Dependencies
- `AuthService` - Used for authentication operations
- Angular Material components (MatFormField, MatInput, MatButton, etc.)
- Angular Reactive Forms - For form handling and validation
- Firebase Authentication - For authentication operations

#### Properties
- `loginForm` - FormGroup that manages the login form state and validation
- `isLoading` - Signal that indicates whether an authentication operation is in progress
- `errorMessage` - Signal that contains any error message to display
- `hidePassword` - Boolean that controls password visibility

#### Methods
- `onSubmit()` - Handles form submission for email/password login
- `signInWithGoogle()` - Initiates Google authentication
- `getErrorMessage(errorCode: string)` - Converts Firebase error codes to user-friendly messages

### Styling

The component uses Angular Material styling with custom CSS for layout. You can target the component with CSS using the `.login-container` class:

```css
.login-container {
  /* Your custom styles */
}
```

The form is responsive and will adapt to different screen sizes, with a maximum width of 400px.

## Logout Component

The `LogoutComponent` provides a user-friendly confirmation dialog for logging out of the application. It ensures users don't accidentally log out by requiring explicit confirmation before signing out.

### Features

- Clean, simple confirmation dialog with Material Design styling
- Asks for confirmation before logging out
- Shows loading state during the logout process
- Error handling for logout failures
- Automatic navigation back to the previous page after logout or cancellation

### Usage

Add the component to your routes configuration:

```typescript
const routes: Routes = [
  { path: 'logout', component: LogoutComponent }
];
```

Then navigate to the logout route:

```typescript
// In your component
constructor(private router: Router) {}

navigateToLogout() {
  this.router.navigate(['/logout']);
}
```

Or create a link in your template:

```html
<a routerLink="/logout">Logout</a>
```

The component is typically accessed through the `AuthButtonComponent` menu when a user is authenticated.

### API Reference

#### Selector
`pp-logout`

#### Dependencies
- `AuthService` - Used for signing out the user
- `NavigateBackService` - Used for navigation after logout or cancellation
- Angular Material components (MatDialog, MatButton)

#### Properties
- `isLoading` - Signal that indicates whether a logout operation is in progress

#### Methods
- `onCancel()` - Handles cancellation of the logout process
- `onLogout()` - Handles the logout process, including error handling

### Styling

The component uses Angular Material styling with custom CSS for layout. You can target the component with CSS using the `.logout-dialog` class:

```css
.logout-dialog {
  /* Your custom styles */
}
```

The dialog is compact and focused, providing a clear confirmation message and action buttons.

## Registration Component

The `RegistrationComponent` provides a user-friendly interface for creating a new account. It includes form validation, error handling, and visual feedback during the registration process.

### Features

- Clean, responsive registration form with Material Design styling
- Email and password registration with Firebase
- Real-time form validation with error messages
- Password visibility toggle for both password fields
- Password confirmation with matching validation
- Password strength requirements (minimum 6 characters)
- Loading state indication during registration
- Error handling with user-friendly messages
- Automatic navigation back after successful registration
- Link to login page for users who already have an account

### Usage

Add the component to your routes configuration:

```typescript
const routes: Routes = [
  { path: 'register', component: RegistrationComponent }
];
```

Then navigate to the registration route:

```typescript
// In your component
constructor(private router: Router) {}

navigateToRegister() {
  this.router.navigate(['/register']);
}
```

Or create a link in your template:

```html
<a routerLink="/register">Create Account</a>
```

The component is typically accessed through the `AuthButtonComponent` menu when a user is not authenticated.

### API Reference

#### Selector
`pp-registration`

#### Dependencies
- `Auth` from Firebase - Used for user registration
- `NavigateBackService` - Used for navigation after registration
- `MatSnackBar` - Used for displaying error messages
- Angular Material components (MatFormField, MatInput, MatButton, etc.)
- Angular Reactive Forms - For form handling and validation

#### Properties
- `registerForm` - FormGroup that manages the registration form state and validation
- `isLoading` - Signal that indicates whether a registration operation is in progress
- `errorMessage` - Signal that contains any error message to display
- `hidePassword` - Boolean that controls password visibility
- `hideConfirmPassword` - Boolean that controls confirm password visibility

#### Methods
- `onSubmit()` - Handles form submission for registration
- `getErrorMessage(errorCode: string)` - Converts Firebase error codes to user-friendly messages
- `passwordMatchValidator()` - Custom validator to ensure password and confirm password match

### Styling

The component uses Angular Material styling with custom CSS for layout. You can target the component with CSS using the `.registration-container` class:

```css
.registration-container {
  /* Your custom styles */
}
```

The form is responsive and will adapt to different screen sizes, with a maximum width of 400px.

## My Profile Component

The `MyProfileComponent` provides a user interface for viewing and managing the authenticated user's profile information. It allows users to see their account details and potentially update their profile settings.

### Features

- Accessible only to authenticated users
- Displays user profile information
- Clean, intuitive interface with Material Design styling
- Integrated with the authentication system

### Usage

Add the component to your routes configuration:

```typescript
const routes: Routes = [
  { path: 'my-profile', component: MyProfileComponent }
];
```

Then navigate to the my-profile route:

```typescript
// In your component
constructor(private router: Router) {}

navigateToProfile() {
  this.router.navigate(['/my-profile']);
}
```

Or create a link in your template:

```html
<a routerLink="/my-profile">My Profile</a>
```

The component is typically accessed through the `AuthButtonComponent` menu when a user is authenticated.

### API Reference

#### Selector
`pp-my-profile`

#### Dependencies
- Angular Material components (for styling and UI elements)
- Authentication service (for accessing user profile data)

#### Properties
- Currently minimal implementation with planned expansion for user profile management

### Styling

The component uses Angular Material styling with custom CSS for layout. You can target the component with CSS:

```css
pp-my-profile {
  /* Your custom styles */
}
```

The profile view is designed to be clean and user-friendly, presenting user information in an organized manner.

## Authentication Service

The `AuthService` provides core authentication functionality for the application, leveraging Firebase Authentication. It manages user authentication state and provides methods for common authentication operations.

### Features

- Firebase Authentication integration
- Reactive authentication state management using Angular signals
- Simple API for checking authentication status
- Methods for signing out and retrieving the current user
- Singleton service available throughout the application

### Usage

Import and inject the service in your components or other services:

```typescript
import { Component, inject } from '@angular/core';
import { AuthService } from '@processpuzzle/auth';

@Component({
  selector: 'app-example',
  template: `
    <div *ngIf="authService.isAuthenticated()">
      Welcome, {{ authService.user()?.email }}!
      <button (click)="logout()">Logout</button>
    </div>
    <div *ngIf="!authService.isAuthenticated()">
      Please log in to continue.
    </div>
  `
})
export class ExampleComponent {
  authService = inject(AuthService);

  async logout() {
    await this.authService.signOut();
    // Handle post-logout navigation or UI updates
  }
}
```

### API Reference

#### Dependencies
- `Auth` from Firebase - Used for authentication operations
- Angular's dependency injection system

#### Properties
- `user` - A signal that provides the current Firebase User object or null if not authenticated
- `isAuthenticated` - A computed signal that returns true if a user is logged in, false otherwise

#### Methods
- `signOut()` - Asynchronous method that signs out the current user
- `getCurrentUser()` - Synchronous method that returns the current Firebase User object or null

### Integration with Components

The `AuthService` is used by several components in the auth library:

- `AuthButtonComponent` uses it to determine which menu options to display
- `LogoutComponent` uses it to sign out the user
- `AuthGuard` uses it to protect routes that require authentication

## Authentication Guard

The `authGuard` is a route guard that protects routes requiring authentication. It redirects unauthenticated users to the login page when they attempt to access protected routes.

### Features

- Functional route guard using Angular's dependency injection
- Integrates with the `AuthService` to check authentication status
- Redirects unauthenticated users to the login page
- Simple API that works with Angular Router

### Usage

Add the guard to your routes configuration:

```typescript
import { Routes } from '@angular/router';
import { authGuard } from '@processpuzzle/auth';

export const routes: Routes = [
  {
    path: 'protected',
    component: ProtectedComponent,
    canActivate: [authGuard]
  }
];
```

### API Reference

#### Dependencies
- `Router` from Angular - Used for navigation to the login page
- `AuthService` - Used to check authentication status

#### Behavior
- Returns `true` if the user is authenticated, allowing navigation to the protected route
- Redirects to '/auth/login' and returns `false` if the user is not authenticated, preventing navigation to the protected route
