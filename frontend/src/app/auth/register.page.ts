import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AuthLayout } from './auth-layout';
import { AuthService } from '../core/auth/auth.service';
import { applyServerErrors } from '../core/http/problem';
import { PASSWORD_PATTERN, PasswordField } from './password-field';

const samePassword: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const confirm = group.get('confirmPassword');
  if (!confirm) return null;
  const mismatch = group.get('password')?.value !== confirm.value;
  if (mismatch && confirm.value) {
    confirm.setErrors({ ...(confirm.errors ?? {}), mismatch: true });
  } else if (confirm.hasError('mismatch')) {
    const rest = { ...(confirm.errors ?? {}) };
    delete rest['mismatch'];
    confirm.setErrors(Object.keys(rest).length ? rest : null);
  }
  return null;
};

@Component({
  selector: 'app-register-page',
  imports: [
    AuthLayout,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    PasswordField,
  ],
  template: `
    <app-auth-layout variant="register">
      @if (done()) {
        <h1>Quase lá!</h1>
        <div class="alert alert-success" role="status">
          <p>{{ done() }}</p>
        </div>
        <p>
          Não recebeu? Confira a caixa de spam ou
          <a routerLink="/reenviar-confirmacao">peça um novo link</a>.
        </p>
      } @else {
        <h1>Criar conta</h1>
        <p class="muted">
          Primeiro passo do credenciamento no Contrata + NATA. Depois de confirmar seu e-mail você
          preenche o requerimento e envia os documentos.
        </p>
        @if (error()) {
          <p class="alert alert-error" role="alert">{{ error() }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="stack">
          <mat-form-field>
            <mat-label>Nome completo</mat-label>
            <input matInput formControlName="fullName" autocomplete="name" />
            <mat-hint>Seu nome ou o do responsável pelo negócio.</mat-hint>
            @if (form.controls.fullName.hasError('required')) {
              <mat-error>Informe seu nome.</mat-error>
            } @else if (form.controls.fullName.hasError('minlength')) {
              <mat-error>Nome muito curto.</mat-error>
            }
          </mat-form-field>
          <mat-form-field>
            <mat-label>E-mail</mat-label>
            <input
              matInput
              type="email"
              formControlName="email"
              autocomplete="email"
              inputmode="email"
            />
            <mat-hint>Você vai receber um link de confirmação neste e-mail.</mat-hint>
            @if (form.controls.email.hasError('required')) {
              <mat-error>Informe seu e-mail.</mat-error>
            } @else if (form.controls.email.hasError('email')) {
              <mat-error>E-mail inválido.</mat-error>
            } @else if (form.controls.email.hasError('server')) {
              <mat-error>{{ form.controls.email.getError('server') }}</mat-error>
            }
          </mat-form-field>
          <app-password-field
            [control]="form.controls.password"
            label="Crie uma senha"
            autocomplete="new-password"
            hint="Mínimo de 8 caracteres, com letras e números."
          />
          <app-password-field
            [control]="form.controls.confirmPassword"
            label="Repita a senha"
            autocomplete="new-password"
          />
          <p class="small muted">
            Seus dados serão usados apenas para o credenciamento e a comunicação com o Contrata +
            NATA, conforme a Lei Geral de Proteção de Dados (LGPD) e o art. 28 da Política de
            Credenciamento.
          </p>
          <div class="form-actions">
            <button mat-flat-button type="submit" [disabled]="loading()">
              {{ loading() ? 'Enviando…' : 'Criar minha conta' }}
            </button>
          </div>
        </form>
        <p>Já tem conta? <a routerLink="/entrar">Entrar</a></p>
      }
    </app-auth-layout>
  `,
  styles: `
    .small {
      font-size: 0.9rem;
    }
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(200)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: samePassword },
  );
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
    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register(fullName.trim(), email.trim(), password).subscribe({
      next: (r) => this.done.set(r.message),
      error: (e: unknown) => {
        this.error.set(applyServerErrors(this.form, e));
        this.loading.set(false);
      },
    });
  }
}
