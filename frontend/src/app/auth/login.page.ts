import { Component, inject, input, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { AuthLayout } from './auth-layout';
import { AuthService } from '../core/auth/auth.service';
import { toProblem } from '../core/http/problem';
import { PasswordField } from './password-field';
import { CaptchaWidget } from './captcha-widget';

@Component({
  selector: 'app-login-page',
  imports: [
    AuthLayout,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    PasswordField,
    CaptchaWidget,
  ],
  template: `
    <app-auth-layout variant="login">
      <h1>Entrar</h1>
      <p class="muted">Use o e-mail e a senha da sua conta no SupriHub.</p>
      @if (expirada()) {
        <p class="alert alert-info" role="status">
          Sua sessão expirou. Entre novamente para continuar.
        </p>
      }
      @if (error()) {
        <div class="alert alert-error" role="alert">
          <p>{{ error() }}</p>
          @if (notVerified()) {
            <p><a routerLink="/reenviar-confirmacao">Reenviar o e-mail de confirmação</a></p>
          }
        </div>
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
        <app-password-field [control]="form.controls.password" />
        <app-captcha />
        <div class="form-actions">
          <button mat-flat-button type="submit" [disabled]="loading()">
            {{ loading() ? 'Entrando…' : 'Entrar' }}
          </button>
        </div>
      </form>
      <p class="links-after"><a routerLink="/esqueci-senha">Esqueci minha senha</a></p>
      <p>Ainda não tem conta? <a routerLink="/criar-conta">Quero me credenciar</a></p>
    </app-auth-layout>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly voltar = input<string>();
  readonly expirada = input<string>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly notVerified = signal(false);
  private readonly captcha = viewChild.required(CaptchaWidget);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.captcha().valid()) {
      this.error.set('Conclua a verificação de segurança antes de continuar.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password, this.captcha().token()).subscribe({
      next: () => {
        const target = this.voltar();
        const url = target && target.startsWith('/') ? target : this.auth.homeRoute();
        // Se a navegação não concluir (guard, rota inválida), libera o botão em vez de travar.
        this.router
          .navigateByUrl(url)
          .then((ok) => ok || this.loading.set(false))
          .catch(() => this.loading.set(false));
      },
      error: (e: unknown) => {
        const problem = toProblem(e);
        this.notVerified.set(problem.code === 'EMAIL_NOT_VERIFIED');
        this.error.set(problem.detail ?? 'Não foi possível entrar.');
        this.loading.set(false);
        this.captcha().reset();
      },
    });
  }
}
