import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { BoardDetail, JUDGMENT_BADGE, JudgmentApi } from '../../core/api/judgment.api';
import { formatDateTime, formatMoney } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { JudgmentMap } from '../judgment/judgment-map';
import { AreaTabs } from '../shared/area-tabs';

/** Diretoria: confere o mapa e aprova (ou devolve) a ata da Faixa 3. */
@Component({
  selector: 'app-board-judgment-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AreaTabs,
    JudgmentMap,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/diretoria">← Atas para aprovar</a></p>
        @if (d(); as x) {
          <span class="eyebrow">{{ x.tenderNumber }} · {{ x.bandLabel }}</span>
          <h1>{{ x.tenderTitle }}</h1>
          <p>
            Linha {{ x.lineCode }} · {{ x.lineName }} · valor estimado
            <strong>{{ money(x.estimatedValue) }}</strong>
          </p>
        } @else {
          <h1>Ata de julgamento</h1>
        }
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      }
      @if (d(); as x) {
        @let j = x.judgment;
        <section class="card">
          <div class="title-row">
            <h2><mat-icon aria-hidden="true">balance</mat-icon> Mapa e resultado</h2>
            <span class="badge" [class]="badge[j.status]">{{ j.statusLabel }}</span>
          </div>
          <p class="pre muted">{{ x.description }}</p>
          <app-judgment-map [judgment]="j" />

          <div class="actions">
            <button mat-stroked-button type="button" (click)="ata()">
              <mat-icon aria-hidden="true">picture_as_pdf</mat-icon>
              {{ j.status === 'HOMOLOGATED' ? 'Ata (PDF)' : 'Minuta da ata (PDF)' }}
            </button>
          </div>

          @if (j.status === 'DECIDED') {
            <div class="decide">
              @if (!returning()) {
                <mat-form-field class="grow">
                  <mat-label>Observação (opcional)</mat-label>
                  <input matInput [(ngModel)]="note" maxlength="2000" />
                </mat-form-field>
                <button
                  mat-stroked-button
                  type="button"
                  class="link-danger"
                  (click)="returning.set(true)"
                >
                  Devolver à Comissão
                </button>
                <button
                  mat-flat-button
                  type="button"
                  class="ok"
                  [disabled]="busy()"
                  (click)="approve()"
                >
                  <mat-icon aria-hidden="true">verified</mat-icon> Aprovar a ata
                </button>
              } @else {
                <mat-form-field class="grow">
                  <mat-label>Motivo da devolução</mat-label>
                  <input matInput [(ngModel)]="reason" maxlength="2000" />
                </mat-form-field>
                <button mat-stroked-button type="button" (click)="returning.set(false)">
                  Voltar
                </button>
                <button
                  mat-flat-button
                  type="button"
                  class="danger"
                  [disabled]="busy() || !reason.trim()"
                  (click)="giveBack()"
                >
                  Devolver (anula o julgamento)
                </button>
              }
            </div>
          } @else if (j.status === 'HOMOLOGATED') {
            <p class="alert alert-success">Ata aprovada em {{ dateTime(j.homologatedAt) }}.</p>
          } @else if (j.status === 'ANNULLED') {
            <p class="alert alert-warning">Devolvida: {{ j.annulReason }}</p>
          }
        </section>
      } @else if (!error()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      }
    </div>
  `,
  styles: `
    .back {
      margin: 0 0 0.75rem !important;
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
    .title-row {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
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
    .pre {
      white-space: pre-line;
    }
    .actions,
    .decide {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
      margin-top: 1.25rem;
    }
    .grow {
      flex: 1 1 260px;
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
export class BoardJudgmentPage {
  private readonly api = inject(JudgmentApi);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id` (id do julgamento). */
  readonly id = input.required<string>();

  protected readonly badge = JUDGMENT_BADGE;
  protected readonly money = formatMoney;
  protected readonly dateTime = formatDateTime;
  protected readonly d = signal<BoardDetail | null>(null);
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly returning = signal(false);
  protected note = '';
  protected reason = '';

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() =>
        this.api.boardGet(id).subscribe({
          next: (d) => this.d.set(d),
          error: (e: unknown) => this.error.set(problemMessage(e)),
        }),
      );
    });
  }

  private run(call: Observable<BoardDetail>, ok: string): void {
    this.busy.set(true);
    this.error.set('');
    call.subscribe({
      next: (d) => {
        this.busy.set(false);
        this.d.set(d);
        this.returning.set(false);
        this.snack.open(ok, 'OK', { duration: 5000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toProblem(e).detail ?? problemMessage(e));
      },
    });
  }

  protected approve(): void {
    const j = this.d()?.judgment;
    if (j) {
      this.run(
        this.api.boardApprove(j.id, this.note.trim() || null, j.version),
        'Ata aprovada. Os proponentes foram avisados.',
      );
    }
  }

  protected giveBack(): void {
    const j = this.d()?.judgment;
    if (j && this.reason.trim()) {
      this.run(
        this.api.boardReturn(j.id, this.reason.trim(), j.version),
        'Ata devolvida à Comissão.',
      );
    }
  }

  protected ata(): void {
    const j = this.d()?.judgment;
    if (j) {
      this.api.openAta(j.id);
    }
  }
}
