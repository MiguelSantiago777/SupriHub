import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { APPEAL_BADGE, AppealApi, AppealDetail } from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatDateTime, formatSize } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { ApplicationView } from '../supplier/application-view';

type Action = 'reconsider' | 'forward' | 'grant' | 'deny';

interface Option {
  value: Action;
  label: string;
  icon: string;
  hint: string;
  tone: 'ok' | 'warn' | 'no';
}

const COMMISSION_OPTIONS: Option[] = [
  {
    value: 'reconsider',
    label: 'Reconsiderar',
    icon: 'verified',
    hint: 'A Comissão revê a decisão: o fornecedor é habilitado por 24 meses.',
    tone: 'ok',
  },
  {
    value: 'forward',
    label: 'Encaminhar à Diretoria',
    icon: 'forward',
    hint: 'Mantém a inabilitação e envia o recurso, com a motivação, para a Diretoria decidir.',
    tone: 'warn',
  },
];

const BOARD_OPTIONS: Option[] = [
  {
    value: 'grant',
    label: 'Dar provimento',
    icon: 'verified',
    hint: 'Recurso aceito: o fornecedor é habilitado por 24 meses.',
    tone: 'ok',
  },
  {
    value: 'deny',
    label: 'Negar provimento',
    icon: 'block',
    hint: 'A inabilitação é mantida. O fornecedor pode corrigir e reenviar o requerimento.',
    tone: 'no',
  },
];

