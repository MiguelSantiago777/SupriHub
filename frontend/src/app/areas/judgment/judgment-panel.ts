import {
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { JUDGMENT_BADGE, JudgmentApi, JudgmentPanel as Panel } from '../../core/api/judgment.api';
import { ProposalRow, TenderDetail } from '../../core/api/tender.api';
import { formatDate, formatDateTime, formatMoney } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { JudgmentMap } from './judgment-map';

/** Julgamento das Faixas 2 e 3 na tela da oportunidade (Comissão). */
@Component({
  selector: 'app-judgment-panel',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    JudgmentMap,
  ],
  template: `
    <section class="card" aria-labelledby="j-title">
      <div class="title-row">
        <h2 id="j-title"><mat-icon aria-hidden="true">balance</mat-icon> Julgamento</h2>
        @if (live(); as j) {
          <span class="badge" [class]="badge[j.status]">{{ j.statusLabel }}</span>
        }
      </div>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      }

      @if (panel(); as p) {
        @if (p.judgment?.status === 'ANNULLED') {
          <p class="alert alert-warning">
            Julgamento anulado em {{ dateTime(p.judgment!.annulledAt) }}:
            {{ p.judgment!.annulReason }}
          </p>
        }

        @if (p.canStart) {
          <!-- Antes de julgar ------------------------------------------------- -->
          <p class="muted">
            Confira as propostas. Desclassifique, com motivo, a que não atender ao pedido. Quem não
            tiver credenciamento vigente é desclassificado automaticamente.
          </p>
          <ul class="props">
            @for (pr of tender().proposals; track pr.id) {
              <li [class.dq]="pr.disqualifiedReason">
                <div class="who">
                  <strong>{{ pr.supplierName }}</strong>
                  <span class="muted">{{ money(pr.totalValue) }}</span>
                  @if (pr.disqualifiedReason) {
                    <span class="badge badge-error">Desclassificada</span>
                    <span class="muted small">{{ pr.disqualifiedReason }}</span>
                  }
                </div>
                @if (pr.disqualifiedReason) {
                  <button mat-button type="button" [disabled]="busy()" (click)="requalify(pr)">
                    Desfazer
                  </button>
                } @else if (disqualifying() === pr.id) {
                  <div class="inline">
                    <mat-form-field class="grow">
                      <mat-label>Motivo da desclassificação</mat-label>
                      <input matInput [(ngModel)]="dqReason" maxlength="1000" />
                    </mat-form-field>
                    <button mat-stroked-button type="button" (click)="disqualifying.set(null)">
                      Voltar
                    </button>
                    <button
                      mat-flat-button
                      type="button"
                      class="danger"
                      [disabled]="busy() || !dqReason.trim()"
                      (click)="disqualify(pr)"
                    >
                      Desclassificar
                    </button>
                  </div>
                } @else {
                  <button mat-button type="button" class="link-danger" (click)="startDq(pr)">
                    Desclassificar
                  </button>
                }
              </li>
            }
          </ul>
          @if (validCount() < p.minValidRequired) {
            <div class="alert alert-warning">
              <p>
                <strong
                  >A Faixa 2 pede {{ p.minValidRequired }} propostas válidas e há
                  {{ validCount() }}.</strong
                >
                Você pode cancelar e repetir a cotação, ou justificar para julgar assim mesmo
                (Edital 7.1).
              </p>
              <mat-form-field class="full">
                <mat-label>Justificativa</mat-label>
                <textarea matInput rows="2" [(ngModel)]="justification" maxlength="2000"></textarea>
              </mat-form-field>
            </div>
          }
          <div class="btns">
            <button
              mat-flat-button
              type="button"
              [disabled]="
                busy() ||
                !validCount() ||
                (validCount() < p.minValidRequired && !justification.trim())
              "
              (click)="start()"
            >
              <mat-icon aria-hidden="true">gavel</mat-icon> Julgar propostas
            </button>
          </div>
        } @else if (live(); as j) {
          <!-- Julgamento em curso ou concluído -------------------------------- -->
          @if (j.status === 'AWAITING_COVER' && pendingCover(); as c) {
            <p class="alert alert-info">
              <strong>{{ c.supplierName }}</strong> foi convocada a cobrir
              {{ money(c.ceiling) }} até {{ date(c.dueDate) }}. Se não cobrir, a vez passa à próxima
              colocada.
            </p>
          }
          <app-judgment-map [judgment]="j" />

          <div class="decide">
            <button mat-stroked-button type="button" (click)="ata()">
              <mat-icon aria-hidden="true">picture_as_pdf</mat-icon>
              {{ j.status === 'HOMOLOGATED' ? 'Ata (PDF)' : 'Minuta da ata (PDF)' }}
            </button>
            @if (j.status === 'DECIDED' && !j.approvalByBoard) {
              <mat-form-field class="grow">
                <mat-label>Observação na homologação (opcional)</mat-label>
                <input matInput [(ngModel)]="note" maxlength="2000" />
              </mat-form-field>
              <button
                mat-flat-button
                type="button"
                class="ok"
                [disabled]="busy()"
                (click)="homologate()"
              >
                <mat-icon aria-hidden="true">verified</mat-icon> Homologar resultado
              </button>
            } @else if (j.status === 'DECIDED') {
              <span class="muted">Faixa 3: a ata segue para aprovação da Diretoria.</span>
            }
          </div>
          @if (j.status === 'HOMOLOGATED') {
            <p class="muted small">
              {{ j.approvalByBoard ? 'Aprovada pela Diretoria' : 'Homologado' }} em
              {{ dateTime(j.homologatedAt) }}.
              @if (j.homologationNote) {
                {{ j.homologationNote }}
              }
              Hash da ata: <code>{{ j.ataSha256?.slice(0, 16) }}…</code>
            </p>
          }

          @if (j.status === 'AWAITING_COVER' || j.status === 'DECIDED') {
            <div class="annul">
              @if (!annulling()) {
                <button mat-button type="button" class="link-danger" (click)="annulling.set(true)">
                  Anular julgamento
                </button>
              } @else {
                <mat-form-field class="grow">
                  <mat-label>Motivo da anulação</mat-label>
                  <input matInput [(ngModel)]="annulReason" maxlength="2000" />
                </mat-form-field>
                <button mat-stroked-button type="button" (click)="annulling.set(false)">
                  Voltar
                </button>
                <button
                  mat-flat-button
                  type="button"
                  class="danger"
                  [disabled]="busy() || !annulReason.trim()"
                  (click)="annul()"
                >
                  Anular
                </button>
              }
            </div>
          }
        }

        @if (p.judgment?.history?.length) {
          <details class="history">
            <summary>Julgamentos anteriores ({{ p.judgment!.history.length }})</summary>
            <ul>
              @for (h of p.judgment!.history; track h.id) {
                <li>
                  {{ dateTime(h.startedAt) }} — {{ h.statusLabel }}
                  @if (h.annulReason) {
                    : {{ h.annulReason }}
                  }
                </li>
              }
            </ul>
          </details>
        }
      }
    </section>
  `,
  styles: `
    .title-row {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.85rem;
      h2 {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 1.2rem;
        margin: 0;
        mat-icon {
          color: var(--sh-primary);
        }
      }
    }
    .props {
      list-style: none;
      padding: 0;
      margin: 0 0 1rem;
      display: grid;
      gap: 0.5rem;
      li {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        align-items: center;
        gap: 0.5rem;
        padding: 0.6rem 0.85rem;
        border: 1px solid var(--sh-border);
        border-radius: var(--sh-radius-sm);
      }
      li.dq {
        background: var(--sh-error-soft);
      }
    }
    .who {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem 0.75rem;
      align-items: center;
    }
    .inline,
    .decide,
    .annul,
    .btns {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: flex-start;
    }
    .inline {
      flex: 1 1 100%;
    }
    .decide {
      margin-top: 1.25rem;
      align-items: center;
    }
    .annul {
      margin-top: 0.75rem;
    }
    .grow {
      flex: 1 1 260px;
    }
    .full {
      width: 100%;
    }
    .small {
      font-size: 0.9rem;
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
    .history {
      margin-top: 1rem;
      font-size: 0.92rem;
    }
  `,
})
export class JudgmentPanel {
  private readonly api = inject(JudgmentApi);
  private readonly snack = inject(MatSnackBar);

