import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { BoardItem, JudgmentApi } from '../../core/api/judgment.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** Início da Diretoria: atas da Faixa 3 aguardando aprovação. */
@Component({
  selector: 'app-board-home-page',
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Diretoria</span>
        <h1>Atas para aprovar</h1>
        <p>
          Olá, {{ auth.user()?.fullName }}. Na Faixa 3 (chamada pública) a ata de julgamento só vale
          depois da aprovação da Diretoria. Os recursos de inabilitação ficam na aba Recursos.
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (!items()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else if (!items()!.length) {
        <section class="card empty">
          <mat-icon aria-hidden="true">task_alt</mat-icon>
          <p>Nenhuma ata aguardando aprovação.</p>
        </section>
      } @else {
        <ul class="list">
          @for (i of items(); track i.judgmentId) {
            <li>
              <a class="row card-hover" [routerLink]="['/diretoria/atas', i.judgmentId]">
                <span class="icon-tile" aria-hidden="true"><mat-icon>gavel</mat-icon></span>
                <div class="main">
                  <strong>{{ i.tenderTitle }}</strong>
                  <span class="muted"
                    >{{ i.tenderNumber }} · {{ i.lineCode }} · vencedora {{ i.winnerName }}</span
                  >
                </div>
                <div class="side">
                  <strong>{{ money(i.awardedValue) }}</strong>
                  <small class="muted">estimado {{ money(i.estimatedValue) }}</small>
                  <small class="muted">desde {{ dateTime(i.decidedAt) }}</small>
                </div>
              </a>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
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
    .empty {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      mat-icon {
        color: var(--sh-green);
      }
      p {
        margin: 0;
      }
    }
    .list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.75rem;
    }
    .row {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1rem 1.25rem;
      text-decoration: none;
      color: inherit;
    }
    .main {
      flex: 1 1 auto;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .side {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      text-align: right;
    }
    @media (max-width: 600px) {
      .row {
        flex-wrap: wrap;
      }
      .side {
        align-items: flex-start;
        text-align: left;
      }
    }
  `,
})
export class BoardHomePage {
  private readonly api = inject(JudgmentApi);
  protected readonly auth = inject(AuthService);

  protected readonly money = formatMoney;
  protected readonly dateTime = formatDateTime;
  protected readonly items = signal<BoardItem[] | null>(null);
  protected readonly error = signal('');

  constructor() {
    this.api.boardPending().subscribe({
      next: (i) => this.items.set(i),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }
}
