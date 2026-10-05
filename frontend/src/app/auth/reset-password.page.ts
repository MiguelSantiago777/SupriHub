import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { AuthLayout } from './auth-layout';
import { AuthService } from '../core/auth/auth.service';
import { applyServerErrors } from '../core/http/problem';
import { PASSWORD_PATTERN, PasswordField } from './password-field';

@Component({
  selector: 'app-reset-password-page',
  imports: [AuthLayout, ReactiveFormsModule, RouterLink, MatButtonModule, PasswordField],
  template: `
    <app-auth-layout variant="default">
      <h1>Criar nova senha</h1>
      @if (done()) {
        <p class="alert alert-success" role="status">{{ done() }}</p>
        <a mat-flat-button routerLink="/entrar">Entrar</a>
      } @else if (!token()) {
        <p class="alert alert-error" role="alert">
          Link inválido. Peça um novo em "Esqueci minha senha".
        </p>
        <p><a routerLink="/esqueci-senha">Esqueci minha senha</a></p>
      } @else {
        @if (error()) {
          <p class="alert alert-error" role="alert">{{ error() }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="stack">
          <app-password-field
            [control]="form.controls.newPassword"
            label="Nova senha"
            autocomplete="new-password"
            hint="Mínimo de 8 caracteres, com letras e números."
          />
          <div class="form-actions">
            <button mat-flat-button type="submit" [disabled]="loading()">Salvar nova senha</button>
          </div>
        </form>
      }
    </app-auth-layout>
  `,
})
export class ResetPasswordPage {
  private readonly auth = inject(AuthService);
  readonly token = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    newPassword: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly done = signal('');

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.auth.resetPassword(this.token() ?? '', this.form.getRawValue().newPassword).subscribe({
      next: (r) => this.done.set(r.message),
      error: (e: unknown) => {
        this.error.set(applyServerErrors(this.form, e));
        this.loading.set(false);
      },
    });
  }
}
