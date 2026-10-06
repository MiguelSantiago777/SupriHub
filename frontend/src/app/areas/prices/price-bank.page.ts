import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CatalogApi } from '../../core/api/catalog.api';
import { PriceBankApi, PriceItem, PriceSource } from '../../core/api/control.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatMoney } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { SupplyLine } from '../../core/models';
import { AreaTabs } from '../shared/area-tabs';

function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Banco de Preços (aba da Planilha; Manual 4.1 e 4.2, passo 1). */
@Component({
  selector: 'app-price-bank-page',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    AreaTabs,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Contratação</span>
        <h1>Banco de Preços</h1>
        <p>
          Preços praticados (entram sozinhos no recebimento definitivo) e cotados. Use para estimar
          o valor da demanda e conferir o preço do rodízio — o teto da Faixa 1 é a média +
          {{ marginLabel }}.
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <section class="card filters" aria-label="Filtros">
        <mat-form-field>
          <mat-label>Linha</mat-label>
          <mat-select [(ngModel)]="line" (selectionChange)="search()">
            <mat-option [value]="null">Todas</mat-option>
            @for (l of lines(); track l.code) {
              <mat-option [value]="l.code">{{ l.code }} · {{ l.shortName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field class="grow">
          <mat-label>Item</mat-label>
          <input matInput [(ngModel)]="q" (keyup.enter)="search()" placeholder="ex.: kit lanche" />
        </mat-form-field>
        <button mat-flat-button type="button" (click)="search()">
          <mat-icon aria-hidden="true">search</mat-icon> Buscar
        </button>
        @if (canAdd()) {
          <button mat-stroked-button type="button" (click)="adding.set(!adding())">
            <mat-icon aria-hidden="true">add</mat-icon> Lançar cotação
          </button>
        }
      </section>

      @if (adding()) {
        <section class="card add" aria-labelledby="pb-add">
          <h2 id="pb-add">Lançar preço</h2>
          <p class="muted small">
            Use a mesma descrição e unidade dos itens das oportunidades — é assim que o sistema
            encontra a referência na conferência do rodízio.
          </p>
          @if (addError()) {
            <div class="alert alert-error" role="alert">
              <p>
                <strong>{{ addError() }}</strong>
              </p>
              @for (i of addIssues(); track i.field) {
                <p>{{ i.message }}</p>
              }
            </div>
          }
          <div class="grid">
            <mat-form-field>
              <mat-label>Linha</mat-label>
              <mat-select [(ngModel)]="form.lineCode">
                @for (l of lines(); track l.code) {
                  <mat-option [value]="l.code">{{ l.code }} · {{ l.shortName }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field class="wide">
              <mat-label>Item (descrição padronizada)</mat-label>
              <input matInput [(ngModel)]="form.description" maxlength="500" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Unidade</mat-label>
              <input matInput [(ngModel)]="form.unit" maxlength="30" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Preço unitário (R$)</mat-label>
              <input matInput type="number" step="0.01" [(ngModel)]="form.unitPrice" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Fonte</mat-label>
              <mat-select [(ngModel)]="form.source">
                <mat-option value="QUOTATION">Cotação</mat-option>
                <mat-option value="PRICE_PANEL">Painel de Preços</mat-option>
                <mat-option value="RESEARCH">Pesquisa de mercado</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Data do preço</mat-label>
              <input matInput type="date" [(ngModel)]="form.observedOn" [max]="todayIso" />
            </mat-form-field>
            <mat-form-field class="wide">
              <mat-label>Referência (fornecedor, link, nº da cotação)</mat-label>
              <input matInput [(ngModel)]="form.reference" maxlength="100" />
            </mat-form-field>
          </div>
          <button mat-flat-button type="button" [disabled]="busy()" (click)="add()">Lançar</button>
        </section>
      }

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else if (!items().length) {
        <section class="card empty">
          <mat-icon aria-hidden="true">inbox</mat-icon>
          <p>Nenhum preço encontrado.</p>
        </section>
      } @else {
        <div class="table-wrap card">
          <table class="data">
            <caption class="visually-hidden">
              Preços por item
            </caption>
            <thead>
              <tr>
                <th scope="col">Item</th>
                <th scope="col">Linha</th>
                <th scope="col" class="num">Registros</th>
                <th scope="col" class="num">Menor</th>
                <th scope="col" class="num">Média</th>
                <th scope="col" class="num">Teto (rodízio)</th>
                <th scope="col">Último</th>
                <th scope="col"><span class="visually-hidden">Detalhes</span></th>
              </tr>
            </thead>
            <tbody>
              @for (i of items(); track $index) {
                <tr>
                  <td>
                    <strong>{{ i.description }}</strong> <span class="muted">({{ i.unit }})</span>
                  </td>
                  <td>{{ i.lineCode }}</td>
                  <td class="num">{{ i.count }}</td>
                  <td class="num">{{ money(i.min) }}</td>
                  <td class="num">{{ money(i.average) }}</td>
                  <td class="num">
                    <strong>{{ money(i.ceiling) }}</strong>
                  </td>
                  <td>{{ date(i.lastObserved) }}</td>
                  <td>
                    <button
                      mat-icon-button
                      type="button"
                      (click)="toggle($index)"
                      [attr.aria-expanded]="open() === $index"
                      [attr.aria-label]="'Registros de ' + i.description"
                    >
                      <mat-icon>{{ open() === $index ? 'expand_less' : 'expand_more' }}</mat-icon>
                    </button>
                  </td>
                </tr>
                @if (open() === $index) {
                  <tr class="details">
                    <td colspan="8">
                      <ul>
                        @for (e of i.entries; track e.id) {
                          <li>
                            {{ date(e.observedOn) }} — <strong>{{ money(e.unitPrice) }}</strong> ·
                            {{ e.sourceLabel }}{{ e.reference ? ' · ' + e.reference : '' }}
                          </li>
                        }
                      </ul>
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: `
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
      display: grid;
      gap: 1rem;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 0.75rem;
      align-items: flex-start;
    }
    .grow {
      flex: 1 1 260px;
    }
    .add h2 {
      font-size: 1.15rem;
      margin: 0 0 0.35rem;
    }
    .grid {
      display: grid;
      gap: 0 0.75rem;
      @media (min-width: 760px) {
        grid-template-columns: repeat(4, 1fr);
        .wide {
          grid-column: span 2;
        }
      }
    }
    .num {
      text-align: right;
      white-space: nowrap;
    }
    .details td {
      background: var(--sh-bg);
    }
    .details ul {
      margin: 0;
      padding-left: 1.2rem;
    }
    .small {
      font-size: 0.9rem;
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
      p {
        margin: 0;
      }
    }
  `,
})
export class PriceBankPage {
  private readonly api = inject(PriceBankApi);
  private readonly catalog = inject(CatalogApi);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly todayIso = today();
  protected readonly marginLabel = '10%';

  protected readonly lines = signal<SupplyLine[]>([]);
  protected readonly items = signal<PriceItem[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly open = signal<number | null>(null);
  protected readonly adding = signal(false);
  protected readonly busy = signal(false);
  protected readonly addError = signal('');
  protected readonly addIssues = signal<{ field: string; message: string }[]>([]);
  protected readonly canAdd = computed(() =>
    this.auth.hasRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'ADMIN'),
  );

  protected line: string | null = null;
  protected q = '';
  protected form = {
    lineCode: '',
    description: '',
    unit: '',
    unitPrice: null as number | null,
    source: 'QUOTATION' as PriceSource,
    reference: '',
    observedOn: today(),
  };

  constructor() {
    this.catalog.supplyLines().subscribe({ next: (l) => this.lines.set(l) });
    this.search();
  }

  protected search(): void {
    this.loading.set(true);
    this.error.set('');
    this.open.set(null);
    this.api.search(this.line, this.q).subscribe({
      next: (i) => {
        this.items.set(i);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected toggle(i: number): void {
    this.open.set(this.open() === i ? null : i);
  }

  protected add(): void {
    this.busy.set(true);
    this.addError.set('');
    this.addIssues.set([]);
    this.api.add({ ...this.form, reference: this.form.reference.trim() || null }).subscribe({
      next: () => {
        this.busy.set(false);
        this.adding.set(false);
        this.snack.open('Preço lançado.', 'OK', { duration: 4000 });
        this.form = { ...this.form, description: '', unitPrice: null, reference: '' };
        this.search();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const p = toProblem(e);
        this.addError.set(p.detail ?? problemMessage(e));
        this.addIssues.set(p.errors ?? []);
      },
    });
  }
}
