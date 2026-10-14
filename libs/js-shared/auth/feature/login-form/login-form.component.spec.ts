import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { LoginFormComponent } from './login-form.component';

describe('LoginFormComponent', () => {
  let fixture: ComponentFixture<LoginFormComponent>;

  const root = (): HTMLElement => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [LoginFormComponent] }).compileComponents();
    fixture = TestBed.createComponent(LoginFormComponent);
    fixture.componentRef.setInput('action', 'https://kc/realms/r/login-actions/authenticate?session_code=abc');
  });

  it('should post to the Keycloak login action with username and password fields', async () => {
    await fixture.whenStable();
    const form = root().querySelector('form') as HTMLFormElement;
    expect(form.getAttribute('method')).toBe('post');
    expect(form.getAttribute('action')).toBe('https://kc/realms/r/login-actions/authenticate?session_code=abc');
    expect(root().querySelector('input[name="username"]')).not.toBeNull();
    expect((root().querySelector('input[name="password"]') as HTMLInputElement).type).toBe('password');
  });

  it('should prefill the username Keycloak sends back after a failed attempt', async () => {
    fixture.componentRef.setInput('username', 'jane@example.com');
    await fixture.whenStable();
    expect((root().querySelector('input[name="username"]') as HTMLInputElement).value).toBe('jane@example.com');
  });

  it('should show an error only when there is one', async () => {
    await fixture.whenStable();
    expect(root().querySelector('.error-message')).toBeNull();

    fixture.componentRef.setInput('error', 'Invalid username or password.');
    await fixture.whenStable();
    expect(root().querySelector('.error-message')?.textContent).toContain('Invalid username or password.');
  });

  it('should render the register and forgot-password links only when their urls are given', async () => {
    await fixture.whenStable();
    expect(root().querySelector('a')).toBeNull();

    fixture.componentRef.setInput('registerUrl', '/register');
    fixture.componentRef.setInput('registerLabel', 'Create account');
    fixture.componentRef.setInput('forgotPasswordUrl', '/reset');
    fixture.componentRef.setInput('forgotPasswordLabel', 'Forgot?');
    await fixture.whenStable();
    expect(Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'))).toEqual(['/reset', '/register']);
  });
});
