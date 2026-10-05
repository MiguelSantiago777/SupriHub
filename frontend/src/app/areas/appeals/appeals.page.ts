import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { AreaTabs } from '../shared/area-tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { APPEAL_BADGE, AppealApi, AppealStage, AppealSummary } from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatDateTime } from '../../core/format';
import { problemMessage } from '../../core/http/problem';

interface Tab {
  stage: AppealStage;
  slug: string;
  label: string;
  icon: string;
  empty: string;
}

const TABS: Tab[] = [
  {
    stage: 'COMMISSION',
    slug: 'comissao',
    label: 'Na Comissão',
    icon: 'groups',
    empty: 'Nenhum recurso aguardando a Comissão.',
  },
  {
    stage: 'BOARD',
    slug: 'diretoria',
    label: 'Na Diretoria',
    icon: 'account_balance',
    empty: 'Nenhum recurso aguardando a Diretoria.',
  },
  {
    stage: 'DECIDED',
    slug: 'decididos',
    label: 'Decididos',
    icon: 'task_alt',
    empty: 'Ainda não há recursos decididos.',
  },
];

@Component({
  selector: 'app-appeals-page',
  imports: [AreaTabs, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Edital, item 5.2</span>
        <h1>Recursos contra inabilitação</h1>
        <p>
          A Comissão reconsidera em até 3 dias úteis ou encaminha o recurso, com sua motivação, à
          Diretoria, que decide em até 5 dias úteis. Recurso aceito habilita o fornecedor.
        </p>
        <p class="links">
          @if (auth.hasRole('COMMISSION', 'ADMIN')) {
            <a routerLink="/comissao/requerimentos">Requerimentos</a>
          }
          @if (auth.hasRole('ADMIN')) {
            <a routerLink="/admin">Administração</a>
          }
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <div class="tabs" role="tablist" aria-label="Etapa dos recursos">
        @for (t of tabs; track t.stage) {
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="t.stage === current().stage"
            [class.active]="t.stage === current().stage"
            (click)="select(t)"
          >
            <mat-icon aria-hidden="true">{{ t.icon }}</mat-icon>
            <span>{{ t.label }}</span>
            <span class="count" [class.hot]="isMine(t.stage) && (counts()[t.stage] ?? 0) > 0">{{
              counts()[t.stage] ?? 0
            }}</span>
          </button>
        }
      </div>

      <section role="tabpanel" [attr.aria-label]="current().label" aria-live="polite">
        @if (error()) {
          <p class="alert alert-error" role="alert">{{ error() }}</p>
        } @else if (loading()) {
          <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
        } @else {
          <ul class="list">
            @for (a of items(); track a.id) {
              <li>
                <a class="row card-hover" [routerLink]="['/recursos', a.id]">
                  <span class="icon-tile amber" aria-hidden="true"><mat-icon>gavel</mat-icon></span>
                  <div class="main">
                    <strong>{{ a.legalName || 'Sem nome' }}</strong>
                    <span class="muted">
                      {{ a.protocol }} · {{ a.supplierTypeLabel }} · recurso em
                      {{ dateTime(a.filedAt) }}
                    </span>
                  </div>
                  <div class="side">
                    @if (a.dueDate) {
                      <span class="badge" [class]="a.overdue ? 'badge-error' : 'badge-warning'">
                        {{ a.overdue ? 'Prazo vencido em' : 'Decidir até' }} {{ date(a.dueDate) }}
                      </span>
                    } @else {
                      <span class="badge" [class]="badge[a.status]">{{ a.statusLabel }}</span>
                    }
                    <mat-icon class="chev" aria-hidden="true">chevron_right</mat-icon>
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
          @if (totalPages() > 1) {
            <nav class="pager" aria-label="Paginação">
              <button
                mat-stroked-button
                type="button"
                [disabled]="page() === 0"
                (click)="load(page() - 1)"
              >
                Anterior
              </button>
              <span>Página {{ page() + 1 }} de {{ totalPages() }}</span>
              <button
                mat-stroked-button
                type="button"
                [disabled]="page() + 1 >= totalPages()"
                (click)="load(page() + 1)"
              >
                Próxima
              </button>
            </nav>
          }
        }
      </section>
    </div>
  `,
  styles: `
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .links {
      display: flex;
      gap: 1.25rem;
      margin-top: 0.75rem !important;
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
    .tabs button:hover {
      border-color: var(--sh-primary);
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
    .count.hot,
    .active .count.hot {
      background: var(--sh-amber);
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
      align-items: center;
      gap: 0.5rem;
    }
    .chev {
      color: var(--sh-text-muted);
    }
    @media (max-width: 640px) {
      .row {
        flex-wrap: wrap;
      }
      .side {
        width: 100%;
      }
      .chev {
        display: none;
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
    .pager {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      margin-top: 1.25rem;
    }
  `,
})
export class AppealsPage {
  private readonly api = inject(AppealApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Query param `?etapa=`. Sem ele, abre na etapa de quem está logado. */
  readonly etapa = input<string>();

  protected readonly tabs = TABS;
  protected readonly badge = APPEAL_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly current = computed(() => {
    const bySlug = TABS.find((t) => t.slug === this.etapa());
    if (bySlug) {
      return bySlug;
    }
    const boardOnly = this.auth.hasRole('BOARD') && !this.auth.hasRole('COMMISSION', 'ADMIN');
    return boardOnly ? TABS[1] : TABS[0];
  });
  protected readonly counts = signal<Partial<Record<AppealStage, number>>>({});
  protected readonly items = signal<AppealSummary[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      this.current();
      untracked(() => this.load(0));
    });
  }

  /** Etapa em que o perfil logado precisa agir (destaca o contador). */
  protected isMine(stage: AppealStage): boolean {
    return (
      (stage === 'COMMISSION' && this.auth.hasRole('COMMISSION', 'ADMIN')) ||
      (stage === 'BOARD' && this.auth.hasRole('BOARD', 'ADMIN'))
    );
  }

  protected select(tab: Tab): void {
    void this.router.navigate([], { queryParams: { etapa: tab.slug } });
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set('');
    this.api.counts().subscribe({ next: (c) => this.counts.set(c) });
    this.api.list(this.current().stage, page).subscribe({
      next: (p) => {
        this.items.set(p.content);
        this.page.set(p.page);
        this.totalPages.set(p.totalPages);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }
}
