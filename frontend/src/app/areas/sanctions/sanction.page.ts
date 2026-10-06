import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import {
  BoardOutcome,
  SANCTION_BADGE,
  SANCTION_LABELS,
  SanctionApi,
  SanctionDetail,
  SanctionKind,
} from '../../core/api/control.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';
import { SanctionView } from './sanction-view';

const KINDS: SanctionKind[] = ['NONE', 'WARNING', 'FINE', 'SUSPENSION', 'DEREGISTRATION'];

/** Processo sancionador para a equipe: decisão da Comissão e recurso na Diretoria. */
@Component({
  selector: 'app-sanction-page',
  imports: [
    FormsModule,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    AreaTabs,
    SanctionView,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/sancoes">← Processos sancionadores</a></p>
        @if (p(); as d) {
          <div class="head">
            <div>
              <span class="eyebrow">{{ d.number }}</span>
              <h1>{{ d.supplierName }}</h1>
              <p>{{ d.infractionLabel }}</p>
            </div>
            <span class="badge big" [class]="badge[d.status]">{{ d.statusLabel }}</span>
          </div>
        } @else {
          <h1>Processo sancionador</h1>
        }
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      @if (loadError()) {
        <p class="alert alert-error" role="alert">{{ loadError() }}</p>
      } @else if (!p()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let d = p()!;
        @if (d.recidivist) {
          <p class="alert alert-warning">
            <strong>Reincidência:</strong> o fornecedor já teve sanção nos últimos 5 anos (Anexo
            XII).
          </p>
        }
        @if (error()) {
          <div class="alert alert-error" role="alert">
            <p>
              <strong>{{ error() }}</strong>
            </p>
            @for (i of issues(); track i.field) {
              <p>{{ i.message }}</p>
            }
          </div>
        }
        <div class="layout">
          <section class="card">
            <app-sanction-view [process]="d" (openFile)="openFile($event)" />
          </section>

          <div class="side">
            @if (canDecide()) {
              <section class="card">
                <h2>Decisão motivada da Comissão</h2>
                @if (d.status === 'DEFENSE') {
                  <p class="muted small">
                    O prazo de defesa vai até {{ date(d.defenseDue) }}. Depois dele, a decisão pode
                    ser tomada mesmo sem defesa.
                  </p>
                } @else {
                  <p class="muted small">Prazo: {{ date(d.decisionDue) }} (10 dias úteis).</p>
                }
                <ng-container *ngTemplateOutlet="decisionForm" />
                <mat-form-field class="full">
                  <mat-label>Motivação</mat-label>
                  <textarea matInput rows="4" [(ngModel)]="reason" maxlength="5000"></textarea>
                </mat-form-field>
                <button
                  mat-flat-button
                  type="button"
                  [disabled]="busy() || !reason.trim()"
                  (click)="decide()"
                >
                  Registrar a decisão
                </button>
              </section>
            }

            @if (d.status === 'BOARD_REVIEW' && isBoard()) {
              <section class="card">
                <h2>Decisão final da Diretoria</h2>
                <p class="muted small">Prazo: {{ date(d.boardDue) }} (10 dias úteis).</p>
                <mat-radio-group [(ngModel)]="outcome" class="radios" aria-label="Decisão">
                  <mat-radio-button value="UPHELD">Manter a decisão da Comissão</mat-radio-button>
                  <mat-radio-button value="REFORMED">Reformar a sanção</mat-radio-button>
                  <mat-radio-button value="ANNULLED">Anular (sem sanção)</mat-radio-button>
                </mat-radio-group>
                @if (outcome === 'REFORMED') {
                  <ng-container *ngTemplateOutlet="decisionForm" />
                }
                <mat-form-field class="full">
                  <mat-label>Motivação</mat-label>
                  <textarea matInput rows="4" [(ngModel)]="reason" maxlength="5000"></textarea>
                </mat-form-field>
                <button
                  mat-flat-button
                  type="button"
                  [disabled]="busy() || !reason.trim()"
                  (click)="board()"
                >
                  Registrar a decisão final
                </button>
              </section>
            }

            @if (d.status !== 'FINAL' && canCommission()) {
              <section class="card">
                <h2>Juntar prova</h2>
                <label class="file-btn">
                  <input
                    type="file"
                    accept=".pdf,.jpg,.jpeg,.png,.docx"
                    (change)="evidence($event)"
                  />
                  <span>Anexar arquivo</span>
                </label>
              </section>
            }

            @if (d.previous.length) {
              <section class="card">
                <h2>Outros processos deste fornecedor</h2>
                <ul class="prev">
                  @for (x of d.previous; track x.id) {
                    <li>
                      <a [routerLink]="['/sancoes', x.id]">{{ x.number }}</a> —
                      {{ x.infractionLabel }} · {{ x.finalSanctionLabel || x.statusLabel }}
                    </li>
                  }
                </ul>
              </section>
            }
          </div>
        </div>
      }
    </div>

    <ng-template #decisionForm>
      <mat-radio-group [(ngModel)]="sanction" class="radios" aria-label="Sanção">
        @for (k of kinds; track k) {
          <mat-radio-button [value]="k">{{ labels[k] }}</mat-radio-button>
        }
      </mat-radio-group>
      @if (sanction === 'FINE') {
        <mat-form-field>
          <mat-label>Multa (%)</mat-label>
          <input matInput type="number" min="2" max="10" step="0.5" [(ngModel)]="finePct" />
          <mat-hint>{{
            p()?.orderPenaltyPct
              ? 'Prevista na ordem: até ' + p()?.orderPenaltyPct + '%'
              : 'Sem ordem com multa prevista — não cabe multa'
          }}</mat-hint>
        </mat-form-field>
      }
      @if (sanction === 'SUSPENSION') {
        <mat-form-field>
          <mat-label>Meses de suspensão</mat-label>
          <input matInput type="number" min="1" max="24" [(ngModel)]="months" />
        </mat-form-field>
      }
      @if (sanction === 'DEREGISTRATION') {
        <mat-form-field>
          <mat-label>Anos de impedimento</mat-label>
          <input matInput type="number" min="1" max="3" [(ngModel)]="years" />
        </mat-form-field>
      }
    </ng-template>
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
      @media (min-width: 1000px) {
        grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
        align-items: start;
      }
    }
    .side {
      display: grid;
      gap: 1rem;
    }
    h2 {
      font-size: 1.15rem;
      margin: 0 0 0.5rem;
    }
    .radios {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      margin: 0.5rem 0 1rem;
    }
    .full {
      width: 100%;
    }
    .small {
      font-size: 0.9rem;
    }
    .prev {
      margin: 0;
      padding-left: 1.2rem;
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
  `,
})
export class SanctionPage {
  private readonly api = inject(SanctionApi);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly badge = SANCTION_BADGE;
  protected readonly labels = SANCTION_LABELS;
  protected readonly kinds = KINDS;
  protected readonly date = formatDate;

  protected readonly p = signal<SanctionDetail | null>(null);
  protected readonly loadError = signal('');
  protected readonly error = signal('');
  protected readonly issues = signal<{ field: string; message: string }[]>([]);
  protected readonly busy = signal(false);

  protected readonly canCommission = computed(() => this.auth.hasRole('COMMISSION', 'ADMIN'));
  protected readonly isBoard = computed(() => this.auth.hasRole('BOARD'));
  protected readonly canDecide = computed(() => {
    const d = this.p();
    return (
      !!d &&
      this.canCommission() &&
      (d.status === 'AWAITING_DECISION' || (d.status === 'DEFENSE' && !d.canDefend))
    );
  });

  protected sanction: SanctionKind = 'WARNING';
  protected finePct: number | null = null;
  protected months: number | null = null;
  protected years: number | null = null;
  protected reason = '';
  protected outcome: BoardOutcome = 'UPHELD';

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() =>
        this.api.get(id).subscribe({
          next: (p) => this.p.set(p),
          error: (e: unknown) => this.loadError.set(problemMessage(e)),
        }),
      );
    });
  }

  private run(call: Observable<SanctionDetail>, ok: string): void {
    this.busy.set(true);
    this.error.set('');
    this.issues.set([]);
    call.subscribe({
      next: (p) => {
        this.busy.set(false);
        this.p.set(p);
        this.reason = '';
        this.snack.open(ok, 'OK', { duration: 5000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const pr = toProblem(e);
        this.error.set(pr.detail ?? problemMessage(e));
        this.issues.set(pr.errors ?? []);
      },
    });
  }

  private decision() {
    return {
      sanction: this.sanction,
      finePct: this.finePct,
      months: this.months,
      years: this.years,
      reason: this.reason.trim(),
    };
  }

  protected decide(): void {
    const d = this.p();
    if (d) {
      this.run(
        this.api.decide(d.id, this.decision(), d.version),
        'Decisão registrada. O fornecedor foi notificado.',
      );
    }
  }

  protected board(): void {
    const d = this.p();
    if (d) {
      this.run(
        this.api.board(d.id, this.outcome, this.decision(), this.reason.trim(), d.version),
        'Decisão final registrada.',
      );
    }
  }

  protected evidence(event: Event): void {
    const d = this.p();
    const el = event.target as HTMLInputElement;
    const f = el.files?.[0];
    el.value = '';
    if (d && f) {
      this.run(this.api.evidence(d.id, f), 'Prova juntada.');
    }
  }

  protected openFile(fileId: number): void {
    const d = this.p();
    if (d) {
      this.api.openFile(d.id, fileId);
    }
  }
}
