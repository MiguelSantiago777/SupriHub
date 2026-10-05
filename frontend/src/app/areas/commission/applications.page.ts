import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import {
  ApplicationStatus,
  ApplicationSummary,
  ReviewApi,
  STATUS_BADGE,
} from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatDateTime } from '../../core/format';
import { problemMessage } from '../../core/http/problem';

interface Tab {
  status: ApplicationStatus;
  slug: string;
  label: string;
  icon: string;
  empty: string;
}

const TABS: Tab[] = [
  {
    status: 'SUBMITTED',
    slug: 'em-analise',
    label: 'Em análise',
    icon: 'pending_actions',
    empty: 'Nenhum requerimento aguardando análise. Tudo em dia!',
  },
  {
    status: 'ADJUSTMENT_REQUESTED',
    slug: 'ajuste',
    label: 'Aguardando ajuste',
    icon: 'edit_note',
    empty: 'Nenhum fornecedor está com ajuste pendente.',
  },
  {
    status: 'APPROVED',
    slug: 'habilitados',
    label: 'Habilitados',
    icon: 'verified',
    empty: 'Ainda não há fornecedores habilitados.',
  },
  {
    status: 'REJECTED',
    slug: 'inabilitados',
    label: 'Inabilitados',
    icon: 'block',
    empty: 'Nenhum requerimento inabilitado.',
  },
  {
    status: 'DRAFT',
    slug: 'em-preenchimento',
    label: 'Em preenchimento',
    icon: 'draw',
    empty: 'Ninguém está preenchendo o requerimento agora.',
  },
];

