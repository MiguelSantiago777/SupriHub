import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import {
  SANCTION_BADGE,
  SanctionApi,
  SanctionStatus,
  SanctionSummary,
} from '../../core/api/control.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatDateTime } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

interface Tab {
  slug: string;
  label: string;
  icon: string;
  statuses: SanctionStatus[];
  empty: string;
}

const TABS: Tab[] = [
  {
    slug: 'andamento',
    label: 'Em andamento',
    icon: 'pending_actions',
    statuses: ['DEFENSE', 'AWAITING_DECISION', 'APPEAL_WINDOW'],
    empty: 'Nenhum processo em andamento.',
  },
  {
    slug: 'diretoria',
    label: 'Recurso na Diretoria',
    icon: 'gavel',
    statuses: ['BOARD_REVIEW'],
    empty: 'Nenhum recurso pendente.',
  },
  {
    slug: 'encerrados',
    label: 'Encerrados',
    icon: 'task_alt',
    statuses: ['FINAL'],
    empty: 'Nenhum processo encerrado.',
  },
];

/** Processos sancionadores (Política art. 24 a 26; Anexo XII). */
@Component({
  selector: 'app-sanctions-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container head">
        <div>
          <span class="eyebrow">Comissão de Credenciamento</span>
          <h1>Processos sancionadores</h1>
          <p>
            Notificação, defesa em 5 dias úteis, decisão motivada, recurso à Diretoria. Suspensão e
            descredenciamento tiram o fornecedor das oportunidades.
          </p>
        </div>
        @if (canOpen()) {
          <a mat-flat-button routerLink="/sancoes/novo">
            <mat-icon aria-hidden="true">add</mat-icon> Novo processo
          </a>
        }
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
          @for (p of items(); track p.id) {
            <li>
              <a class="row card-hover" [routerLink]="['/sancoes', p.id]">
                <span class="icon-tile" aria-hidden="true"><mat-icon>gavel</mat-icon></span>
                <div class="main">
                  <strong>{{ p.supplierName }}</strong>
                  <span class="muted">{{ p.number }} · {{ p.infractionLabel }}</span>
                </div>
                <div class="side">
                  <span class="badge" [class]="badge[p.status]">{{ p.statusLabel }}</span>
                  @if (p.nextDue) {
                    <small class="muted">prazo {{ date(p.nextDue) }}</small>
                  } @else if (p.finalSanctionLabel) {
                    <small class="muted"
                      >{{ p.finalSanctionLabel
                      }}{{ p.effectiveUntil ? ' até ' + date(p.effectiveUntil) : '' }}</small
                    >
                  }
                  <small class="muted">aberto em {{ dateTime(p.openedAt) }}</small>
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
  styleUrl: '../shared/list-page.scss',
})
export class SanctionsPage {
  private readonly api = inject(SanctionApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  /** Query param `?aba=`. */
  readonly aba = input<string>();

  protected readonly tabs = TABS;
  protected readonly badge = SANCTION_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly canOpen = computed(() => this.auth.hasRole('COMMISSION', 'ADMIN'));

  protected readonly current = computed(() => TABS.find((t) => t.slug === this.aba()) ?? TABS[0]);
  protected readonly counts = signal<Partial<Record<SanctionStatus, number>>>({});
  protected readonly items = signal<SanctionSummary[]>([]);
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
