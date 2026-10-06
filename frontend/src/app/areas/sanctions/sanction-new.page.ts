import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Infraction, SanctionApi } from '../../core/api/control.api';
import { AccreditedSupplier, AccreditedSupplierApi } from '../../core/api/supplier.api';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** Instauração do processo: notificação escrita com os fatos (Política art. 26). */
@Component({
  selector: 'app-sanction-new-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    AreaTabs,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/sancoes">← Processos sancionadores</a></p>
        <h1>Novo processo sancionador</h1>
        <p>
          O fornecedor recebe a notificação por e-mail e tem 5 dias úteis para se defender. Só
          credenciados podem ser processados.
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <section class="card form">
        @if (error()) {
          <div class="alert alert-error" role="alert">
            <p>
              <strong>{{ error() }}</strong>
            </p>
            @for (i of issues(); track i.field) {
              <p>{{ i.message }}</p>
            }
          </div>
        }

        @if (!supplier()) {
          <div class="search">
            <mat-form-field class="grow">
              <mat-label>Buscar credenciado (nome, CNPJ/CPF)</mat-label>
              <input matInput [(ngModel)]="q" (keyup.enter)="search()" />
            </mat-form-field>
            <button mat-stroked-button type="button" (click)="search()">Buscar</button>
          </div>
          <ul class="results">
            @for (s of results(); track s.id) {
              <li>
                <button mat-button type="button" (click)="supplier.set(s)">
                  {{ s.name }}
                </button>
                <span class="muted small">{{ s.documentMasked }} · {{ s.municipalityName }}</span>
              </li>
            }
          </ul>
        } @else {
          <p class="chosen">
            Fornecedor: <strong>{{ supplier()!.name }}</strong>
            @if (!fixedSupplier()) {
              <button mat-button type="button" (click)="supplier.set(null)">Trocar</button>
            }
          </p>
          @if (orderId()) {
            <p class="muted small">
              Ligado à ordem de fornecimento #{{ orderId() }} (multa só se prevista nela).
            </p>
          }
          <mat-form-field class="full">
            <mat-label>Infração (Anexo XII)</mat-label>
            <mat-select [(ngModel)]="infraction">
              @for (i of infractions(); track i.code) {
                <mat-option [value]="i.code">{{ i.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          @if (reference(); as r) {
            <p class="muted small">Sanção de referência: {{ r }}</p>
          }
          <mat-form-field class="full">
            <mat-label>Descrição dos fatos (vai na notificação)</mat-label>
            <textarea matInput rows="5" [(ngModel)]="facts" maxlength="5000"></textarea>
          </mat-form-field>
          <mat-checkbox [(ngModel)]="interimBlock">
            Risco iminente: inativar o cadastro desde já, até a decisão (art. 26)
          </mat-checkbox>
          <div class="btns">
            <button
              mat-flat-button
              type="button"
              [disabled]="busy() || !infraction || !facts.trim()"
              (click)="open()"
            >
              <mat-icon aria-hidden="true">send</mat-icon> Instaurar e notificar
            </button>
          </div>
        }
      </section>
    </div>
  `,
  styles: `
    .back {
      margin: 0 0 0.75rem !important;
    }
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .form {
      max-width: 820px;
      display: grid;
      gap: 0.5rem;
    }
    .search,
    .btns {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: flex-start;
    }
    .grow {
      flex: 1 1 280px;
    }
    .full {
      width: 100%;
    }
    .results {
      list-style: none;
      padding: 0;
      margin: 0;
      li {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.25rem 0.5rem;
      }
    }
    .small {
      font-size: 0.9rem;
    }
    .chosen {
      margin: 0;
    }
  `,
})
export class SanctionNewPage {
  private readonly api = inject(SanctionApi);
  private readonly suppliers = inject(AccreditedSupplierApi);
  private readonly router = inject(Router);

  /** Query params opcionais: fornecedor e ordem (vindo da ordem de fornecimento). */
  readonly applicationId = input<string>();
  readonly orderId = input<string>();

  protected readonly infractions = signal<Infraction[]>([]);
  protected readonly results = signal<AccreditedSupplier[]>([]);
  protected readonly supplier = signal<{ id: number; name: string } | null>(null);
  protected readonly fixedSupplier = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly issues = signal<{ field: string; message: string }[]>([]);
  protected q = '';
  protected infraction = '';
  protected facts = '';
  protected interimBlock = false;

  constructor() {
    this.api.infractions().subscribe({ next: (i) => this.infractions.set(i) });
    effect(() => {
      const id = this.applicationId();
      if (id) {
        untracked(() => {
          this.fixedSupplier.set(true);
          this.supplier.set({ id: Number(id), name: 'Fornecedor da ordem' });
        });
      }
    });
  }

  protected reference(): string | null {
    return this.infractions().find((i) => i.code === this.infraction)?.reference ?? null;
  }

  protected search(): void {
    this.suppliers.search('', null, this.q).subscribe({
      next: (p) => this.results.set(p.content),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }

  protected open(): void {
    const s = this.supplier();
    if (!s) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api
      .open({
        applicationId: s.id,
        serviceOrderId: this.orderId() ? Number(this.orderId()) : null,
        infraction: this.infraction,
        facts: this.facts,
        interimBlock: this.interimBlock,
      })
      .subscribe({
        next: (p) => void this.router.navigate(['/sancoes', p.id]),
        error: (e: unknown) => {
          this.busy.set(false);
          const p = toProblem(e);
          this.error.set(p.detail ?? problemMessage(e));
          this.issues.set(p.errors ?? []);
        },
      });
  }
}
