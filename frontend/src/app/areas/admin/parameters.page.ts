import { DatePipe, KeyValuePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../../core/api/catalog.api';
import { applyServerErrors } from '../../core/http/problem';
import { Parameter, ParameterHistory } from '../../core/models';

const CATEGORY_LABELS: Record<string, string> = {
  FAIXAS: 'Faixas de valor',
  JULGAMENTO: 'Julgamento e tratamento favorecido',
  PROPOSTAS: 'Propostas',
  CREDENCIAMENTO: 'Credenciamento e prazos',
  CALENDARIO: 'Calendário',
  METAS: 'Metas anuais',
};

@Component({
  selector: 'app-parameters-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    KeyValuePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <div class="container">
      <p><a routerLink="/admin">← Administração</a></p>
      <h1>Parâmetros</h1>
      <p class="muted">
        Valores usados pelas regras do sistema. Percentuais em fração (0.10 = 10%). Toda alteração
        exige motivo e fica registrada no histórico e na auditoria.
      </p>

      @for (group of grouped() | keyvalue; track group.key) {
        <section class="card group">
          <h2>{{ categoryLabel(group.key) }}</h2>
          <div class="table-wrap">
            <table>
              <caption class="visually-hidden">
                {{
                  categoryLabel(group.key)
                }}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Parâmetro</th>
                  <th scope="col">Valor</th>
                  <th scope="col">Fonte</th>
                  <th scope="col"><span class="visually-hidden">Ações</span></th>
                </tr>
              </thead>
              <tbody>
                @for (p of group.value; track p.key) {
                  <tr>
                    <td>
                      {{ p.description }}<br /><code class="muted">{{ p.key }}</code>
                    </td>
                    <td>
                      <strong>{{ p.value }}</strong>
                    </td>
                    <td class="muted">{{ p.source }}</td>
                    <td>
                      <button
                        mat-stroked-button
                        type="button"
                        (click)="edit(p)"
                        [attr.aria-label]="'Alterar ' + p.description"
                      >
                        Alterar
                      </button>
                    </td>
                  </tr>
                  @if (editing()?.key === p.key) {
                    <tr class="edit-row">
                      <td colspan="4">
                        <form [formGroup]="form" (ngSubmit)="save(p)" class="edit-form" novalidate>
                          <mat-form-field>
                            <mat-label>Novo valor</mat-label>
                            <input matInput formControlName="value" />
                            @if (form.controls.value.hasError('server')) {
                              <mat-error>{{ form.controls.value.getError('server') }}</mat-error>
                            }
                          </mat-form-field>
                          <mat-form-field>
                            <mat-label>Motivo da alteração</mat-label>
                            <input
                              matInput
                              formControlName="reason"
                              placeholder="Ex.: Decreto nº 12.900/2026"
                            />
                            @if (form.controls.reason.hasError('required')) {
                              <mat-error>Informe o motivo.</mat-error>
                            }
                          </mat-form-field>
                          @if (error()) {
                            <p class="alert alert-error" role="alert">{{ error() }}</p>
                          }
                          <div class="form-actions">
                            <button mat-flat-button type="submit">Salvar</button>
                            <button mat-button type="button" (click)="editing.set(null)">
                              Cancelar
                            </button>
                          </div>
                          @if (history().length) {
                            <h3>Histórico</h3>
                            <ul class="history">
                              @for (h of history(); track h.changedAt) {
                                <li>
                                  {{ h.changedAt | date: 'dd/MM/yyyy HH:mm' }}: {{ h.oldValue }} →
                                  <strong>{{ h.newValue }}</strong>
                                  @if (h.reason) {
                                    — {{ h.reason }}
                                  }
                                </li>
                              }
                            </ul>
                          }
                        </form>
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .group {
      margin-bottom: 1rem;
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
    code {
      font-size: 0.8rem;
    }
    .edit-row td {
      background: var(--sh-surface-alt);
    }
    .edit-form {
      max-width: 640px;
    }
    .history {
      font-size: 0.9rem;
    }
  `,
})
export class ParametersPage {
  private readonly api = inject(AdminApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly parameters = signal<Parameter[]>([]);
  protected readonly editing = signal<Parameter | null>(null);
  protected readonly history = signal<ParameterHistory[]>([]);
  protected readonly error = signal('');
  protected readonly grouped = computed(() => {
    const map: Record<string, Parameter[]> = {};
    for (const p of this.parameters()) (map[p.category] ??= []).push(p);
    return map;
  });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    value: ['', Validators.required],
    reason: ['', Validators.required],
  });

  constructor() {
    this.load();
  }

  protected categoryLabel(key: string): string {
    return CATEGORY_LABELS[key] ?? key;
  }

  protected edit(p: Parameter): void {
    this.editing.set(p);
    this.error.set('');
    this.form.reset({ value: p.value, reason: '' });
    this.history.set([]);
    this.api.parameterHistory(p.key).subscribe((h) => this.history.set(h));
  }

  protected save(p: Parameter): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { value, reason } = this.form.getRawValue();
    this.api.updateParameter(p.key, value, reason).subscribe({
      next: () => {
        this.snackBar.open('Parâmetro atualizado.', 'Fechar');
        this.editing.set(null);
        this.load();
      },
      error: (e: unknown) => this.error.set(applyServerErrors(this.form, e)),
    });
  }

  private load(): void {
    this.api.parameters().subscribe((list) => this.parameters.set(list));
  }
}