  readonly tender = input.required<TenderDetail>();
  /** Avisa a página para recarregar a oportunidade (a situação muda ao julgar/homologar). */
  readonly changed = output<void>();

  protected readonly badge = JUDGMENT_BADGE;
  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly panel = signal<Panel | null>(null);
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly disqualifying = signal<number | null>(null);
  protected readonly annulling = signal(false);
  protected dqReason = '';
  protected justification = '';
  protected note = '';
  protected annulReason = '';

  protected readonly live = computed(() => {
    const j = this.panel()?.judgment;
    return j && j.status !== 'ANNULLED' ? j : null;
  });

  protected readonly pendingCover = computed(
    () => this.live()?.covers.find((c) => c.status === 'OFFERED') ?? null,
  );

  protected readonly validCount = computed(
    () => this.tender().proposals.filter((p) => !p.disqualifiedReason).length,
  );

  constructor() {
    effect(() => {
      const id = this.tender().id;
      untracked(() =>
        this.api.panel(id).subscribe({
          next: (p) => this.panel.set(p),
          error: (e: unknown) => this.error.set(problemMessage(e)),
        }),
      );
    });
  }

  private run(call: Observable<Panel>, ok: string, reloadTender = true): void {
    this.busy.set(true);
    this.error.set('');
    call.subscribe({
      next: (p) => {
        this.busy.set(false);
        this.panel.set(p);
        this.disqualifying.set(null);
        this.annulling.set(false);
        this.snack.open(ok, 'OK', { duration: 5000 });
        if (reloadTender) {
          this.changed.emit();
        }
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const problem = toProblem(e);
        this.error.set(problem.detail ?? problemMessage(e));
      },
    });
  }

  protected startDq(p: ProposalRow): void {
    this.dqReason = '';
    this.disqualifying.set(p.id);
  }

  protected disqualify(p: ProposalRow): void {
    this.run(
      this.api.disqualify(this.tender().id, p.id, this.dqReason.trim()),
      'Proposta desclassificada.',
    );
  }

  protected requalify(p: ProposalRow): void {
    this.run(this.api.requalify(this.tender().id, p.id), 'Desclassificação desfeita.');
  }

  protected start(): void {
    const j = this.justification.trim();
    this.run(this.api.start(this.tender().id, j || null), 'Julgamento feito.');
  }

  protected homologate(): void {
    const j = this.live();
    if (j) {
      this.run(
        this.api.homologate(this.tender().id, this.note.trim() || null, j.version),
        'Resultado homologado. Os proponentes foram avisados.',
      );
    }
  }

  protected annul(): void {
    const j = this.live();
    if (j && this.annulReason.trim()) {
      this.run(
        this.api.annul(this.tender().id, this.annulReason.trim(), j.version),
        'Julgamento anulado.',
      );
    }
  }

  protected ata(): void {
    const j = this.live();
    if (j) {
      this.api.openAta(j.id);
    }
  }
}
