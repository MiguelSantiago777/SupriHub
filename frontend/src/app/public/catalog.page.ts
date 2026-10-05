import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';
import { CatalogApi } from '../core/api/catalog.api';
import { SupplyLine } from '../core/models';

type LoadState =
  { status: 'loading' } | { status: 'error' } | { status: 'ok'; lines: SupplyLine[] };

/** Ícone ilustrativo por linha (puramente visual). */
const LINE_ICONS: Record<string, string> = {
  L01: 'restaurant',
  L02: 'outdoor_grill',
  L03: 'music_note',
  L04: 'festival',
  L05: 'chair',
  L06: 'celebration',
  L07: 'palette',
  L08: 'fitness_center',
  L09: 'photo_camera',
  L10: 'print',
  L11: 'checkroom',
  L12: 'brush',
  L13: 'school',
  L14: 'directions_bus',
  L15: 'build',
  L16: 'cleaning_services',
  L17: 'medical_services',
  L18: 'computer',
  L19: 'local_florist',
  L20: 'content_cut',
};

const normalize = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

@Component({
  selector: 'app-catalog-page',
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Anexo I · Edital nº 001/2026</span>
        <h1>Catálogo de linhas de fornecimento</h1>
        <p>
          Você pode se credenciar em quantas linhas quiser. As marcadas com
          <span class="badge badge-success">Aceita PF</span> aceitam profissional autônomo, sem
          CNPJ.
        </p>
      </div>
    </header>

    <div class="container">
      <div class="toolbar">
        <label class="search">
          <span class="visually-hidden">Buscar por serviço ou produto</span>
          <mat-icon aria-hidden="true">search</mat-icon>
          <input
            type="search"
            [value]="query()"
            (input)="query.set($any($event.target).value)"
            placeholder="Buscar: lanche, som, van, costura…"
          />
        </label>
        <div class="filters" role="group" aria-label="Filtrar linhas">
          <button
            type="button"
            class="chip"
            [attr.aria-pressed]="!onlyPf()"
            (click)="onlyPf.set(false)"
          >
            Todas
          </button>
          <button
            type="button"
            class="chip"
            [attr.aria-pressed]="onlyPf()"
            (click)="onlyPf.set(true)"
          >
            <mat-icon aria-hidden="true">person</mat-icon> Aceitam pessoa física
          </button>
        </div>
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
          <p aria-live="polite" class="muted count">
            {{ filtered().length }} linha(s) encontrada(s).
          </p>
          <ul class="grid">
            @for (line of filtered(); track line.code) {
              <li class="card card-hover line">
                <div class="line-top">
                  <span class="icon-tile"
                    ><mat-icon aria-hidden="true">{{ icon(line.code) }}</mat-icon></span
                  >
                  <span class="code">{{ line.code }}</span>
                  @if (line.allowsIndividual) {
                    <span class="badge badge-success">Aceita PF</span>
                  }
                </div>
                <h2>{{ line.name }}</h2>
                <p class="muted">{{ line.scope }}</p>
                <details>
                  <summary>CNAE de referência</summary>
                  <p>{{ line.cnaeReference }}</p>
                </details>
              </li>
            } @empty {
              <li class="empty card">
                <mat-icon aria-hidden="true">search_off</mat-icon>
                <p>Nenhuma linha encontrada para a busca.</p>
              </li>
            }
          </ul>
          <div class="cta card">
            <div>
              <h2>Encontrou sua linha?</h2>
              <p class="muted">Crie sua conta e comece o credenciamento. É gratuito.</p>
            </div>
            <a routerLink="/criar-conta" class="cta-link">
              Quero me credenciar <mat-icon aria-hidden="true">arrow_forward</mat-icon>
            </a>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      margin-bottom: 1rem;
    }
    .search {
      flex: 1 1 320px;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0 1rem;
      height: 56px;
      background: var(--sh-surface);
      border: 1px solid var(--sh-border-strong);
      border-radius: 999px;
      color: var(--sh-text-muted);
    }
    .search:focus-within {
      border-color: var(--sh-primary);
      box-shadow: 0 0 0 3px var(--sh-primary-soft);
    }
    .search input {
      flex: 1;
      border: 0;
      outline: none !important;
      box-shadow: none !important;
      font: inherit;
      color: var(--sh-text);
      background: transparent;
      min-width: 0;
    }
    .filters {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      height: 48px;
      padding: 0 1.1rem;
      border-radius: 999px;
      border: 1px solid var(--sh-border-strong);
      background: var(--sh-surface);
      color: var(--sh-text);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    .chip[aria-pressed='true'] {
      background: var(--sh-navy);
      border-color: var(--sh-navy);
      color: #fff;
    }
    .chip mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .count {
      margin: 0.5rem 0 1rem;
    }
    .grid {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
    }
    .line {
      display: flex;
      flex-direction: column;
    }
    .line h2 {
      font-size: 1.12rem;
      margin: 0.85rem 0 0.4rem;
    }
    .line p {
      font-size: 0.95rem;
      flex: 1;
    }
    .line-top {
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }
    .code {
      font-family: var(--sh-font-heading);
      font-weight: 800;
      color: var(--sh-primary);
      margin-right: auto;
    }
    details {
      border-top: 1px solid var(--sh-border);
      padding-top: 0.6rem;
      font-size: 0.9rem;
    }
    summary {
      cursor: pointer;
      font-weight: 700;
      color: var(--sh-primary);
    }
    details p {
      margin: 0.4rem 0 0;
      color: var(--sh-text-muted);
    }
    .empty {
      grid-column: 1 / -1;
      text-align: center;
      color: var(--sh-text-muted);
    }
    .center {
      display: flex;
      justify-content: center;
      padding: 3rem;
    }
    .cta {
      margin-top: 1.5rem;
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      align-items: center;
      justify-content: space-between;
    }
    .cta h2 {
      font-size: 1.25rem;
    }
    .cta p {
      margin: 0;
    }
    .cta-link {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      font-weight: 700;
      text-decoration: none;
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

  protected icon(code: string): string {
    return LINE_ICONS[code] ?? 'category';
  }
}
