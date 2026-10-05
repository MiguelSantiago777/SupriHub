import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { TENDER_BADGE, TenderApi, TenderStatus, TenderSummary } from '../../core/api/tender.api';
import { formatDate, formatDateTime, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

interface Tab {
  slug: string;
  label: string;
  icon: string;
  statuses: TenderStatus[];
  empty: string;
}

const TABS: Tab[] = [
  {
    slug: 'abertos',
    label: 'Abertos',
    icon: 'campaign',
    statuses: ['OPEN'],
    empty: 'Nenhuma oportunidade aberta.',
  },
  {
    slug: 'rascunhos',
    label: 'Rascunhos',
    icon: 'draw',
    statuses: ['DRAFT'],
    empty: 'Nenhum rascunho. Use "Nova oportunidade" para começar.',
  },
  {
    slug: 'concluidos',
    label: 'Concluídos',
    icon: 'task_alt',
    statuses: ['AWARDED', 'CANCELLED'],
    empty: 'Ainda não há oportunidades concluídas.',
  },
];

@Component({
  selector: 'app-tenders-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container head">
        <div>
          <span class="eyebrow">Comissão de Credenciamento</span>
          <h1>Editais e oportunidades</h1>
          <p>
            Publique o que o Instituto precisa comprar. A faixa sai do valor estimado: Faixa 1 vai
            por rodízio; Faixas 2 e 3 recebem propostas de todos os credenciados da linha.
          </p>
        </div>
        <a mat-flat-button routerLink="/comissao/editais/novo">
          <mat-icon aria-hidden="true">add</mat-icon> Nova oportunidade
        </a>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <div class="tabs" role="tablist" aria-label="Situação">
        @for (t of tabs; track t.slug) {
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="t.slug === current().slug"
            [class.active]="t.slug === current().slug"
            (click)="select(t)"
          >
            <mat-icon aria-hidden="true">{{ t.icon }}</mat-icon>
            {{ t.label }}
            <span class="count">{{ count(t) }}</span>
          </button>
        }
      </div>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        <ul class="list">
          @for (t of items(); track t.id) {
            <li>
              <a class="row card-hover" [routerLink]="['/comissao/editais', t.id]">
                <span class="icon-tile" aria-hidden="true"><mat-icon>campaign</mat-icon></span>
                <div class="main">
                  <strong>{{ t.title }}</strong>
                  <span class="muted">
                    {{ t.number || 'Rascunho' }} · {{ t.lineCode }} {{ t.lineName }} ·
                    {{ t.bandLabel }} · estimado {{ money(t.estimatedValue) }}
                  </span>
                </div>
                <div class="side">
                  @if (t.status === 'OPEN' && t.band === 'FAIXA_1') {
                    @if (t.pendingSupplier) {
                      <span class="badge badge-warning">Rodízio: {{ t.pendingSupplier }}</span>
                      <small class="muted">responde até {{ date(t.pendingDueDate) }}</small>
                    } @else {
                      <span class="badge badge-error">Rodízio sem convidado</span>
                    }
                  } @else if (t.status === 'OPEN') {
                    <span class="badge" [class]="t.deadlinePassed ? 'badge-warning' : 'badge-info'">
                      {{
                        t.deadlinePassed ? 'Prazo encerrado' : 'Até ' + dateTime(t.proposalDeadline)
                      }}
                    </span>
                    <small class="muted">
                      {{ t.proposalCount }} {{ t.proposalCount === 1 ? 'proposta' : 'propostas' }}
                    </small>
                  } @else {
                    <span class="badge" [class]="badge[t.status]">{{ t.statusLabel }}</span>
                  }
                </div>
              </a>
            </li>
          } @empty {
            <li class="empty">
              <mat-icon aria-hidden="true">inbox</mat-icon>
              <p>{{ current().empty }}</p>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-end;
      gap: 1rem;
    }
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 1rem;
    }
    .tabs button {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      min-height: 48px;
      padding: 0 1rem;
      border-radius: 999px;
      border: 1px solid var(--sh-border);
      background: var(--sh-surface);
      color: var(--sh-text);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    .tabs button.active {
      background: var(--sh-navy);
      border-color: var(--sh-navy);
      color: #fff;
    }
    .tabs mat-icon {
      width: 20px;
      height: 20px;
      font-size: 20px;
    }
    .count {
      min-width: 1.6rem;
      padding: 0 0.45rem;
      border-radius: 999px;
      background: var(--sh-bg);
      color: var(--sh-text-muted);
      font-size: 0.82rem;
      line-height: 1.6rem;
      text-align: center;
    }
    .active .count {
      background: rgba(255, 255, 255, 0.18);
      color: #fff;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
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
      gap: 1rem;
      align-items: center;
      padding: 1rem 1.25rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius);
      background: var(--sh-surface);
      color: var(--sh-text);
      text-decoration: none;
      box-shadow: var(--sh-shadow-sm);
    }
    .main {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }
    .side {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 0.25rem;
      text-align: right;
    }
    @media (max-width: 640px) {
      .row {
        flex-wrap: wrap;
      }
      .side {
        align-items: flex-start;
        text-align: left;
      }
    }
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 3rem 1rem;
      color: var(--sh-text-muted);
      border: 2px dashed var(--sh-border);
      border-radius: var(--sh-radius);
    }
    .empty mat-icon {
      width: 40px;
      height: 40px;
      font-size: 40px;
    }
  `,
})
export class TendersPage {
  private readonly api = inject(TenderApi);
  private readonly router = inject(Router);

  /** Query param `?aba=`. */
  readonly aba = input<string>();

  protected readonly tabs = TABS;
  protected readonly badge = TENDER_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly money = formatMoney;

  protected readonly current = computed(() => TABS.find((t) => t.slug === this.aba()) ?? TABS[0]);
  protected readonly counts = signal<Partial<Record<TenderStatus, number>>>({});
  protected readonly items = signal<TenderSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      const tab = this.current();
      untracked(() => this.load(tab));
    });
  }

  protected count(t: Tab): number {
    return t.statuses.reduce((sum, s) => sum + (this.counts()[s] ?? 0), 0);
  }

  protected select(t: Tab): void {
    void this.router.navigate([], {
      queryParams: { aba: t.slug === TABS[0].slug ? null : t.slug },
    });
  }

  private load(tab: Tab): void {
    this.loading.set(true);
    this.error.set('');
    this.api.counts().subscribe({ next: (c) => this.counts.set(c) });
    this.api.list(tab.statuses).subscribe({
      next: (p) => {
        this.items.set(p.content);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }
}
