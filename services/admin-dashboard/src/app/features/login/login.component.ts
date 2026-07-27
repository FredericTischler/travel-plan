import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Login screen: POST /login with email + password, store the returned JWT
 * on success, then navigate to /users.
 */
@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected email = '';
  protected password = '';

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  submit(): void {
    this.submitting.set(true);
    this.error.set(null);

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/users']);
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Identifiants invalides.');
      },
    });
  }
}