@Component({
  selector: 'app-appeal-detail-page',
  imports: [
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
        <p class="back"><a routerLink="/recursos">← Recursos</a></p>
        @if (detail(); as d) {
          <div class="head">
            <div>
              <span class="eyebrow">Recurso · {{ d.application.protocol }}</span>
              <h1>{{ d.application.legalName }}</h1>
              <p>
                {{ d.application.supplierTypeLabel }} · recurso em {{ dateTime(d.appeal.filedAt) }}
              </p>
            </div>
            <span class="badge big" [class]="badge[d.appeal.status]">{{
              d.appeal.statusLabel
            }}</span>
          </div>
        } @else {
          <h1>Recurso</h1>
        }
      </div>
    </header>

    <div class="container page">
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (!detail()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let d = detail()!;
        @let ap = d.appeal;
        <div class="layout">
          <div class="content">
            <section class="card appeal" aria-labelledby="t-recurso">
              <h2 id="t-recurso">
                <mat-icon aria-hidden="true">gavel</mat-icon> Recurso (Anexo X)
              </h2>
              <div class="decision">
                <span class="muted">Decisão recorrida</span>
                <blockquote>{{ d.application.decisionReason }}</blockquote>
              </div>
              <h3>Razões</h3>
              <p class="pre">{{ ap.reasons }}</p>
              <h3>Pedido</h3>
              <p class="pre">{{ ap.request }}</p>
              @if (ap.attachment) {
                <button mat-stroked-button type="button" (click)="openAttachment()">
                  <mat-icon aria-hidden="true">attach_file</mat-icon>
                  {{ ap.attachment.filename }} · {{ size(ap.attachment.sizeBytes) }}
                </button>
              }

              <h3>Andamento</h3>
              <ol class="steps">
                <li class="done">
                  <strong>Apresentado</strong>
                  <span class="muted"
                    >{{ dateTime(ap.filedAt) }} · Comissão decide até
                    {{ date(ap.reconsiderationDue) }}</span
                  >
                </li>
                @if (ap.commissionDecidedAt) {
                  <li class="done" [class.ok]="ap.status === 'GRANTED' && !ap.boardDecidedAt">
                    <strong>{{
                      ap.boardDue ? 'Encaminhado à Diretoria' : 'Reconsiderado pela Comissão'
                    }}</strong>
                    <span class="muted">
                      {{ dateTime(ap.commissionDecidedAt) }}
                      @if (ap.boardDue) {
                        · Diretoria decide até {{ date(ap.boardDue) }}
                      }
                    </span>
                    <blockquote>{{ ap.commissionReason }}</blockquote>
                  </li>
                }
                @if (ap.boardDecidedAt) {
                  <li
                    class="done"
                    [class.ok]="ap.status === 'GRANTED'"
                    [class.no]="ap.status === 'DENIED'"
                  >
                    <strong>{{
                      ap.status === 'GRANTED' ? 'Provido pela Diretoria' : 'Negado pela Diretoria'
                    }}</strong>
                    <span class="muted">{{ dateTime(ap.boardDecidedAt) }}</span>
                    <blockquote>{{ ap.boardReason }}</blockquote>
                  </li>
                }
              </ol>
            </section>

            <app-application-view [app]="d.application" (openDocument)="openDocument($event)" />
          </div>

          <aside class="panel" aria-labelledby="t-decisao">
            @if (options().length) {
              <div class="card decide">
                <h2 id="t-decisao">
                  {{ ap.status === 'FILED' ? 'Decisão da Comissão' : 'Decisão da Diretoria' }}
                </h2>
                <p class="due" [class.late]="late()">
                  <mat-icon aria-hidden="true">{{ late() ? 'alarm' : 'event' }}</mat-icon>
                  {{ late() ? 'Prazo vencido em' : 'Decidir até' }}
                  <strong>{{ date(dueDate()) }}</strong>
                </p>
                <div class="options" role="radiogroup" aria-label="Decisão">
                  @for (o of options(); track o.value) {
                    <button
                      type="button"
                      role="radio"
                      [attr.aria-checked]="action() === o.value"
                      [class]="'opt ' + o.tone"
                      [class.selected]="action() === o.value"
                      (click)="choose(o.value)"
                    >
                      <mat-icon aria-hidden="true">{{ o.icon }}</mat-icon>
                      <span>
                        <strong>{{ o.label }}</strong>
                        <small>{{ o.hint }}</small>
                      </span>
                    </button>
                  }
                </div>
                @if (action()) {
                  <mat-form-field class="msg">
                    <mat-label>Motivação</mat-label>
                    <textarea matInput [formControl]="reason" rows="5" maxlength="3000"></textarea>
                    <mat-hint>Obrigatória. O fornecedor recebe por e-mail.</mat-hint>
                    @if (reason.hasError('required')) {
                      <mat-error>Escreva a motivação.</mat-error>
                    }
                  </mat-form-field>
                  @if (actionError()) {
                    <p class="alert alert-error" role="alert">{{ actionError() }}</p>
                  }
                  <button
                    mat-flat-button
                    type="button"
                    [class]="'confirm ' + selectedTone()"
                    [disabled]="busy()"
                    (click)="confirm()"
                  >
                    {{ busy() ? 'Registrando…' : confirmLabel() }}
                  </button>
                }
              </div>
            } @else {
              <div class="card info">
                <h2 id="t-decisao">Situação</h2>
                @switch (ap.status) {
                  @case ('FILED') {
                    <p>
                      Aguardando a Comissão até <strong>{{ date(ap.reconsiderationDue) }}</strong
                      >.
                    </p>
                  }
                  @case ('FORWARDED') {
                    <p>
                      Aguardando a Diretoria até <strong>{{ date(ap.boardDue) }}</strong
                      >.
                    </p>
                  }
                  @case ('GRANTED') {
                    <p>
                      Recurso provido. Fornecedor habilitado até
                      <strong>{{ date(d.application.accreditationValidUntil) }}</strong
                      >.
                    </p>
                  }
                  @case ('DENIED') {
                    <p>Recurso negado. A inabilitação foi mantida.</p>
                  }
                }
                @if (auth.hasRole('COMMISSION', 'ADMIN')) {
                  <p>
                    <a [routerLink]="['/comissao/requerimentos', d.application.id]"
                      >Abrir o requerimento</a
                    >
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
    .content {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .panel {
      order: -1;
    }
    @media (min-width: 1100px) {
      .layout {
        grid-template-columns: 1fr 380px;
        align-items: start;
      }
      .panel {
        order: 0;
        position: sticky;
        top: 96px;
        max-height: calc(100vh - 112px);
        overflow-y: auto;
        border-radius: var(--sh-radius);
      }
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.2rem;
      margin: 0 0 0.75rem;
    }
    h2 mat-icon {
      color: var(--sh-amber);
    }
    h3 {
      font-size: 1rem;
      margin: 1.25rem 0 0.35rem;
    }
    .pre {
      white-space: pre-line;
      margin: 0 0 0.5rem;
    }
    blockquote {
      margin: 0.4rem 0 0;
      padding: 0.6rem 0.85rem;
      border-left: 4px solid var(--sh-border-strong);
      background: var(--sh-bg);
      white-space: pre-line;
    }
    .decision blockquote {
      border-left-color: var(--sh-error);
      background: var(--sh-error-soft);
    }
    .steps {
      list-style: none;
      margin: 0.5rem 0 0;
      padding: 0 0 0 1.25rem;
      border-left: 2px solid var(--sh-border);
      display: grid;
      gap: 0.85rem;
    }
    .steps li {
      position: relative;
      display: flex;
      flex-direction: column;
    }
    .steps li::before {
      content: '';
      position: absolute;
      left: calc(-1.25rem - 7px);
      top: 0.35rem;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: var(--sh-primary);
    }
    .steps li.ok::before {
      background: var(--sh-green);
    }
    .steps li.no::before {
      background: var(--sh-error);
    }
    .decide {
      border-top: 4px solid var(--sh-amber);
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
    .opt.ok mat-icon {
      color: var(--sh-green);
    }
    .opt.warn mat-icon {
      color: var(--sh-amber);
    }
    .opt.no mat-icon {
      color: var(--sh-error);
    }
    .opt.ok.selected {
      border-color: var(--sh-green);
      background: var(--sh-green-soft);
    }
    .opt.warn.selected {
      border-color: var(--sh-amber);
      background: var(--sh-amber-soft);
    }
    .opt.no.selected {
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
    .confirm.ok {
      --mat-button-filled-container-color: var(--sh-green);
    }
    .confirm.warn {
      --mat-button-filled-container-color: var(--sh-amber);
    }
    .confirm.no {
      --mat-button-filled-container-color: var(--sh-error);
    }
  `,
})
export class AppealDetailPage {
  private readonly api = inject(AppealApi);
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly badge = APPEAL_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly size = formatSize;

  protected readonly detail = signal<AppealDetail | null>(null);
  protected readonly error = signal('');
  protected readonly action = signal<Action | null>(null);
  protected readonly busy = signal(false);
  protected readonly actionError = signal('');
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  /** Opções que o perfil logado pode usar na etapa atual. */
  protected readonly options = computed<Option[]>(() => {
    const status = this.detail()?.appeal.status;
    if (status === 'FILED' && this.auth.hasRole('COMMISSION', 'ADMIN')) {
      return COMMISSION_OPTIONS;
    }
    if (status === 'FORWARDED' && this.auth.hasRole('BOARD', 'ADMIN')) {
      return BOARD_OPTIONS;
    }
    return [];
  });

  protected readonly dueDate = computed(() => {
    const ap = this.detail()?.appeal;
    return ap?.status === 'FILED' ? ap.reconsiderationDue : (ap?.boardDue ?? null);
  });

  protected readonly late = computed(() => {
    const due = this.dueDate();
    return !!due && due < new Date().toISOString().slice(0, 10);
  });

  protected readonly selectedTone = computed(
    () =>
      [...COMMISSION_OPTIONS, ...BOARD_OPTIONS].find((o) => o.value === this.action())?.tone ??
      'ok',
  );

  protected readonly confirmLabel = computed(() => {
    switch (this.action()) {
      case 'reconsider':
        return 'Confirmar reconsideração';
      case 'forward':
        return 'Encaminhar à Diretoria';
      case 'grant':
        return 'Confirmar provimento';
      default:
        return 'Confirmar: negar provimento';
    }
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    this.error.set('');
    this.api.get(id).subscribe({
      next: (d) => this.detail.set(d),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }

  protected choose(a: Action): void {
    this.action.set(a);
    this.actionError.set('');
  }

  protected confirm(): void {
    const d = this.detail();
    const a = this.action();
    if (!d || !a) {
      return;
    }
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    const text = this.reason.value.trim();
    const { id, version } = d.appeal;
    const call: Observable<AppealDetail> =
      a === 'reconsider'
        ? this.api.reconsider(id, text, version)
        : a === 'forward'
          ? this.api.forward(id, text, version)
          : this.api.boardDecision(id, a === 'grant', text, version);
    this.busy.set(true);
    this.actionError.set('');
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.detail.set(updated);
        this.action.set(null);
        this.reason.reset();
        this.snack.open(
          a === 'forward'
            ? 'Recurso encaminhado à Diretoria.'
            : a === 'deny'
              ? 'Recurso negado. O fornecedor foi avisado.'
              : 'Recurso provido: fornecedor habilitado e avisado por e-mail.',
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

  protected openAttachment(): void {
    const d = this.detail();
    if (d) {
      this.api.openAttachment(d.appeal.id);
    }
  }

  protected openDocument(documentId: number): void {
    const d = this.detail();
    if (d) {
      this.api.openDocument(d.appeal.id, documentId);
    }
  }
}
