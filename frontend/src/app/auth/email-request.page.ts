import { Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { problemMessage } from '../core/http/problem';

export type EmailRequestMode = 'forgot-password' | 'resend-confirmation';

const TEXTS: Record<EmailRequestMode, { heading: string; intro: string; action: string }> = {
  'forgot-password': {
    heading: 'Esqueci minha senha',
    intro: 'Informe o e-mail da sua conta. Vamos enviar um link para você criar uma nova senha.',
    action: 'Enviar link',
  },
  'resend-confirmation': {
    heading: 'Reenviar confirmação de e-mail',
    intro: 'Informe o e-mail usado no cadastro. Enviaremos um novo link de confirmação.',
    action: 'Reenviar link',
  },
};

/** Telas que pedem só o e-mail e mostram resposta neutra (não revela se a conta existe). */
@Component({
  selector: 'app-email-request-page',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="container narrow">
      <div class="card">
        <h1>{{ texts().heading }}</h1>
        @if (done()) {
          <p class="alert alert-success" role="status">{{ done() }}</p>
        } @else {
          <p class="muted">{{ texts().intro }}</p>
          @if (error()) {
            <p class="alert alert-error" role="alert">{{ error() }}</p>
          }
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="stack">
            <mat-form-field>
              <mat-label>E-mail</mat-label>
              <input
                matInput
                type="email"
                formControlName="email"
                autocomplete="email"
                inputmode="email"
              />
              @if (form.controls.email.hasError('required')) {
                <mat-error>Informe seu e-mail.</mat-error>
              } @else if (form.controls.email.hasError('email')) {
                <mat-error>E-mail inválido.</mat-error>
              }
            </mat-form-field>
            <div class="form-actions">
              <button mat-flat-button type="submit" [disabled]="loading()">
                {{ texts().action }}
              </button>
            </div>
          </form>
        }
        <p><a routerLink="/entrar">Voltar para Entrar</a></p>
      </div>
    </div>
  `,
})
export class EmailRequestPage {
  private readonly auth = inject(AuthService);

  /** Vem do `data` da rota. */
  readonly mode = input<EmailRequestMode>('forgot-password');
  protected readonly texts = computed(() => TEXTS[this.mode()]);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
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
    const email = this.form.getRawValue().email.trim();
    const request =
      this.mode() === 'forgot-password'
        ? this.auth.forgotPassword(email)
        : this.auth.resendConfirmation(email);
    request.subscribe({
      next: (r) => this.done.set(r.message),
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }
}
