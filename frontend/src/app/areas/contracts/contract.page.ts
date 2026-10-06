import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import {
  ContractApi,
  FileKind,
  ORDER_BADGE,
  OrderDetail,
  OrderKind,
  PaymentMethod,
  ReceiptInput,
  ReceiptKind,
  SCORE_CRITERIA,
} from '../../core/api/contract.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatMoney } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';
import { DossierPanel } from './dossier-panel';
import { OrderView } from './order-view';

function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Ordem de Fornecimento/Serviço para a equipe (Manual 4.2, passos 7 a 11). */
@Component({
  selector: 'app-contract-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatSelectModule,
    AreaTabs,
    OrderView,
    DossierPanel,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/contratos">← Ordens de fornecimento</a></p>
        @if (o(); as d) {
          <div class="head">
            <div>
              <span class="eyebrow"
                >{{ d.number || d.kindLabel + ' em preparação' }} · {{ d.tender.number }} ·
                {{ d.tender.bandLabel }}</span
              >
              <h1>{{ d.tender.title }}</h1>
              <p>
                {{ d.supplier?.name }} · <strong>{{ money(d.totalValue) }}</strong>
              </p>
            </div>
            <span class="badge big" [class]="badge[d.status]">{{ d.statusLabel }}</span>
          </div>
        } @else {
          <h1>Ordem de fornecimento</h1>
        }
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      @if (loadError()) {
        <p class="alert alert-error" role="alert">{{ loadError() }}</p>
      } @else if (!o()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let d = o()!;
        @if (d.supplier && !d.supplier.regular && !closed()) {
          <p class="alert alert-warning">
            <strong>Fornecedor sem credenciamento vigente.</strong> Não é possível emitir nem pagar
            até a regularização (Edital 4.4, 9.1 e 10.3).
          </p>
        }
        @if (error()) {
          <div class="alert alert-error" role="alert">
            <p>
              <strong>{{ error() }}</strong>
            </p>
            @if (issues().length) {
              <ul>
                @for (i of issues(); track i.field) {
                  <li>{{ i.message }}</li>
                }
              </ul>
            }
          </div>
        }

        <div class="layout">
          <div class="actions">
            <!-- Preparar e emitir ------------------------------------------------ -->
            @if ((d.status === 'DRAFT' || d.status === 'EXPIRED') && canPrepare()) {
              <section class="card" aria-labelledby="c-prep">
                <h2 id="c-prep">
                  <mat-icon aria-hidden="true">edit_document</mat-icon> Preparar a ordem
                </h2>
                @if (d.status === 'EXPIRED') {
                  <p class="alert alert-warning">
                    O fornecedor não assinou até {{ date(d.signatureDueDate) }}. Reemita com novo
                    prazo ou cancele e convoque o próximo (Edital 9.1).
                  </p>
                }
                @if (d.acceptanceBased) {
                  <p class="muted small">
                    Faixa 1: ao emitir, o aceite eletrônico do rodízio vale como assinatura (Edital
                    9.2).
                  </p>
                }
                <div class="form">
                  <mat-radio-group [(ngModel)]="kind" aria-label="Tipo da ordem" class="radios">
                    <mat-radio-button value="SUPPLY">Fornecimento (OF)</mat-radio-button>
                    <mat-radio-button value="SERVICE">Serviço (OS)</mat-radio-button>
                  </mat-radio-group>
                  <mat-form-field>
                    <mat-label>Projeto / parceria / rubrica</mat-label>
                    <input matInput [(ngModel)]="project" maxlength="300" />
                  </mat-form-field>
                  <mat-form-field>
                    <mat-label>Objeto</mat-label>
                    <textarea
                      matInput
                      rows="3"
                      [(ngModel)]="objectDescription"
                      maxlength="3000"
                    ></textarea>
                  </mat-form-field>
                  <div class="two">
                    <mat-form-field>
                      <mat-label>Data e horário</mat-label>
                      <input matInput [(ngModel)]="schedule" maxlength="300" />
                    </mat-form-field>
                    <mat-form-field>
                      <mat-label>Local</mat-label>
                      <input matInput [(ngModel)]="location" maxlength="300" />
                    </mat-form-field>
                  </div>
                  <div class="three">
                    <mat-form-field>
                      <mat-label>Pagar em até (dias)</mat-label>
                      <input matInput type="number" min="1" max="30" [(ngModel)]="paymentDays" />
                    </mat-form-field>
                    <mat-form-field>
                      <mat-label>Sinal (R$, opcional)</mat-label>
                      <input
                        matInput
                        type="number"
                        min="0"
                        step="0.01"
                        [(ngModel)]="upfrontValue"
                      />
                      <mat-hint>Até 30% — eventos e alimentação</mat-hint>
                    </mat-form-field>
                    <mat-form-field>
                      <mat-label>Multa por inexecução (%)</mat-label>
                      <input matInput type="number" min="2" max="10" [(ngModel)]="penaltyPct" />
                      <mat-hint>2% a 10%, se aplicável</mat-hint>
                    </mat-form-field>
                  </div>
                  <mat-form-field>
                    <mat-label>Observações</mat-label>
                    <input matInput [(ngModel)]="notes" maxlength="2000" />
                  </mat-form-field>
                </div>
                <div class="btns">
                  <button mat-stroked-button type="button" [disabled]="busy()" (click)="save()">
                    Salvar
                  </button>
                  <button
                    mat-flat-button
                    type="button"
                    [disabled]="busy() || !d.supplier?.regular"
                    (click)="issue()"
                  >
                    <mat-icon aria-hidden="true">send</mat-icon>
                    {{ d.status === 'EXPIRED' ? 'Reemitir' : 'Emitir a ordem' }}
                  </button>
                </div>
              </section>
            }

            @if (d.status === 'ISSUED') {
              <p class="alert alert-info">
                Aguardando a assinatura do fornecedor até
                <strong>{{ date(d.signatureDueDate) }}</strong
                >.
              </p>
            }

            <!-- Recebimento ----------------------------------------------------- -->
            @if (
              (d.status === 'SIGNED' || d.status === 'RECEIVED_WITH_RESERVATIONS') && canPrepare()
            ) {
              <section class="card" aria-labelledby="c-rec">
                <h2 id="c-rec">
                  <mat-icon aria-hidden="true">fact_check</mat-icon> Termo de Recebimento e
                  Avaliação
                </h2>
                <div class="form">
                  <mat-radio-group
                    [(ngModel)]="receiptKind"
                    aria-label="Recebimento"
                    class="radios col"
                  >
                    <mat-radio-button value="DEFINITIVE"
                      >Definitivo — conforme a ordem</mat-radio-button
                    >
                    <mat-radio-button value="WITH_RESERVATIONS"
                      >Com ressalvas — correções pendentes</mat-radio-button
                    >
                    <mat-radio-button value="REJECTED">Recusado</mat-radio-button>
                  </mat-radio-group>
                  <mat-form-field>
                    <mat-label>Data da execução/entrega</mat-label>
                    <input matInput type="date" [(ngModel)]="executionDate" [max]="todayIso" />
                  </mat-form-field>
                  @if (receiptKind === 'WITH_RESERVATIONS') {
                    <div class="two">
                      <mat-form-field>
                        <mat-label>Correções pedidas</mat-label>
                        <input matInput [(ngModel)]="reservations" maxlength="2000" />
                      </mat-form-field>
                      <mat-form-field>
                        <mat-label>Prazo (dias)</mat-label>
                        <input matInput type="number" min="1" [(ngModel)]="correctionDays" />
                      </mat-form-field>
                    </div>
                  }
                  @if (receiptKind === 'REJECTED') {
                    <mat-form-field>
                      <mat-label>Motivo da recusa</mat-label>
                      <input matInput [(ngModel)]="rejectReason" maxlength="2000" />
                    </mat-form-field>
                  }
                  <fieldset class="scores">
                    <legend>
                      Notas (5 excelente · 4 bom · 3 regular · 2 insuficiente · 1 inaceitável)
                    </legend>
                    @for (c of criteria; track c.key) {
                      <mat-form-field>
                        <mat-label>{{ c.label }}</mat-label>
                        <mat-select [(ngModel)]="scores[c.key]">
                          @for (n of [5, 4, 3, 2, 1]; track n) {
                            <mat-option [value]="n">{{ n }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>
                    }
                    <p class="avg">
                      Média: <strong>{{ average() }}</strong>
                    </p>
                  </fieldset>
                  <mat-form-field>
                    <mat-label>Evidências (fotos, lista de presença, relatório…)</mat-label>
                    <input matInput [(ngModel)]="evidenceKinds" maxlength="200" />
                  </mat-form-field>
                  <mat-form-field>
                    <mat-label>Observações</mat-label>
                    <textarea
                      matInput
                      rows="2"
                      [(ngModel)]="receiptNotes"
                      maxlength="2000"
                    ></textarea>
                  </mat-form-field>
                </div>
                <div class="btns">
                  <label class="file-btn">
                    <input
                      type="file"
                      accept=".pdf,.jpg,.jpeg,.png"
                      (change)="upload($event, 'EVIDENCE')"
                    />
                    <span mat-stroked-button>Anexar evidência</span>
                  </label>
                  <button mat-flat-button type="button" [disabled]="busy()" (click)="receive()">
                    Registrar o termo
                  </button>
                </div>
              </section>
            }

            <!-- Nota fiscal (Financeiro registra, se o fornecedor entregou por fora) -->
            @if (d.status === 'RECEIVED' && canFinance()) {
              <section class="card" aria-labelledby="c-nf">
                <h2 id="c-nf"><mat-icon aria-hidden="true">receipt</mat-icon> Nota fiscal / RPA</h2>
                <p class="muted small">
                  O fornecedor envia pela área dele. Se entregou por e-mail ou em papel, registre
                  aqui.
                </p>
                <div class="three">
                  <mat-form-field>
                    <mat-label>Número</mat-label>
                    <input matInput [(ngModel)]="invoiceNumber" maxlength="60" />
                  </mat-form-field>
                  <mat-form-field>
                    <mat-label>Emissão</mat-label>
                    <input matInput type="date" [(ngModel)]="invoiceDate" [max]="todayIso" />
                  </mat-form-field>
                  <mat-form-field>
                    <mat-label>Valor (R$)</mat-label>
                    <input matInput type="number" step="0.01" [(ngModel)]="invoiceValue" />
                  </mat-form-field>
                </div>
                <div class="btns">
                  <label class="file-btn">
                    <input type="file" accept=".pdf,.jpg,.jpeg,.png" (change)="pick($event)" />
                    <span mat-stroked-button>{{ invoiceFile()?.name || 'Escolher arquivo' }}</span>
                  </label>
                  <button mat-flat-button type="button" [disabled]="busy()" (click)="invoice()">
                    Registrar a nota
                  </button>
                </div>
              </section>
            }

            <!-- Pagamento ------------------------------------------------------- -->
            @if (d.status === 'INVOICED' && canFinance()) {
              <section class="card" aria-labelledby="c-pay">
                <h2 id="c-pay"><mat-icon aria-hidden="true">payments</mat-icon> Pagamento</h2>
                <p class="muted small">
                  Pix ou transferência para a conta do fornecedor, até
                  {{ date(d.paymentDueDate) }}. Anexe o comprovante antes de registrar.
                </p>
                <div class="three">
                  <mat-form-field>
                    <mat-label>Pago em</mat-label>
                    <input matInput type="date" [(ngModel)]="paidOn" [max]="todayIso" />
                  </mat-form-field>
                  <mat-form-field>
                    <mat-label>Valor pago (R$)</mat-label>
                    <input matInput type="number" step="0.01" [(ngModel)]="paidValue" />
                  </mat-form-field>
                  <mat-form-field>
                    <mat-label>Forma</mat-label>
                    <mat-select [(ngModel)]="method">
                      <mat-option value="PIX">Pix</mat-option>
                      <mat-option value="TRANSFER">Transferência</mat-option>
                    </mat-select>
                  </mat-form-field>
                </div>
                <div class="btns">
                  <label class="file-btn">
                    <input
                      type="file"
                      accept=".pdf,.jpg,.jpeg,.png"
                      (change)="upload($event, 'PAYMENT_PROOF')"
                    />
                    <span mat-stroked-button>Anexar comprovante</span>
                  </label>
                  <button
                    mat-flat-button
                    type="button"
                    class="ok"
                    [disabled]="busy()"
                    (click)="pay()"
                  >
                    Registrar pagamento
                  </button>
                </div>
              </section>
            }

            @if (d.status === 'CANCELLED') {
              <p class="alert alert-error">Cancelada: {{ d.cancelReason }}</p>
            }

            <div class="btns">
              <button mat-stroked-button type="button" (click)="pdf()">
                <mat-icon aria-hidden="true">picture_as_pdf</mat-icon>
                {{ d.number ? 'Ordem (PDF)' : 'Minuta (PDF)' }}
              </button>
              <a mat-button [routerLink]="['/comissao/editais', d.tender.id]">Ver a oportunidade</a>
              @if (canSanction() && d.supplier) {
                <a
                  mat-button
                  class="link-danger"
                  routerLink="/sancoes/novo"
                  [queryParams]="{ applicationId: d.supplier.applicationId, orderId: d.id }"
                  >Abrir processo sancionador</a
                >
              }
            </div>
            @if (cancellable() && canPrepare()) {
              <div class="btns">
                @if (!cancelling()) {
                  <button
                    mat-button
                    type="button"
                    class="link-danger"
                    (click)="cancelling.set(true)"
                  >
                    Cancelar a ordem
                  </button>
                } @else {
                  <mat-form-field class="grow">
                    <mat-label>Motivo do cancelamento</mat-label>
                    <input matInput [(ngModel)]="cancelReason" maxlength="1000" />
                  </mat-form-field>
                  <button mat-stroked-button type="button" (click)="cancelling.set(false)">
                    Voltar
                  </button>
                  <button
                    mat-flat-button
                    type="button"
                    class="danger"
                    [disabled]="busy() || !cancelReason.trim()"
                    (click)="cancel()"
                  >
                    Confirmar
                  </button>
                }
              </div>
            }
          </div>

          <div class="right">
            <app-order-view [order]="d" (openFile)="openFile($event)" />
            @if (d.status !== 'DRAFT' && d.status !== 'CANCELLED') {
              <app-dossier-panel [orderId]="d.id" />
            }
          </div>
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
      @media (min-width: 1100px) {
        grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
        align-items: start;
      }
    }
    .right {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .actions {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .card h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.2rem;
      margin: 0 0 0.85rem;
      mat-icon {
        color: var(--sh-primary);
      }
    }
    .form {
      display: grid;
      gap: 0.25rem;
    }
    .two,
    .three {
      display: grid;
      gap: 0 0.75rem;
      @media (min-width: 640px) {
        grid-template-columns: 1fr 1fr;
      }
    }
    .three {
      @media (min-width: 900px) {
        grid-template-columns: 1fr 1fr 1fr;
      }
    }
    .radios {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1.25rem;
      margin-bottom: 0.75rem;
    }
    .radios.col {
      flex-direction: column;
    }
    .scores {
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
      padding: 0.75rem 1rem 0.25rem;
      margin: 0.5rem 0 1rem;
      display: grid;
      gap: 0 0.75rem;
      @media (min-width: 640px) {
        grid-template-columns: 1fr 1fr;
      }
      legend {
        font-weight: 700;
        font-size: 0.92rem;
        padding: 0 0.35rem;
      }
    }
    .avg {
      margin: 0.75rem 0;
    }
    .btns {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
    }
    .grow {
      flex: 1 1 260px;
    }
    .small {
      font-size: 0.9rem;
    }
    .file-btn {
      position: relative;
      display: inline-flex;
      align-items: center;
      min-height: 44px;
      padding: 0 1rem;
      border: 1px solid var(--sh-border-strong);
      border-radius: 999px;
      font-weight: 700;
      cursor: pointer;
      color: var(--sh-primary);
      input {
        position: absolute;
        inset: 0;
        opacity: 0;
        cursor: pointer;
      }
      &:focus-within {
        outline: 3px solid var(--sh-focus);
        outline-offset: 2px;
      }
    }
    .link-danger {
      color: var(--sh-error);
    }
    .danger {
      --mat-button-filled-container-color: var(--sh-error);
    }
    .ok {
      --mat-button-filled-container-color: var(--sh-green);
    }
  `,
})
export class ContractPage {
  private readonly api = inject(ContractApi);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly badge = ORDER_BADGE;
  protected readonly criteria = SCORE_CRITERIA;
  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly todayIso = today();

  protected readonly o = signal<OrderDetail | null>(null);
  protected readonly loadError = signal('');
  protected readonly error = signal('');
  protected readonly issues = signal<{ field: string; message: string }[]>([]);
  protected readonly busy = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly invoiceFile = signal<File | null>(null);

  protected readonly canPrepare = computed(() =>
    this.auth.hasRole('COMMISSION', 'DEMAND_MANAGER', 'ADMIN'),
  );
  protected readonly canSanction = computed(() => this.auth.hasRole('COMMISSION', 'ADMIN'));
  protected readonly canFinance = computed(() => this.auth.hasRole('FINANCE', 'ADMIN'));
  protected readonly closed = computed(() =>
    ['PAID', 'CANCELLED', 'REJECTED'].includes(this.o()?.status ?? ''),
  );
  protected readonly cancellable = computed(() =>
    ['DRAFT', 'ISSUED', 'EXPIRED', 'SIGNED'].includes(this.o()?.status ?? ''),
  );

  // Preparar
  protected kind: OrderKind = 'SUPPLY';
  protected project = '';
  protected objectDescription = '';
  protected schedule = '';
  protected location = '';
  protected paymentDays = 30;
  protected upfrontValue: number | null = null;
  protected penaltyPct: number | null = null;
  protected notes = '';
  protected cancelReason = '';

  // Receber
  protected receiptKind: ReceiptKind = 'DEFINITIVE';
  protected executionDate = today();
  protected reservations = '';
  protected correctionDays: number | null = null;
  protected rejectReason = '';
  protected scores: Record<string, number> = {
    scoreDeadline: 5,
    scoreQuality: 5,
    scoreDocumentation: 5,
    scoreService: 5,
    scoreSafety: 5,
  };
  protected evidenceKinds = '';
  protected receiptNotes = '';

  // Nota e pagamento
  protected invoiceNumber = '';
  protected invoiceDate = today();
  protected invoiceValue: number | null = null;
  protected paidOn = today();
  protected paidValue: number | null = null;
  protected method: PaymentMethod = 'PIX';

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() =>
        this.api.get(id).subscribe({
          next: (o) => this.set(o),
          error: (e: unknown) => this.loadError.set(problemMessage(e)),
        }),
      );
    });
  }

  protected average(): string {
    const v = Object.values(this.scores);
    return (v.reduce((a, b) => a + b, 0) / v.length).toFixed(2).replace('.', ',');
  }

  private set(o: OrderDetail): void {
    this.o.set(o);
    this.kind = o.kind;
    this.project = o.project ?? '';
    this.objectDescription = o.objectDescription;
    this.schedule = o.schedule ?? '';
    this.location = o.location ?? '';
    this.paymentDays = o.paymentDays;
    this.upfrontValue = o.upfrontValue;
    this.penaltyPct = o.penaltyPct;
    this.notes = o.notes ?? '';
    this.invoiceValue = o.totalValue;
    this.paidValue = o.invoiceValue ?? o.totalValue;
  }

  private run(call: Observable<OrderDetail>, ok: string): void {
    this.busy.set(true);
    this.error.set('');
    this.issues.set([]);
    call.subscribe({
      next: (o) => {
        this.busy.set(false);
        this.set(o);
        this.cancelling.set(false);
        this.snack.open(ok, 'OK', { duration: 5000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const p = toProblem(e);
        this.error.set(p.detail ?? problemMessage(e));
        this.issues.set(p.errors ?? []);
      },
    });
  }

  private terms() {
    return {
      kind: this.kind,
      project: this.project.trim() || null,
      objectDescription: this.objectDescription,
      schedule: this.schedule.trim() || null,
      location: this.location.trim() || null,
      paymentDays: this.paymentDays,
      upfrontValue: this.upfrontValue || null,
      penaltyPct: this.penaltyPct || null,
      notes: this.notes.trim() || null,
    };
  }

  protected save(): void {
    const o = this.o();
    if (o) {
      this.run(this.api.update(o.id, this.terms(), o.version), 'Ordem salva.');
    }
  }

  protected issue(): void {
    const o = this.o();
    if (!o) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.issues.set([]);
    this.api.update(o.id, this.terms(), o.version).subscribe({
      next: (saved) =>
        this.run(
          this.api.issue(saved.id, saved.version),
          saved.acceptanceBased
            ? 'Ordem emitida. O aceite da Faixa 1 vale como assinatura.'
            : 'Ordem emitida. O fornecedor foi avisado para assinar.',
        ),
      error: (e: unknown) => {
        this.busy.set(false);
        const p = toProblem(e);
        this.error.set(p.detail ?? problemMessage(e));
        this.issues.set(p.errors ?? []);
      },
    });
  }

  protected cancel(): void {
    const o = this.o();
    if (o && this.cancelReason.trim()) {
      this.run(this.api.cancel(o.id, this.cancelReason.trim(), o.version), 'Ordem cancelada.');
    }
  }

  protected receive(): void {
    const o = this.o();
    if (!o) {
      return;
    }
    const input: ReceiptInput = {
      kind: this.receiptKind,
      executionDate: this.executionDate,
      reservations: this.reservations.trim() || null,
      correctionDays: this.correctionDays,
      rejectReason: this.rejectReason.trim() || null,
      scoreDeadline: this.scores['scoreDeadline'],
      scoreQuality: this.scores['scoreQuality'],
      scoreDocumentation: this.scores['scoreDocumentation'],
      scoreService: this.scores['scoreService'],
      scoreSafety: this.scores['scoreSafety'],
      notes: this.receiptNotes.trim() || null,
      evidenceKinds: this.evidenceKinds.trim() || null,
    };
    this.run(this.api.receive(o.id, input), 'Termo registrado. O fornecedor foi avisado.');
  }

  protected upload(event: Event, kind: FileKind): void {
    const o = this.o();
    const el = event.target as HTMLInputElement;
    const f = el.files?.[0];
    el.value = '';
    if (o && f) {
      this.run(this.api.upload(o.id, kind, f), 'Arquivo anexado.');
    }
  }

  protected pick(event: Event): void {
    const el = event.target as HTMLInputElement;
    this.invoiceFile.set(el.files?.[0] ?? null);
  }

  protected invoice(): void {
    const o = this.o();
    if (o) {
      this.run(
        this.api.invoice(
          o.id,
          { number: this.invoiceNumber, date: this.invoiceDate, value: this.invoiceValue },
          this.invoiceFile(),
        ),
        'Nota fiscal registrada.',
      );
    }
  }

  protected pay(): void {
    const o = this.o();
    if (o && this.paidValue) {
      this.run(
        this.api.pay(o.id, this.paidOn, this.paidValue, this.method, o.version),
        'Pagamento registrado. O fornecedor foi avisado.',
      );
    }
  }

  protected openFile(fileId: number): void {
    const o = this.o();
    if (o) {
      this.api.openFile(o.id, fileId);
    }
  }

  protected pdf(): void {
    const o = this.o();
    if (o) {
      this.api.openPdf(o.id);
    }
  }
}
