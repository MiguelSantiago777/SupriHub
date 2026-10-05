import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, map, of, startWith } from 'rxjs';
import { CatalogApi } from '../core/api/catalog.api';
import { SupplyLine } from '../core/models';

type LoadState =
  { status: 'loading' } | { status: 'error' } | { status: 'ok'; lines: SupplyLine[] };

const normalize = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

@Component({
  selector: 'app-catalog-page',
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="container">
      <h1>Catálogo de linhas de fornecimento</h1>
      <p class="muted">
        Anexo I do Edital de Credenciamento nº 001/2026. Você pode se credenciar em quantas linhas
        quiser. A marca <span class="badge badge-success">Aceita PF</span> indica as linhas que
        aceitam profissional autônomo (pessoa física, sem CNPJ).
      </p>

      <div class="filters card">
        <mat-form-field>
          <mat-label>Buscar por serviço ou produto</mat-label>
          <input
            matInput
            [ngModel]="query()"
            (ngModelChange)="query.set($event)"
            placeholder="Ex.: lanche, som, van"
          />
        </mat-form-field>
        <mat-checkbox [ngModel]="onlyPf()" (ngModelChange)="onlyPf.set($event)">
          Mostrar só linhas que aceitam pessoa física
        </mat-checkbox>
      </div>

      @switch (state().status) {
        @case ('loading') {
          <div class="center" role="status">
            <mat-spinner diameter="40" aria-label="Carregando catálogo" />
          </div>
        }
        @case ('error') {
          <p class="alert alert-error" role="alert">
            Não foi possível carregar o catálogo. Tente novamente.
          </p>
        }
        @default {
          <p aria-live="polite" class="muted">{{ filtered().length }} linha(s) encontrada(s).</p>
          <mat-accordion multi>
            @for (line of filtered(); track line.code) {
              <mat-expansion-panel>
                <mat-expansion-panel-header>
                  <mat-panel-title>
                    <span class="code">{{ line.code }}</span>
                    <span>{{ line.name }}</span>
                    @if (line.allowsIndividual) {
                      <span class="badge badge-success">Aceita PF</span>
                    }
                  </mat-panel-title>
                </mat-expansion-panel-header>
                <p><strong>O que inclui:</strong> {{ line.scope }}</p>
                <p><strong>CNAE de referência:</strong> {{ line.cnaeReference }}</p>
                <p>
                  <strong>Pessoa física (autônomo):</strong>
                  {{ line.allowsIndividual ? 'aceita' : 'não aceita' }}
                </p>
              </mat-expansion-panel>
            } @empty {
              <p>Nenhuma linha encontrada para a busca.</p>
            }
          </mat-accordion>
        }
      }
    </div>
  `,
  styles: `
    .filters {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-bottom: 1rem;
    }
    .code {
      font-weight: 700;
      margin-right: 0.5rem;
      color: var(--sh-primary);
    }
    .center {
      display: flex;
      justify-content: center;
      padding: 2rem;
    }
  `,
})
export class CatalogPage {
  private readonly api = inject(CatalogApi);

  protected readonly query = signal('');
  protected readonly onlyPf = signal(false);

  protected readonly state = toSignal(
    this.api.supplyLines().pipe(
      map((lines): LoadState => ({ status: 'ok', lines })),
      startWith<LoadState>({ status: 'loading' }),
      catchError(() => of<LoadState>({ status: 'error' })),
    ),
    { initialValue: { status: 'loading' } as LoadState },
  );

  protected readonly filtered = computed(() => {
    const s = this.state();
    if (s.status !== 'ok') return [];
    const q = normalize(this.query().trim());
    return s.lines.filter(
      (l) =>
        (!this.onlyPf() || l.allowsIndividual) &&
        (!q || normalize(`${l.code} ${l.name} ${l.scope}`).includes(q)),
    );
  });
}
