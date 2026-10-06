import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { IndicatorApi, IndicatorReport } from '../../core/api/control.api';
import { formatDate, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** Indicadores e metas do relatório semestral (Manual item 7; Política art. 27). */
@Component({
  selector: 'app-indicators-page',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AreaTabs,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Relatório semestral</span>
        <h1>Indicadores do Contrata + NATA</h1>
        <p>
          Base: ordens de fornecimento emitidas no período. As metas vêm dos parâmetros (aprovados
          pela Diretoria).
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <section class="card period" aria-label="Período">
        <mat-form-field>
          <mat-label>De</mat-label>
          <input matInput type="date" [(ngModel)]="from" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Até</mat-label>
          <input matInput type="date" [(ngModel)]="to" />
        </mat-form-field>
        <button mat-flat-button type="button" (click)="load()">Atualizar</button>
        <button mat-stroked-button type="button" (click)="print()">
          <mat-icon aria-hidden="true">print</mat-icon> Imprimir
        </button>
      </section>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (!r()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let d = r()!;
        <p class="summary">
          {{ date(d.from) }} a {{ date(d.to) }} ·
          <strong>{{ d.contracts }}</strong> contratação(ões) ·
          <strong>{{ money(d.totalValue) }}</strong> · {{ d.activeSuppliers }} credenciado(s)
          ativo(s)
        </p>

        <ul class="tiles">
          @for (i of d.indicators; track i.code) {
            <li class="card tile" [class.met]="i.met === true" [class.miss]="i.met === false">
              <span class="label">{{ i.label }}</span>
              <span class="value">{{ show(i.code, i.value) }}</span>
              <span class="goal">
                @if (i.met === true) {
                  <mat-icon aria-hidden="true">check_circle</mat-icon> Meta atingida
                } @else if (i.met === false) {
                  <mat-icon aria-hidden="true">error</mat-icon> Abaixo da meta
                } @else {
                  <mat-icon aria-hidden="true">remove_circle_outline</mat-icon> Sem dados
                }
                ({{ i.goalLabel }})
              </span>
              <span class="muted small">{{ i.formula }}</span>
              @if (i.note) {
                <span class="muted small">{{ i.note }}</span>
              }
            </li>
          }
        </ul>

        <div class="two">
          <section class="card">
            <h2>Por faixa</h2>
            <table class="data">
              <thead>
                <tr>
                  <th scope="col">Faixa</th>
                  <th scope="col" class="num">Ordens</th>
                  <th scope="col" class="num">Valor</th>
                  <th scope="col" class="num">DFD → OF (d.u.)</th>
                </tr>
              </thead>
              <tbody>
                @for (b of d.bands; track b.band) {
                  <tr>
                    <td>{{ b.label }}</td>
                    <td class="num">{{ b.count }}</td>
                    <td class="num">{{ money(b.value) }}</td>
                    <td class="num">{{ b.avgBusinessDays ?? '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
          <section class="card">
            <h2>Por projeto</h2>
            @if (!d.projects.length) {
              <p class="muted">Nenhuma contratação no período.</p>
            } @else {
              <table class="data">
                <thead>
                  <tr>
                    <th scope="col">Projeto</th>
                    <th scope="col" class="num">Ordens</th>
                    <th scope="col" class="num">Valor</th>
                  </tr>
                </thead>
                <tbody>
                  @for (p of d.projects; track p.project) {
                    <tr>
                      <td>{{ p.project }}</td>
                      <td class="num">{{ p.count }}</td>
                      <td class="num">{{ money(p.value) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </section>
        </div>

        <section class="card">
          <h2>Credenciados ativos por linha</h2>
          <p class="muted small">
            Meta: no mínimo 3 por linha em T1 (para a exclusividade e o rodízio).
          </p>
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th scope="col">Linha</th>
                  <th scope="col" class="num">Ativos</th>
                  <th scope="col" class="num">Atendem T1</th>
                </tr>
              </thead>
              <tbody>
                @for (l of d.lines; track l.code) {
                  <tr [class.low]="l.activeT1 < 3">
                    <td>{{ l.code }} · {{ l.name }}</td>
                    <td class="num">{{ l.active }}</td>
                    <td class="num">
                      {{ l.activeT1 }}
                      @if (l.activeT1 < 3) {
                        <span class="badge badge-warning">abaixo de 3</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
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
    .period {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 0.75rem;
      align-items: flex-start;
    }
    .summary {
      margin: 0;
    }
    .tiles {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    }
    .tile {
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
      border-left: 6px solid var(--sh-border);
    }
    .tile.met {
      border-left-color: var(--sh-green);
    }
    .tile.miss {
      border-left-color: var(--sh-error);
    }
    .label {
      font-weight: 700;
    }
    .value {
      font-family: var(--sh-font-heading);
      font-size: 2rem;
      font-weight: 800;
      color: var(--sh-navy);
    }
    .goal {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      font-weight: 700;
      mat-icon {
        width: 20px;
        height: 20px;
        font-size: 20px;
      }
    }
    .met .goal {
      color: var(--sh-green);
    }
    .miss .goal {
      color: var(--sh-error);
    }
    .small {
      font-size: 0.88rem;
    }
    .two {
      display: grid;
      gap: 1rem;
      @media (min-width: 900px) {
        grid-template-columns: 1fr 1fr;
      }
    }
    h2 {
      font-size: 1.15rem;
      margin: 0 0 0.5rem;
    }
    .num {
      text-align: right;
      white-space: nowrap;
    }
    tr.low td {
      background: var(--sh-amber-soft);
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
    @media print {
      .period {
        display: none;
      }
    }
  `,
})
export class IndicatorsPage {
  private readonly api = inject(IndicatorApi);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly r = signal<IndicatorReport | null>(null);
  protected readonly error = signal('');
  protected from = '';
  protected to = '';

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set('');
    this.api.report(this.from || undefined, this.to || undefined).subscribe({
      next: (r) => {
        this.r.set(r);
        this.from = r.from;
        this.to = r.to;
      },
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }

  protected show(code: string, v: number | null): string {
    if (v === null) {
      return '—';
    }
    if (code.startsWith('DAYS')) {
      return `${v.toLocaleString('pt-BR')} d.u.`;
    }
    if (code === 'RATING') {
      return v.toLocaleString('pt-BR', { minimumFractionDigits: 2 });
    }
    return `${v.toLocaleString('pt-BR')}%`;
  }

  protected print(): void {
    window.print();
  }
}
