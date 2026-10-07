import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import {
  ContractApi,
  ORDER_BADGE,
  OrderStatus,
  OrderSummary,
  UrgencySummary,
} from '../../core/api/contract.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

interface Tab {
  slug: string;
  label: string;
  icon: string;
  statuses: OrderStatus[];
  empty: string;
}

const TABS: Tab[] = [
  {
    slug: 'preparacao',
    label: 'Em preparação',
    icon: 'edit_document',
    statuses: ['DRAFT', 'EXPIRED'],
    empty: 'Nenhuma ordem para preparar.',
  },
  {
    slug: 'assinatura',
    label: 'Aguardando assinatura',
    icon: 'draw',
    statuses: ['ISSUED'],
    empty: 'Nenhuma ordem aguardando assinatura.',
  },
  {
    slug: 'execucao',
    label: 'Em execução',
    icon: 'local_shipping',
    statuses: ['SIGNED', 'RECEIVED_WITH_RESERVATIONS'],
    empty: 'Nenhuma ordem em execução.',
  },
  {
    slug: 'pagamento',
    label: 'Nota e pagamento',
    icon: 'payments',
    statuses: ['RECEIVED', 'INVOICED'],
    empty: 'Nada a pagar.',
  },
  {
    slug: 'concluidas',
    label: 'Concluídas',
    icon: 'task_alt',
    statuses: ['PAID', 'REJECTED', 'CANCELLED'],
    empty: 'Ainda não há ordens concluídas.',
  },
];

/** Ordens de Fornecimento/Serviço: gestor, Comissão, Financeiro e consulta. */
@Component({
  selector: 'app-contracts-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container head">
        <div>
          <span class="eyebrow">Contratação</span>
          <h1>Ordens de fornecimento</h1>
          <p>
            Da oportunidade adjudicada ao pagamento: emitir a ordem, acompanhar a assinatura,
            registrar o recebimento e a avaliação, receber a nota fiscal e pagar.
          </p>
        </div>
        @if (canUrgent()) {
          <a mat-stroked-button routerLink="/contratos/urgencia">
            <mat-icon aria-hidden="true">priority_high</mat-icon> Contratação de urgência
          </a>
        }
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      @if (urgencies().length) {
        <section class="alert alert-warning urgencies" aria-labelledby="urg">
          <strong id="urg">Contratações de urgência aguardando a ciência da Comissão</strong>
          <ul>
            @for (u of urgencies(); track u.tenderId) {
              <li>
                <a [routerLink]="u.orderId ? ['/contratos', u.orderId] : null">
                  {{ u.number }} · {{ u.title }}</a
                >
                — {{ money(u.value) }} ·
                <span [class.late]="u.overdue">ciência até {{ date(u.ackDue) }}</span>
              </li>
            }
          </ul>
        </section>
      }
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
          @for (o of items(); track o.id) {
            <li>
              <a class="row card-hover" [routerLink]="['/contratos', o.id]">
                <span class="icon-tile" aria-hidden="true"><mat-icon>receipt_long</mat-icon></span>
                <div class="main">
                  <strong>{{ o.tenderTitle }}</strong>
                  <span class="muted">
                    {{ o.number || o.kindLabel + ' em preparação' }} · {{ o.tenderNumber }} ·
                    {{ o.supplierName }}
                  </span>
                </div>
                <div class="side">
                  <strong>{{ money(o.totalValue) }}</strong>
                  <span class="badge" [class]="badge[o.status]">{{ o.statusLabel }}</span>
                  @if (o.status === 'ISSUED' && o.signatureDueDate) {
                    <small class="muted">assinar até {{ date(o.signatureDueDate) }}</small>
                  } @else if (
                    (o.status === 'RECEIVED' || o.status === 'INVOICED') && o.paymentDueDate
                  ) {
                    <small class="muted">pagar até {{ date(o.paymentDueDate) }}</small>
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
    .urgencies {
      margin-bottom: 1rem;
    }
    .urgencies ul {
      margin: 0.5rem 0 0;
      padding-left: 1.2rem;
    }
    .late {
      color: var(--sh-error);
      font-weight: 700;
    }
  `,
})
export class ContractsPage {
  private readonly api = inject(ContractApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  /** Query param `?aba=`. */
  readonly aba = input<string>();

  protected readonly tabs = TABS;
  protected readonly badge = ORDER_BADGE;
  protected readonly date = formatDate;
  protected readonly money = formatMoney;

  protected readonly current = computed(() => TABS.find((t) => t.slug === this.aba()) ?? TABS[0]);
  protected readonly counts = signal<Partial<Record<OrderStatus, number>>>({});
  protected readonly items = signal<OrderSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly urgencies = signal<UrgencySummary[]>([]);
  protected readonly canUrgent = computed(() =>
    this.auth.hasRole('DEMAND_MANAGER', 'COMMISSION', 'ADMIN'),
  );

  constructor() {
    if (this.auth.hasRole('COMMISSION', 'ADMIN')) {
      this.api.pendingUrgencies().subscribe({ next: (u) => this.urgencies.set(u) });
    }
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
