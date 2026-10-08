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
import { CodeRequiredResponse } from '../core/models';
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
      @if (challenge(); as ch) {
        <h1>Código de acesso</h1>
        <p>
          Enviamos um código de 6 números para <strong>{{ ch.sentTo }}</strong>. Ele vale por 10
          minutos.
        </p>
        @if (codeInfo()) {
          <p class="alert alert-info" role="status">{{ codeInfo() }}</p>
        }
        @if (error()) {
          <p class="alert alert-error" role="alert">{{ error() }}</p>
        }
        <form [formGroup]="codeForm" (ngSubmit)="submitCode()" novalidate class="stack">
          <mat-form-field>
            <mat-label>Código</mat-label>
            <input
              matInput
              formControlName="code"
              autocomplete="one-time-code"
              inputmode="numeric"
              maxlength="6"
              class="code-input"
            />
            @if (codeForm.controls.code.hasError('required')) {
              <mat-error>Informe o código.</mat-error>
            } @else if (codeForm.controls.code.hasError('pattern')) {
              <mat-error>O código tem 6 números.</mat-error>
            }
          </mat-form-field>
          <div class="form-actions">
            <button mat-flat-button type="submit" [disabled]="loading()">
              {{ loading() ? 'Conferindo…' : 'Confirmar' }}
            </button>
          </div>
        </form>
        <p class="links-after">
          Não chegou? Confira o spam ou
          <button type="button" class="link-button" (click)="resend()">envie outro código</button>.
        </p>
        <p><button type="button" class="link-button" (click)="restart()">Voltar e entrar de novo</button></p>
      } @else {
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
      }
    </app-auth-layout>
  `,
  styles: `
    .code-input {
      font-size: 1.6rem;
      letter-spacing: 0.5rem;
      font-family: 'Courier New', monospace;
    }
    .link-button {
      background: none;
      border: 0;
      padding: 0;
      font: inherit;
      color: var(--sh-primary);
      text-underline-offset: 3px;
      text-decoration: underline;
      cursor: pointer;
    }
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
  protected readonly challenge = signal<CodeRequiredResponse | null>(null);
  protected readonly codeInfo = signal('');
  protected readonly codeForm = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\s*\d{6}\s*$/)]],
  });
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
      next: (r) => {
        if ('codeRequired' in r) {
          this.challenge.set(r);
          this.loading.set(false);
          return;
        }
        this.goHome();
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

  protected submitCode(): void {
    const ch = this.challenge();
    if (!ch) return;
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.codeInfo.set('');
    this.auth.verifyLoginCode(ch.challenge, this.codeForm.getRawValue().code).subscribe({
      next: () => this.goHome(),
      error: (e: unknown) => {
        const problem = toProblem(e);
        this.loading.set(false);
        if (problem.code === 'CODE_EXPIRED') {
          // Código vencido ou tentativas esgotadas: volta para a senha.
          this.restart();
        }
        this.error.set(problem.detail ?? 'Não foi possível conferir o código.');
        this.codeForm.reset();
      },
    });
  }

  protected resend(): void {
    const ch = this.challenge();
    if (!ch) return;
    this.error.set('');
    this.auth.resendLoginCode(ch.challenge).subscribe({
      next: (r) => this.codeInfo.set(r.message),
      error: (e: unknown) => {
        const problem = toProblem(e);
        if (problem.code === 'CODE_EXPIRED') {
          this.restart();
        }
        this.error.set(problem.detail ?? 'Não foi possível enviar outro código.');
      },
    });
  }

  protected restart(): void {
    this.challenge.set(null);
    this.codeInfo.set('');
    this.codeForm.reset();
    this.form.controls.password.reset();
    this.error.set('');
  }

  private goHome(): void {
    const target = this.voltar();
    const url = target && target.startsWith('/') ? target : this.auth.homeRoute();
    // Se a navegação não concluir (guard, rota inválida), libera o botão em vez de travar.
    this.router
      .navigateByUrl(url)
      .then((ok) => ok || this.loading.set(false))
      .catch(() => this.loading.set(false));
  }
}
