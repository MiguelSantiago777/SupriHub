import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { DossierApi, DossierSample } from '../../core/api/control.api';
import { formatDate, formatDateTime, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** Controle Interno: revisão dos dossiês por amostragem (mínimo 10% ao semestre). */
@Component({
  selector: 'app-dossiers-page',
  imports: [
    FormsModule,
    RouterLink,
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
        <span class="eyebrow">Controle Interno</span>
        <h1>Revisão de dossiês</h1>
        <p>
          Contratações pagas no período. Revise pelo menos 10% por semestre: abra a ordem, confira o
          checklist do Anexo XIII e registre a revisão.
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <section class="card period">
        <mat-form-field>
          <mat-label>De</mat-label>
          <input matInput type="date" [(ngModel)]="from" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>Até</mat-label>
          <input matInput type="date" [(ngModel)]="to" />
        </mat-form-field>
        <button mat-flat-button type="button" (click)="load()">Atualizar</button>
      </section>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (!s()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let x = s()!;
        <p class="alert" [class]="x.reviewed >= x.minimum ? 'alert-success' : 'alert-warning'">
          {{ x.reviewed }} de {{ x.paid }} dossiê(s) revisado(s) — amostra mínima: {{ x.minimum }}.
        </p>
        @if (!x.rows.length) {
          <p class="muted">Nenhuma contratação paga no período.</p>
        } @else {
          <div class="table-wrap card">
            <table class="data">
              <thead>
                <tr>
                  <th scope="col">Ordem</th>
                  <th scope="col" class="num">Valor</th>
                  <th scope="col">Paga em</th>
                  <th scope="col">Revisão</th>
                </tr>
              </thead>
              <tbody>
                @for (r of x.rows; track r.orderId) {
                  <tr>
                    <td>
                      <a [routerLink]="['/contratos', r.orderId]">{{ r.orderNumber }}</a>
                    </td>
                    <td class="num">{{ money(r.value) }}</td>
                    <td>{{ date(r.paidOn) }}</td>
                    <td>
                      @if (r.reviewedAt) {
                        <span
                          class="badge"
                          [class]="r.complete ? 'badge-success' : 'badge-error'"
                          >{{ r.complete ? 'sem pendência' : 'com pendência' }}</span
                        >
                        <small class="muted"> {{ dateTime(r.reviewedAt) }}</small>
                      } @else {
                        <span class="muted">não revisado</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
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
    .num {
      text-align: right;
      white-space: nowrap;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
  `,
})
export class DossiersPage {
  private readonly api = inject(DossierApi);

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly s = signal<DossierSample | null>(null);
  protected readonly error = signal('');
  protected from = '';
  protected to = '';

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set('');
    this.api.sample(this.from || undefined, this.to || undefined).subscribe({
      next: (s) => {
        this.s.set(s);
        this.from = s.from;
        this.to = s.to;
      },
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }
}
