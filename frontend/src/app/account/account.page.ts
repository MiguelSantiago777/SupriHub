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
import { AuthService } from '../core/auth/auth.service';
import { applyServerErrors } from '../core/http/problem';
import { PASSWORD_PATTERN, PasswordField } from '../auth/password-field';

const samePassword: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const confirm = group.get('confirmPassword');
  if (!confirm) return null;
  const mismatch = group.get('newPassword')?.value !== confirm.value;
  if (mismatch && confirm.value) {
    confirm.setErrors({ ...(confirm.errors ?? {}), mismatch: true });
  } else if (confirm.hasError('mismatch')) {
    const rest = { ...(confirm.errors ?? {}) };
    delete rest['mismatch'];
    confirm.setErrors(Object.keys(rest).length ? rest : null);
  }
  return null;
};

/** "Minha conta": cada pessoa troca o próprio nome, e-mail de acesso e senha. */
@Component({
  selector: 'app-account-page',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, PasswordField],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">CONTA</span>
        <h1>Minha conta</h1>
        <p>Seus dados de acesso ao SupriHub.</p>
      </div>
    </header>

    <div class="container account">
      <section class="card" aria-labelledby="dados-title">
        <h2 id="dados-title">Seus dados</h2>
        @if (nameMsg()) {
          <p class="alert alert-success" role="status">{{ nameMsg() }}</p>
        }
        @if (nameError()) {
          <p class="alert alert-error" role="alert">{{ nameError() }}</p>
        }
        <form [formGroup]="nameForm" (ngSubmit)="saveName()" novalidate class="stack">
          <mat-form-field>
            <mat-label>Nome completo</mat-label>
            <input matInput formControlName="fullName" autocomplete="name" />
            @if (nameForm.controls.fullName.hasError('required')) {
              <mat-error>Informe seu nome.</mat-error>
            } @else if (nameForm.controls.fullName.hasError('minlength')) {
              <mat-error>Nome muito curto.</mat-error>
            }
          </mat-form-field>
          <div class="form-actions">
            <button mat-flat-button type="submit" [disabled]="savingName()">Salvar nome</button>
          </div>
        </form>
      </section>

      <section class="card" aria-labelledby="email-title">
        <h2 id="email-title">E-mail de acesso</h2>
        <p class="muted">
          Hoje você entra com <strong>{{ auth.user()?.email }}</strong>. Para trocar, enviamos um
          link de confirmação para o novo endereço; o e-mail só muda depois que você abrir esse
          link.
        </p>
        @if (emailMsg()) {
          <p class="alert alert-success" role="status">{{ emailMsg() }}</p>
        }
        @if (emailError()) {
          <p class="alert alert-error" role="alert">{{ emailError() }}</p>
        }
        <form [formGroup]="emailForm" (ngSubmit)="changeEmail()" novalidate class="stack">
          <mat-form-field>
            <mat-label>Novo e-mail</mat-label>
            <input matInput type="email" formControlName="newEmail" autocomplete="email" />
            @if (emailForm.controls.newEmail.hasError('required')) {
              <mat-error>Informe o novo e-mail.</mat-error>
            } @else if (emailForm.controls.newEmail.hasError('email')) {
              <mat-error>E-mail inválido.</mat-error>
            }
          </mat-form-field>
          <app-password-field
            [control]="emailForm.controls.currentPassword"
            label="Senha atual"
            autocomplete="current-password"
          />
          <div class="form-actions">
            <button mat-flat-button type="submit" [disabled]="savingEmail()">
              Enviar link de confirmação
            </button>
          </div>
        </form>
      </section>

      <section class="card" aria-labelledby="dois-fatores-title">
        <h2 id="dois-fatores-title">Verificação em duas etapas</h2>
        @if (auth.user()?.twoFactorMandatory) {
          <p class="alert alert-success" role="status">
            Ativa. Para a equipe interna ela é obrigatória: ao entrar, depois da senha, você recebe
            um código de 6 números no seu e-mail.
          </p>
        } @else {
          <p class="muted">
            Com ela ativa, ao entrar, depois da senha, você recebe um código de 6 números no seu
            e-mail. Assim, mesmo quem descobrir sua senha não consegue entrar sem acesso ao seu
            e-mail.
          </p>
          <p>
            Situação: <strong>{{ auth.user()?.twoFactor ? 'ativa' : 'desativada' }}</strong>
          </p>
          @if (twoFactorMsg()) {
            <p class="alert alert-success" role="status">{{ twoFactorMsg() }}</p>
          }
          @if (twoFactorError()) {
            <p class="alert alert-error" role="alert">{{ twoFactorError() }}</p>
          }
          <form [formGroup]="twoFactorForm" (ngSubmit)="toggleTwoFactor()" novalidate class="stack">
            <app-password-field
              [control]="twoFactorForm.controls.currentPassword"
              label="Senha atual"
              autocomplete="current-password"
            />
            <div class="form-actions">
              <button mat-flat-button type="submit" [disabled]="savingTwoFactor()">
                {{ auth.user()?.twoFactor ? 'Desativar' : 'Ativar' }} verificação em duas etapas
              </button>
            </div>
          </form>
        }
      </section>

      <section class="card" aria-labelledby="senha-title">
        <h2 id="senha-title">Senha</h2>
        <p class="muted">Ao trocar a senha, as sessões abertas em outros aparelhos são encerradas.</p>
        @if (passwordMsg()) {
          <p class="alert alert-success" role="status">{{ passwordMsg() }}</p>
        }
        @if (passwordError()) {
          <p class="alert alert-error" role="alert">{{ passwordError() }}</p>
        }
        <form [formGroup]="passwordForm" (ngSubmit)="changePassword()" novalidate class="stack">
          <app-password-field
            [control]="passwordForm.controls.currentPassword"
            label="Senha atual"
            autocomplete="current-password"
          />
          <app-password-field
            [control]="passwordForm.controls.newPassword"
            label="Nova senha"
            autocomplete="new-password"
            hint="Mínimo de 8 caracteres, com letras e números."
          />
          <app-password-field
            [control]="passwordForm.controls.confirmPassword"
            label="Repita a nova senha"
            autocomplete="new-password"
          />
          <div class="form-actions">
            <button mat-flat-button type="submit" [disabled]="savingPassword()">Trocar senha</button>
          </div>
        </form>
      </section>
    </div>
  `,
  styles: `
    .account {
      display: grid;
      gap: 1.5rem;
      max-width: 44rem;
      padding-block: 2rem 3rem;
    }
    h2 {
      margin-top: 0;
    }
  `,
})
export class AccountPage {
  protected readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly nameForm = this.fb.group({
    fullName: [
      this.auth.user()?.fullName ?? '',
      [Validators.required, Validators.minLength(3), Validators.maxLength(200)],
    ],
  });
  protected readonly emailForm = this.fb.group({
    newEmail: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    currentPassword: ['', Validators.required],
  });
  protected readonly passwordForm = this.fb.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: samePassword },
  );

  protected readonly twoFactorForm = this.fb.group({
    currentPassword: ['', Validators.required],
  });

  protected readonly savingTwoFactor = signal(false);
  protected readonly twoFactorMsg = signal('');
  protected readonly twoFactorError = signal('');
  protected readonly savingName = signal(false);
  protected readonly nameMsg = signal('');
  protected readonly nameError = signal('');
  protected readonly savingEmail = signal(false);
  protected readonly emailMsg = signal('');
  protected readonly emailError = signal('');
  protected readonly savingPassword = signal(false);
  protected readonly passwordMsg = signal('');
  protected readonly passwordError = signal('');

  protected saveName(): void {
    if (this.nameForm.invalid) {
      this.nameForm.markAllAsTouched();
      return;
    }
    this.savingName.set(true);
    this.nameMsg.set('');
    this.nameError.set('');
    this.auth.rename(this.nameForm.getRawValue().fullName).subscribe({
      next: (me) => {
        this.nameForm.setValue({ fullName: me.fullName });
        this.nameMsg.set('Nome salvo.');
        this.savingName.set(false);
      },
      error: (e: unknown) => {
        this.nameError.set(applyServerErrors(this.nameForm, e));
        this.savingName.set(false);
      },
    });
  }

  protected changeEmail(): void {
    if (this.emailForm.invalid) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.savingEmail.set(true);
    this.emailMsg.set('');
    this.emailError.set('');
    const { newEmail, currentPassword } = this.emailForm.getRawValue();
    this.auth.requestEmailChange(newEmail, currentPassword).subscribe({
      next: (r) => {
        this.emailMsg.set(r.message);
        this.emailForm.reset();
        this.savingEmail.set(false);
      },
      error: (e: unknown) => {
        this.emailError.set(applyServerErrors(this.emailForm, e));
        this.savingEmail.set(false);
      },
    });
  }

  protected toggleTwoFactor(): void {
    if (this.twoFactorForm.invalid) {
      this.twoFactorForm.markAllAsTouched();
      return;
    }
    this.savingTwoFactor.set(true);
    this.twoFactorMsg.set('');
    this.twoFactorError.set('');
    const enable = !this.auth.user()?.twoFactor;
    this.auth.setTwoFactor(enable, this.twoFactorForm.getRawValue().currentPassword).subscribe({
      next: () => {
        this.twoFactorMsg.set(
          enable
            ? 'Verificação em duas etapas ativada. No próximo acesso, enviaremos um código para o seu e-mail.'
            : 'Verificação em duas etapas desativada.',
        );
        this.twoFactorForm.reset();
        this.savingTwoFactor.set(false);
      },
      error: (e: unknown) => {
        this.twoFactorError.set(applyServerErrors(this.twoFactorForm, e));
        this.savingTwoFactor.set(false);
      },
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    this.savingPassword.set(true);
    this.passwordMsg.set('');
    this.passwordError.set('');
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordMsg.set('Senha trocada. As sessões em outros aparelhos foram encerradas.');
        this.passwordForm.reset();
        this.savingPassword.set(false);
      },
      error: (e: unknown) => {
        this.passwordError.set(applyServerErrors(this.passwordForm, e));
        this.savingPassword.set(false);
      },
    });
  }
}
