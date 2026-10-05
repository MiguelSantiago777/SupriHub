import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { AreaTabs } from '../shared/area-tabs';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Application, ReviewApi, STATUS_BADGE } from '../../core/api/supplier.api';
import { formatDate, formatDateTime } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { ApplicationView } from '../supplier/application-view';

type Decision = 'approve' | 'adjust' | 'reject';

const DECISIONS: { value: Decision; label: string; icon: string; hint: string }[] = [
  {
    value: 'approve',
    label: 'Habilitar',
    icon: 'verified',
    hint: 'Documentação conferida. Credenciamento válido por 24 meses.',
  },
  {
    value: 'adjust',
    label: 'Pedir ajuste',
    icon: 'edit_note',
    hint: 'Falta documento ou há algo a corrigir. O fornecedor tem 5 dias úteis.',
  },
  {
    value: 'reject',
    label: 'Inabilitar',
    icon: 'block',
    hint: 'Decisão motivada. O fornecedor pode corrigir e reenviar ou recorrer.',
  },
];

@Component({
  selector: 'app-application-review-page',
  imports: [
    AreaTabs,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ApplicationView,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/comissao/requerimentos">← Requerimentos</a></p>
        @if (app(); as a) {
          <div class="head">
            <div>
              <span class="eyebrow">{{ a.protocol || 'Sem protocolo' }}</span>
              <h1>{{ a.legalName || 'Requerimento' }}</h1>
              <p>
                {{ a.supplierTypeLabel }}
                @if (a.submittedAt) {
                  · enviado em {{ dateTime(a.submittedAt) }}
                }
              </p>
            </div>
            <span class="badge big" [class]="badge[a.status]">{{ a.statusLabel }}</span>
          </div>
        } @else {
          <h1>Análise do requerimento</h1>
        }
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (!app()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let a = app()!;
        <div class="layout">
          <app-application-view [app]="a" (openDocument)="open($event)" />

          <aside class="panel" aria-labelledby="decisao">
            @if (a.status === 'SUBMITTED') {
              <div class="card decide">
                <h2 id="decisao">Decisão da Comissão</h2>
                <p class="due" [class.late]="late()">
                  <mat-icon aria-hidden="true">{{ late() ? 'alarm' : 'event' }}</mat-icon>
                  {{ late() ? 'Prazo vencido em' : 'Decidir até' }}
                  <strong>{{ date(a.analysisDueDate) }}</strong>
                </p>
                <details class="tips">
                  <summary>Antes de decidir, confira</summary>
                  <ul>
                    <li>
                      certidões nos sites oficiais (RFB/PGFN, Caixa/FGTS, TST/CNDT e prefeitura);
                    </li>
                    <li>CEIS, CNEP e CEPIM no Portal da Transparência (Edital 4.2);</li>
                    <li>se o titular da conta bancária é o próprio CNPJ/CPF;</li>
                    <li>se as linhas escolhidas batem com a atividade (CNAE).</li>
                  </ul>
                  <p class="muted">
                    Erros que não mudam a substância podem ser sanados pela Comissão (Edital 4.5).
                  </p>
                </details>

                <div class="options" role="radiogroup" aria-label="Decisão">
                  @for (d of decisions; track d.value) {
                    <button
                      type="button"
                      role="radio"
                      [attr.aria-checked]="decision() === d.value"
                      [class]="'opt ' + d.value"
                      [class.selected]="decision() === d.value"
                      (click)="choose(d.value)"
                    >
                      <mat-icon aria-hidden="true">{{ d.icon }}</mat-icon>
                      <span>
                        <strong>{{ d.label }}</strong>
                        <small>{{ d.hint }}</small>
                      </span>
                    </button>
                  }
                </div>

                @if (decision(); as d) {
                  <mat-form-field class="msg">
                    <mat-label>{{ messageLabel() }}</mat-label>
                    <textarea matInput [formControl]="message" rows="5" maxlength="3000"></textarea>
                    <mat-hint>{{
                      d === 'approve'
                        ? 'Opcional. Fica registrado no andamento.'
                        : 'Vai no e-mail para o fornecedor.'
                    }}</mat-hint>
                    @if (message.hasError('required')) {
                      <mat-error>Escreva a mensagem para o fornecedor.</mat-error>
                    }
                  </mat-form-field>
                  @if (actionError()) {
                    <p class="alert alert-error" role="alert">{{ actionError() }}</p>
                  }
                  <button
                    mat-flat-button
                    type="button"
                    [class]="'confirm ' + d"
                    [disabled]="busy()"
                    (click)="confirm()"
                  >
                    {{ busy() ? 'Registrando…' : confirmLabel() }}
                  </button>
                }
              </div>
            } @else {
              <div class="card info">
                <h2 id="decisao">Situação</h2>
                @switch (a.status) {
                  @case ('ADJUSTMENT_REQUESTED') {
                    <p>
                      Aguardando o fornecedor corrigir até
                      <strong>{{ date(a.adjustmentDueDate) }}</strong
                      >.
                    </p>
                    <blockquote>{{ a.adjustmentMessage }}</blockquote>
                    <p class="muted">
                      Quando ele reenviar, o requerimento volta para "Em análise".
                    </p>
                  }
                  @case ('APPROVED') {
                    <p>
                      Habilitado em {{ dateTime(a.decidedAt) }}. Credenciamento válido até
                      <strong>{{ date(a.accreditationValidUntil) }}</strong
                      >.
                    </p>
                    @if (a.decisionReason) {
                      <blockquote>{{ a.decisionReason }}</blockquote>
                    }
                  }
                  @case ('REJECTED') {
                    <p>Inabilitado em {{ dateTime(a.decidedAt) }}.</p>
                    <blockquote>{{ a.decisionReason }}</blockquote>
                    @if (a.canAppeal) {
                      <p class="muted">
                        O fornecedor pode recorrer até {{ date(a.appealDeadline) }} ou corrigir e
                        reenviar.
                      </p>
                    }
                  }
                  @default {
                    <p>
                      O fornecedor ainda está preenchendo o requerimento. Não há decisão a tomar.
                    </p>
                  }
                }
                @if (a.appeal; as ap) {
                  <p class="appeal-link">
                    <span class="badge badge-warning">Recurso: {{ ap.statusLabel }}</span>
                    <a [routerLink]="['/recursos', ap.id]">Abrir o recurso</a>
                  </p>
                }
              </div>
            }
          </aside>
        </div>
      }
    </div>
  `,
  styles: `
    .back {
      margin: 0 0 0.75rem !important;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-end;
      gap: 1rem;
    }
    .badge.big {
      font-size: 0.95rem;
      padding: 0.3rem 0.9rem;
    }
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
    .layout {
      display: grid;
      gap: 1.5rem;
    }
    @media (min-width: 1100px) {
      .layout {
        grid-template-columns: 1fr 380px;
        align-items: start;
      }
      .panel {
        position: sticky;
        top: 96px;
        max-height: calc(100vh - 112px);
        overflow-y: auto;
        border-radius: var(--sh-radius);
      }
    }
    .panel {
      order: -1;
    }
    @media (min-width: 1100px) {
      .panel {
        order: 0;
      }
    }
    .panel h2 {
      font-size: 1.2rem;
      margin: 0 0 0.75rem;
    }
    .decide {
      border-top: 4px solid var(--sh-primary);
    }
    .due {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0 0 0.75rem;
    }
    .due.late {
      color: var(--sh-error);
      font-weight: 700;
    }
    .tips {
      margin-bottom: 1rem;
      padding: 0.6rem 0.85rem;
      border-radius: var(--sh-radius-sm);
      background: var(--sh-bg);
      border: 1px solid var(--sh-border);
      font-size: 0.92rem;
    }
    .tips summary {
      cursor: pointer;
      font-weight: 700;
    }
    .tips ul {
      padding-left: 1.1rem;
      margin: 0.5rem 0;
    }
    .tips p {
      margin: 0;
    }
    .options {
      display: grid;
      gap: 0.5rem;
    }
    .opt {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem 0.85rem;
      border: 2px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
      background: var(--sh-surface);
      color: var(--sh-text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .opt span {
      display: flex;
      flex-direction: column;
    }
    .opt small {
      color: var(--sh-text-muted);
      font-size: 0.85rem;
      line-height: 1.35;
    }
    .opt mat-icon {
      flex: 0 0 auto;
      margin-top: 2px;
    }
    .opt.approve mat-icon {
      color: var(--sh-green);
    }
    .opt.adjust mat-icon {
      color: var(--sh-amber);
    }
    .opt.reject mat-icon {
      color: var(--sh-error);
    }
    .opt.approve.selected {
      border-color: var(--sh-green);
      background: var(--sh-green-soft);
    }
    .opt.adjust.selected {
      border-color: var(--sh-amber);
      background: var(--sh-amber-soft);
    }
    .opt.reject.selected {
      border-color: var(--sh-error);
      background: var(--sh-error-soft);
    }
    .msg {
      width: 100%;
      margin-top: 1rem;
    }
    .confirm {
      width: 100%;
      margin-top: 0.5rem;
    }
    .confirm.approve {
      --mat-button-filled-container-color: var(--sh-green);
    }
    .confirm.adjust {
      --mat-button-filled-container-color: var(--sh-amber);
    }
    .confirm.reject {
      --mat-button-filled-container-color: var(--sh-error);
    }
    .appeal-link {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
    }
    blockquote {
      margin: 0.5rem 0;
      padding: 0.6rem 0.85rem;
      border-left: 4px solid var(--sh-border-strong);
      background: var(--sh-bg);
      white-space: pre-line;
    }
  `,
})
export class ApplicationReviewPage {
  private readonly api = inject(ReviewApi);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly decisions = DECISIONS;
  protected readonly badge = STATUS_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly app = signal<Application | null>(null);
  protected readonly error = signal('');
  protected readonly decision = signal<Decision | null>(null);
  protected readonly busy = signal(false);
  protected readonly actionError = signal('');
  protected readonly message = new FormControl('', { nonNullable: true });

  protected readonly late = computed(() => {
    const due = this.app()?.analysisDueDate;
    return !!due && due < new Date().toISOString().slice(0, 10);
  });

  protected readonly messageLabel = computed(() =>
    this.decision() === 'approve'
      ? 'Observação (opcional)'
      : this.decision() === 'adjust'
        ? 'O que o fornecedor precisa corrigir?'
        : 'Motivo da inabilitação',
  );

  protected readonly confirmLabel = computed(() =>
    this.decision() === 'approve'
      ? 'Confirmar habilitação'
      : this.decision() === 'adjust'
        ? 'Enviar pedido de ajuste'
        : 'Confirmar inabilitação',
  );

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    this.error.set('');
    this.api.get(id).subscribe({
      next: (a) => this.app.set(a),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }

  protected choose(d: Decision): void {
    this.decision.set(d);
    this.actionError.set('');
    this.message.setValidators(d === 'approve' ? [] : [Validators.required]);
    this.message.updateValueAndValidity();
  }

  protected confirm(): void {
    const app = this.app();
    const d = this.decision();
    if (!app || !d) {
      return;
    }
    if (this.message.invalid) {
      this.message.markAsTouched();
      return;
    }
    const text = this.message.value.trim();
    const call: Observable<Application> =
      d === 'approve'
        ? this.api.approve(app.id, text, app.version)
        : d === 'adjust'
          ? this.api.requestAdjustment(app.id, text, app.version)
          : this.api.reject(app.id, text, app.version);
    this.busy.set(true);
    this.actionError.set('');
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.app.set(updated);
        this.decision.set(null);
        this.message.reset();
        this.snack.open(
          d === 'approve'
            ? 'Fornecedor habilitado. Ele foi avisado por e-mail.'
            : d === 'adjust'
              ? 'Pedido de ajuste enviado ao fornecedor.'
              : 'Inabilitação registrada. O fornecedor foi avisado.',
          'OK',
          { duration: 5000 },
        );
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.actionError.set(problemMessage(e));
      },
    });
  }

  protected open(documentId: number): void {
    const app = this.app();
    if (app) {
      this.api.openDocument(app.id, documentId);
    }
  }
}