@Component({
  selector: 'app-applications-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Comissão de Credenciamento</span>
        <h1>Requerimentos de credenciamento</h1>
        <p>
          Analise os requerimentos em até 10 dias úteis (Edital 4.3): habilite, peça ajuste ou
          inabilite com motivo. O fornecedor é avisado por e-mail a cada decisão.
        </p>
        <p class="back">
          <a routerLink="/recursos">Recursos contra inabilitação</a>
          @if (auth.hasRole('ADMIN')) {
            · <a routerLink="/admin">Administração</a>
          }
        </p>
      </div>
    </header>

    <div class="container page">
      <div class="tabs" role="tablist" aria-label="Situação dos requerimentos">
        @for (t of tabs; track t.status) {
          <button
            type="button"
            role="tab"
            [attr.aria-selected]="t.status === current().status"
            [class.active]="t.status === current().status"
            (click)="select(t)"
          >
            <mat-icon aria-hidden="true">{{ t.icon }}</mat-icon>
            <span>{{ t.label }}</span>
            <span
              class="count"
              [class.hot]="t.status === 'SUBMITTED' && (counts()[t.status] ?? 0) > 0"
              >{{ counts()[t.status] ?? 0 }}</span
            >
          </button>
        }
      </div>

      <form class="search" role="search" (submit)="$event.preventDefault(); load(0)">
        <mat-form-field>
          <mat-label>Buscar por nome, protocolo, CNPJ ou CPF</mat-label>
          <mat-icon matPrefix aria-hidden="true">search</mat-icon>
          <input matInput [value]="query()" (input)="query.set($any($event.target).value)" />
        </mat-form-field>
        <button mat-stroked-button type="submit">Buscar</button>
      </form>

      <section role="tabpanel" [attr.aria-label]="current().label" aria-live="polite">
        @if (error()) {
          <p class="alert alert-error" role="alert">{{ error() }}</p>
        } @else if (loading()) {
          <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
        } @else {
          <ul class="list">
            @for (a of items(); track a.id) {
              <li>
                <a class="row card-hover" [routerLink]="['/comissao/requerimentos', a.id]">
                  <div class="main">
                    <div class="title">
                      <strong>{{ a.legalName || 'Sem nome' }}</strong>
                      @if (a.tradeName) {
                        <span class="muted">{{ a.tradeName }}</span>
                      }
                    </div>
                    <div class="meta">
                      @if (a.protocol) {
                        <span><mat-icon aria-hidden="true">tag</mat-icon>{{ a.protocol }}</span>
                      }
                      @if (a.supplierTypeLabel) {
                        <span
                          ><mat-icon aria-hidden="true">storefront</mat-icon
                          >{{ a.supplierTypeLabel }}</span
                        >
                      }
                      @if (a.documentMasked) {
                        <span
                          ><mat-icon aria-hidden="true">badge</mat-icon>{{ a.documentMasked }}</span
                        >
                      }
                      @if (a.municipalityName) {
                        <span
                          ><mat-icon aria-hidden="true">place</mat-icon
                          >{{ a.municipalityName }}</span
                        >
                      }
                      @if (a.womanLed) {
                        <span class="badge badge-info">liderado por mulher</span>
                      }
                    </div>
                    @if (a.lineCodes.length) {
                      <div class="lines" aria-label="Linhas">
                        @for (c of a.lineCodes; track c) {
                          <span>{{ c }}</span>
                        }
                      </div>
                    }
                  </div>
                  <div class="side">
                    @switch (a.status) {
                      @case ('SUBMITTED') {
                        <span class="badge" [class]="a.overdue ? 'badge-error' : 'badge-info'">
                          {{ a.overdue ? 'Prazo vencido' : 'Decidir até' }}
                          {{ date(a.analysisDueDate) }}
                        </span>
                        <small class="muted">Enviado em {{ dateTime(a.submittedAt) }}</small>
                      }
                      @case ('ADJUSTMENT_REQUESTED') {
                        <span class="badge" [class]="a.overdue ? 'badge-error' : 'badge-warning'">
                          {{ a.overdue ? 'Ajuste vencido em' : 'Ajuste até' }}
                          {{ date(a.adjustmentDueDate) }}
                        </span>
                      }
                      @case ('APPROVED') {
                        <span class="badge badge-success"
                          >Válido até {{ date(a.accreditationValidUntil) }}</span
                        >
                      }
                      @default {
                        <span class="badge" [class]="badge[a.status]">{{ current().label }}</span>
                      }
                    }
                    <mat-icon class="chev" aria-hidden="true">chevron_right</mat-icon>
                  </div>
                </a>
              </li>
            } @empty {
              <li class="empty">
                <mat-icon aria-hidden="true">inbox</mat-icon>
                <p>{{ query() ? 'Nenhum resultado para a busca.' : current().empty }}</p>
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
    .back {
      margin-top: 0.75rem !important;
    }
    .tabs {
      display: flex;
      gap: 0.5rem;
      overflow-x: auto;
      padding-bottom: 0.25rem;
      margin-bottom: 1rem;
    }
    @media (min-width: 900px) {
      .tabs {
        flex-wrap: wrap;
        overflow: visible;
      }
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
      white-space: nowrap;
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
    .count.hot {
      background: var(--sh-amber);
      color: #fff;
    }
    .search {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
      flex-wrap: wrap;
    }
    .search mat-form-field {
      flex: 1 1 320px;
    }
    .search button {
      margin-top: 4px;
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
      margin: 0.5rem 0 0;
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
      display: grid;
      gap: 0.4rem;
    }
    .title {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
      align-items: baseline;
      font-size: 1.05rem;
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 1rem;
      color: var(--sh-text-muted);
      font-size: 0.9rem;
    }
    .meta > span:not(.badge) {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }
    .meta mat-icon {
      width: 16px;
      height: 16px;
      font-size: 16px;
    }
    .lines {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem;
    }
    .lines span {
      font-size: 0.78rem;
      font-weight: 700;
      padding: 0.05rem 0.5rem;
      border-radius: 6px;
      background: var(--sh-primary-soft);
      color: var(--sh-primary-hover);
    }
    .side {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 0.3rem;
      text-align: right;
    }
    .chev {
      color: var(--sh-text-muted);
    }
    @media (max-width: 640px) {
      .row {
        flex-direction: column;
        align-items: stretch;
      }
      .side {
        align-items: flex-start;
        text-align: left;
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
export class ApplicationsPage {
  private readonly api = inject(ReviewApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Query param `?situacao=` (mantém a aba ao voltar do detalhe). */
  readonly situacao = input<string>();

  protected readonly tabs = TABS;
  protected readonly badge = STATUS_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly current = computed(
    () => TABS.find((t) => t.slug === this.situacao()) ?? TABS[0],
  );
  protected readonly counts = signal<Partial<Record<ApplicationStatus, number>>>({});
  protected readonly items = signal<ApplicationSummary[]>([]);
  protected readonly query = signal('');
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

  protected select(tab: Tab): void {
    void this.router.navigate([], {
      queryParams: { situacao: tab.slug === TABS[0].slug ? null : tab.slug },
    });
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set('');
    this.api.counts().subscribe({ next: (c) => this.counts.set(c) });
    this.api.list(this.current().status, this.query(), page).subscribe({
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
