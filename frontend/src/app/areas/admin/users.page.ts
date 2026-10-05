import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../../core/api/catalog.api';
import { applyServerErrors } from '../../core/http/problem';
import { AppRole, ROLE_LABELS, UserSummary } from '../../core/models';

const INTERNAL_ROLES: AppRole[] = [
  'COMMISSION',
  'DEMAND_MANAGER',
  'FINANCE',
  'INTERNAL_CONTROL',
  'BOARD',
  'ADMIN',
];

@Component({
  selector: 'app-users-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <div class="container">
      <p><a routerLink="/admin">← Administração</a></p>
      <h1>Usuários</h1>

      <section class="card" aria-labelledby="convidar">
        <h2 id="convidar">Convidar pessoa da equipe</h2>
        <p class="muted">A pessoa recebe um e-mail com link para criar a própria senha.</p>
        @if (inviteError()) {
          <p class="alert alert-error" role="alert">{{ inviteError() }}</p>
        }
        <form [formGroup]="invite" (ngSubmit)="sendInvite()" novalidate>
          <div class="row">
            <mat-form-field>
              <mat-label>Nome completo</mat-label>
              <input matInput formControlName="fullName" autocomplete="off" />
              @if (invite.controls.fullName.hasError('required')) {
                <mat-error>Informe o nome.</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>E-mail</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="off" />
              @if (invite.controls.email.hasError('required')) {
                <mat-error>Informe o e-mail.</mat-error>
              } @else if (invite.controls.email.hasError('email')) {
                <mat-error>E-mail inválido.</mat-error>
              }
            </mat-form-field>
          </div>
          <fieldset>
            <legend>Perfis</legend>
            @for (role of internalRoles; track role) {
              <mat-checkbox
                [checked]="selected().includes(role)"
                (change)="toggle(role, $event.checked)"
              >
                {{ roleLabels[role] }}
              </mat-checkbox>
            }
          </fieldset>
          <div class="form-actions">
            <button mat-flat-button type="submit">Enviar convite</button>
          </div>
        </form>
      </section>

      <section class="card list" aria-labelledby="lista">
        <h2 id="lista">Pessoas cadastradas</h2>
        <form (submit)="$event.preventDefault(); load()" class="search">
          <mat-form-field>
            <mat-label>Buscar por nome ou e-mail</mat-label>
            <input matInput [value]="query()" (input)="query.set($any($event.target).value)" />
          </mat-form-field>
          <button mat-stroked-button type="submit">Buscar</button>
        </form>
        <div class="table-wrap">
          <table class="data">
            <thead>
              <tr>
                <th scope="col">Nome</th>
                <th scope="col">Perfis</th>
                <th scope="col">Situação</th>
                <th scope="col"><span class="visually-hidden">Ações</span></th>
              </tr>
            </thead>
            <tbody>
              @for (u of users(); track u.id) {
                <tr>
                  <td>
                    {{ u.fullName }}<br /><span class="muted">{{ u.email }}</span>
                  </td>
                  <td>{{ labels(u.roles) }}</td>
                  <td>
                    {{ u.enabled ? 'Ativo' : 'Desativado' }}
                    @if (!u.emailVerified) {
                      <br /><span class="muted">e-mail não confirmado</span>
                    }
                  </td>
                  <td>
                    <button mat-stroked-button type="button" (click)="toggleEnabled(u)">
                      {{ u.enabled ? 'Desativar' : 'Reativar' }}
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4">Nenhum usuário encontrado.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: `
    .row {
      display: grid;
      gap: 0 1rem;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    }
    fieldset {
      border: 1px solid var(--sh-border);
      border-radius: 6px;
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 1rem;
    }
    .list {
      margin-top: 1rem;
    }
    .search {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
      flex-wrap: wrap;
    }
    .search mat-form-field {
      flex: 1 1 260px;
    }
    .table-wrap {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 0.6rem 0.5rem;
      border-bottom: 1px solid #e0e0e0;
      vertical-align: top;
    }
  `,
})
export class UsersPage {
  private readonly api = inject(AdminApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly internalRoles = INTERNAL_ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly users = signal<UserSummary[]>([]);
  protected readonly query = signal('');
  protected readonly selected = signal<AppRole[]>(['COMMISSION']);
  protected readonly inviteError = signal('');

  protected readonly invite = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
  });

  constructor() {
    this.load();
  }

  protected labels(roles: AppRole[]): string {
    return roles.map((r) => ROLE_LABELS[r]).join(', ');
  }

  protected toggle(role: AppRole, checked: boolean): void {
    this.selected.update((list) => (checked ? [...list, role] : list.filter((r) => r !== role)));
  }

  protected sendInvite(): void {
    if (this.invite.invalid || this.selected().length === 0) {
      this.invite.markAllAsTouched();
      if (this.selected().length === 0) this.inviteError.set('Selecione ao menos um perfil.');
      return;
    }
    this.inviteError.set('');
    const { email, fullName } = this.invite.getRawValue();
    this.api.inviteUser(email.trim(), fullName.trim(), this.selected()).subscribe({
      next: () => {
        this.snackBar.open('Convite enviado.', 'Fechar');
        this.invite.reset();
        this.load();
      },
      error: (e: unknown) => this.inviteError.set(applyServerErrors(this.invite, e)),
    });
  }

  protected toggleEnabled(u: UserSummary): void {
    this.api.updateUser(u, { enabled: !u.enabled }).subscribe({
      next: () => this.load(),
      error: (e: unknown) => this.snackBar.open(applyServerErrors(this.invite, e), 'Fechar'),
    });
  }

  protected load(): void {
    this.api.users(this.query().trim(), null).subscribe((page) => this.users.set(page.content));
  }
}
