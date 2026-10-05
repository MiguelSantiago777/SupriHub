import { Component, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

/** Campo de senha com botão "mostrar senha" (ajuda quem tem pouca familiaridade digital). */
@Component({
  selector: 'app-password-field',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
  ],
  template: `
    <mat-form-field>
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [type]="visible() ? 'text' : 'password'"
        [formControl]="control()"
        [attr.autocomplete]="autocomplete()"
        [attr.aria-describedby]="hint() ? hintId : null"
      />
      <button
        mat-icon-button
        matSuffix
        type="button"
        (click)="visible.set(!visible())"
        [attr.aria-label]="visible() ? 'Esconder senha' : 'Mostrar senha'"
        [attr.aria-pressed]="visible()"
      >
        <mat-icon aria-hidden="true">{{ visible() ? 'visibility_off' : 'visibility' }}</mat-icon>
      </button>
      @if (hint()) {
        <mat-hint [id]="hintId">{{ hint() }}</mat-hint>
      }
      @if (control().hasError('required')) {
        <mat-error>Informe a senha.</mat-error>
      } @else if (control().hasError('pattern') || control().hasError('minlength')) {
        <mat-error>A senha deve ter de 8 a 72 caracteres, com letras e números.</mat-error>
      } @else if (control().hasError('mismatch')) {
        <mat-error>As senhas não são iguais.</mat-error>
      } @else if (control().hasError('server')) {
        <mat-error>{{ control().getError('server') }}</mat-error>
      }
    </mat-form-field>
  `,
})
export class PasswordField {
  private static seq = 0;
  readonly control = input.required<FormControl<string>>();
  readonly label = input('Senha');
  readonly autocomplete = input('current-password');
  readonly hint = input<string>('');
  protected readonly visible = signal(false);
  protected readonly hintId = `password-hint-${++PasswordField.seq}`;
}

export const PASSWORD_PATTERN = /^(?=.*[A-Za-zÀ-ÿ])(?=.*\d).{8,72}$/;
