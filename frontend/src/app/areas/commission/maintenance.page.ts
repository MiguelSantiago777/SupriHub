import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import {
  CERTIFICATE_BADGE,
  MaintenanceRow,
  MaintenanceView,
  ReviewApi,
} from '../../core/api/supplier.api';
import { formatDate, formatDateTime } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

interface Tab {
  view: MaintenanceView;
  slug: string;
  label: string;
  icon: string;
  empty: string;
}

const TABS: Tab[] = [
  {
    view: 'CERTIFICATES',
    slug: 'certidoes',
    label: 'Certidões',
    icon: 'assignment_late',
    empty: 'Todas as certidões dos credenciados estão em dia.',
  },
  {
    view: 'RECHECK',
    slug: 'reconferencia',
    label: 'Reconferência semestral',
    icon: 'fact_check',
    empty: 'Nenhuma reconferência pendente.',
  },
  {
    view: 'RENEWAL',
    slug: 'renovacoes',
    label: 'Renovações',
    icon: 'autorenew',
    empty: 'Nenhum pedido de renovação.',
  },
  {
    view: 'INACTIVE',
    slug: 'inativos',
    label: 'Inativos',
    icon: 'pause_circle',
    empty: 'Nenhum credenciado inativo.',
  },
];

/** Manutenção do cadastro de credenciados (Manual item 3, passo 8; Política art. 21 a 23). */
@Component({
  selector: 'app-maintenance-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container head">
        <div>
          <span class="eyebrow">Comissão de Credenciamento</span>
          <h1>Manutenção do cadastro</h1>
          <p>
            Certidões a vencer e vencidas, reconferência a cada 6 meses e renovação aos 24 meses.
            Certidão vencida sem regularização em 5 dias úteis deixa o credenciado inativo até
            regularizar.
          </p>
        </div>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <div class="tabs" role="tablist" aria-label="Filas de manutenção">
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
            <span class="count">{{ counts()[t.view] ?? 0 }}</span>
          </button>
        }
      </div>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        <ul class="list">
          @for (r of items(); track r.id) {
            @let m = r.maintenance;
            <li>
              <a class="row card-hover" [routerLink]="['/comissao/requerimentos', r.id]">
                <div class="main">
                  <strong>{{ r.name }}</strong>
                  <span class="muted"
                    >{{ r.protocol }} · {{ r.supplierTypeLabel }} · {{ r.documentMasked }}</span
                  >
                  @if (current().view === 'CERTIFICATES' || current().view === 'INACTIVE') {
                    <span class="certs">
                      @for (c of m.certificates; track c.type) {
                        @if (c.state !== 'VALID') {
                          <span class="badge" [class]="certBadge[c.state]"
                            >{{ short(c.label) }}: {{ c.stateLabel
                            }}{{ c.validUntil ? ' ' + date(c.validUntil) : '' }}</span
                          >
                        }
                      }
                    </span>
                  }
                </div>
                <div class="side">
                  @switch (current().view) {
                    @case ('RECHECK') {
                      <span class="badge badge-warning"
                        >Reconferir até {{ date(m.nextRecheckDue) }}</span
                      >
                      @if (m.lastRecheckOn) {
                        <small class="muted">última em {{ date(m.lastRecheckOn) }}</small>
                      }
                    }
                    @case ('RENEWAL') {
                      <span class="badge badge-info"
                        >Pedido em {{ dateTime(m.renewalRequestedAt) }}</span
                      >
                      <small class="muted">vale até {{ date(r.accreditationValidUntil) }}</small>
                    }
                    @case ('INACTIVE') {
                      <span class="badge badge-error"
                        >Inativo desde {{ date(m.inactiveSince) }}</span
                      >
                      @if (m.deregistrationAlerted) {
                        <small class="muted">irregular há mais de 30 dias</small>
                      }
                    }
                    @default {
                      @if (m.inactiveSince) {
                        <span class="badge badge-error">Inativo</span>
                      } @else if (m.regularizationDue) {
                        <span class="badge badge-warning"
                          >Regularizar até {{ date(m.regularizationDue) }}</span
                        >
                      }
                      <small class="muted"
                        >credenciado até {{ date(r.accreditationValidUntil) }}</small
                      >
                    }
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
  styleUrl: '../shared/list-page.scss',
  styles: `
    .certs {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem;
      margin-top: 0.3rem;
    }
  `,
})
export class MaintenancePage {
  private readonly api = inject(ReviewApi);
  private readonly router = inject(Router);

  /** Query param `?fila=`. */
  readonly fila = input<string>();

  protected readonly tabs = TABS;
  protected readonly certBadge = CERTIFICATE_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly current = computed(() => TABS.find((t) => t.slug === this.fila()) ?? TABS[0]);
  protected readonly counts = signal<Partial<Record<MaintenanceView, number>>>({});
  protected readonly items = signal<MaintenanceRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      const tab = this.current();
      untracked(() => this.load(tab));
    });
  }

  /** "Certidão Negativa de Débitos Trabalhistas (CNDT)" → "CNDT"; sem sigla, o começo do nome. */
  protected short(label: string): string {
    const acronym = /\(([^)]+)\)/.exec(label)?.[1];
    if (acronym && acronym !== 'quando exigível') {
      return acronym;
    }
    if (label.includes('federais')) {
      return 'Federal';
    }
    return label.includes('Municipal') ? 'Municipal' : label;
  }

  protected select(t: Tab): void {
    void this.router.navigate([], {
      queryParams: { fila: t.slug === TABS[0].slug ? null : t.slug },
    });
  }

  private load(tab: Tab): void {
    this.loading.set(true);
    this.error.set('');
    this.api.maintenanceCounts().subscribe({ next: (c) => this.counts.set(c) });
    this.api.maintenance(tab.view).subscribe({
      next: (rows) => {
        this.items.set(rows);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }
}
