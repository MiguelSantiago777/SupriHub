import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, OnInit, signal } from '@angular/core';
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
      } @else if (linkState() === 'checking') {
        <p role="status">Conferindo o link…</p>
      } @else if (linkState() === 'invalid') {
        <p class="alert alert-error" role="alert">
          Este link é inválido, expirou ou já foi usado. Peça um novo em "Esqueci minha senha".
        </p>
        <p><a mat-flat-button routerLink="/esqueci-senha">Pedir novo link</a></p>
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
export class ResetPasswordPage implements OnInit {
  private readonly auth = inject(AuthService);
  readonly token = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    newPassword: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly done = signal('');
  protected readonly linkState = signal<'checking' | 'valid' | 'invalid'>('checking');

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.linkState.set('invalid');
      return;
    }
    // Quem abre um link de nova senha não deve continuar com outra conta aberta.
    if (this.auth.isAuthenticated()) {
      this.auth.endSession().subscribe();
    }
    this.auth.checkResetLink(token).subscribe({
      next: () => this.linkState.set('valid'),
      // Só 400 diz que o link não vale; em falha de rede deixa tentar (o envio confere de novo).
      error: (e: unknown) =>
        this.linkState.set(e instanceof HttpErrorResponse && e.status === 400 ? 'invalid' : 'valid'),
    });
  }

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
