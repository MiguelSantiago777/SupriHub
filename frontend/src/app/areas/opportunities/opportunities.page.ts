import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { Opportunity, OpportunityApi } from '../../core/api/tender.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatDateTime, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** "Editais em aberto": oportunidades da linha/território do fornecedor e convites do rodízio. */
@Component({
  selector: 'app-opportunities-page',
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Área do fornecedor</span>
        <h1>Editais em aberto</h1>
        <p>
          Oportunidades de compra do Instituto na sua linha de fornecimento e no seu território.
          Envie sua proposta pelo sistema até o prazo.
        </p>
      </div>
    </header>
    <app-area-tabs area="supplier" [badge]="pendingCount()" badgeLink="/fornecedor/editais" />

    <div class="container page">
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @if (invitations().length) {
          <section aria-labelledby="t-conv">
            <h2 id="t-conv">
              <mat-icon aria-hidden="true">notifications_active</mat-icon> Convites para você
            </h2>
            <ul class="grid">
              @for (o of invitations(); track o.id) {
                <li>
                  <a class="card card-hover op invite" [routerLink]="['/fornecedor/editais', o.id]">
                    <span class="badge badge-warning"
                      >Rodízio · responda até {{ date(o.invitation!.dueDate) }}</span
                    >
                    <h3>{{ o.title }}</h3>
                    <span class="muted"
                      >{{ o.number }} · {{ o.line.code }} {{ o.line.shortName }}</span
                    >
                    <span class="cta"
                      >Responder ao convite
                      <mat-icon aria-hidden="true">arrow_forward</mat-icon></span
                    >
                  </a>
                </li>
              }
            </ul>
          </section>
        }

        <section aria-labelledby="t-abertos">
          <h2 id="t-abertos"><mat-icon aria-hidden="true">campaign</mat-icon> Abertos para você</h2>
          <ul class="grid">
            @for (o of open(); track o.id) {
              <li>
                <a class="card card-hover op" [routerLink]="['/fornecedor/editais', o.id]">
                  <div class="top">
                    <span class="badge badge-info">{{ o.bandLabel }} · {{ o.selectionMode }}</span>
                    @if (o.proposal) {
                      <span class="badge badge-success">
                        <mat-icon aria-hidden="true">check</mat-icon> Proposta enviada
                      </span>
                    }
                  </div>
                  <h3>{{ o.title }}</h3>
                  <span class="muted"
                    >{{ o.number }} · {{ o.line.code }} {{ o.line.shortName }}</span
                  >
                  <ul class="facts">
                    @if (o.location || o.executionMunicipalityName) {
                      <li>
                        <mat-icon aria-hidden="true">place</mat-icon>
                        {{ o.location || o.executionMunicipalityName }}
                      </li>
                    }
                    <li>
                      <mat-icon aria-hidden="true">inventory_2</mat-icon>
                      {{ o.items.length }} {{ o.items.length === 1 ? 'item' : 'itens' }}
                    </li>
                    <li class="deadline">
                      <mat-icon aria-hidden="true">schedule</mat-icon>
                      Propostas até <strong>{{ dateTime(o.proposalDeadline) }}</strong>
                    </li>
                  </ul>
                  <span class="cta">
                    {{ o.proposal ? 'Ver ou alterar proposta' : 'Enviar proposta' }}
                    <mat-icon aria-hidden="true">arrow_forward</mat-icon>
                  </span>
                </a>
              </li>
            } @empty {
              <li class="empty">
                <mat-icon aria-hidden="true">inbox</mat-icon>
                <p>
                  @if (auth.user()) {
                    Nenhum edital aberto para a sua linha e território agora. Você recebe um e-mail
                    quando abrir um novo.
                  }
                </p>
                <a routerLink="/fornecedor">Conferir minhas linhas e territórios</a>
              </li>
            }
          </ul>
        </section>

        @if (history().length) {
          <section aria-labelledby="t-hist">
            <h2 id="t-hist">
              <mat-icon aria-hidden="true">history</mat-icon> Encerrados e respondidos
            </h2>
            <ul class="rows">
              @for (o of history(); track o.id) {
                <li>
                  <a [routerLink]="['/fornecedor/editais', o.id]" class="row">
                    <div>
                      <strong>{{ o.title }}</strong>
                      <span class="muted">{{ o.number }} · {{ o.line.code }}</span>
                    </div>
                    <div class="right">
                      @if (o.proposal) {
                        <span
                          >Sua proposta: <strong>{{ money(o.proposal.totalValue) }}</strong></span
                        >
                      }
                      <span class="badge badge-neutral">{{ historyLabel(o) }}</span>
                    </div>
                  </a>
                </li>
              }
            </ul>
          </section>
        }
      }
    </div>
  `,
  styles: `
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
      display: grid;
      gap: 1.75rem;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.25rem;
      margin: 0 0 0.85rem;
    }
    h2 mat-icon {
      color: var(--sh-primary);
    }
    .grid {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    }
    .op {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
      height: 100%;
      box-sizing: border-box;
      color: var(--sh-text);
      text-decoration: none;
    }
    .op.invite {
      border-color: #f0d48f;
      background: linear-gradient(180deg, var(--sh-amber-soft), var(--sh-surface) 60%);
    }
    .op h3 {
      font-size: 1.15rem;
      margin: 0.25rem 0 0;
    }
    .top {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
    }
    .top .badge mat-icon {
      width: 16px;
      height: 16px;
      font-size: 16px;
    }
    .facts {
      list-style: none;
      padding: 0;
      margin: 0.25rem 0 0;
      display: grid;
      gap: 0.25rem;
      color: var(--sh-text-muted);
      font-size: 0.92rem;
    }
    .facts li {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .facts mat-icon {
      width: 18px;
      height: 18px;
      font-size: 18px;
    }
    .deadline {
      color: var(--sh-text);
    }
    .cta {
      margin-top: auto;
      padding-top: 0.5rem;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      font-weight: 700;
      color: var(--sh-primary);
    }
    .cta mat-icon {
      width: 20px;
      height: 20px;
      font-size: 20px;
    }
    .empty {
      grid-column: 1 / -1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      padding: 2.5rem 1rem;
      color: var(--sh-text-muted);
      border: 2px dashed var(--sh-border);
      border-radius: var(--sh-radius);
      text-align: center;
    }
    .empty mat-icon {
      width: 40px;
      height: 40px;
      font-size: 40px;
    }
    .rows {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.5rem;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 0.75rem;
      padding: 0.85rem 1rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
      background: var(--sh-surface);
      color: var(--sh-text);
      text-decoration: none;
    }
    .row div {
      display: flex;
      flex-direction: column;
    }
    .row .right {
      flex-direction: row;
      align-items: center;
      gap: 0.75rem;
    }
  `,
})
export class OpportunitiesPage implements OnInit {
  private readonly api = inject(OpportunityApi);
  protected readonly auth = inject(AuthService);

  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly money = formatMoney;

  protected readonly items = signal<Opportunity[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly invitations = computed(() =>
    this.items().filter((o) => o.band === 'FAIXA_1' && o.canSubmit),
  );
  protected readonly open = computed(() =>
    this.items().filter((o) => o.band !== 'FAIXA_1' && o.canSubmit),
  );
  protected readonly history = computed(() => this.items().filter((o) => !o.canSubmit));
  protected readonly pendingCount = computed(
    () => this.invitations().length + this.open().filter((o) => !o.proposal).length,
  );

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected historyLabel(o: Opportunity): string {
    if (o.invitation) {
      return o.invitation.statusLabel;
    }
    if (o.status === 'CANCELLED') {
      return 'Cancelado';
    }
    return o.deadlinePassed ? 'Prazo encerrado — em julgamento' : 'Encerrado';
  }
}
