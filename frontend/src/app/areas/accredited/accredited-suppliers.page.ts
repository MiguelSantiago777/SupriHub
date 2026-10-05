import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import {
  AccreditedSupplier,
  AccreditedSupplierApi,
  Territory,
  TERRITORIES,
} from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDate, formatPhone } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { SupplyLine } from '../../core/models';

/** Cadastro de credenciados — uso interno da equipe do Instituto (D-027). */
@Component({
  selector: 'app-accredited-suppliers-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Uso interno</span>
        <h1>Fornecedores credenciados</h1>
        <p>
          Quem está habilitado e com o credenciamento em vigor, por linha de fornecimento e
          território. Use para encontrar e contatar fornecedores para as demandas dos projetos.
        </p>
      </div>
    </header>

    <div class="container page">
      <form class="toolbar" role="search" (submit)="$event.preventDefault(); load(0)">
        <label class="search">
          <span class="visually-hidden">Buscar pelo nome</span>
          <mat-icon aria-hidden="true">search</mat-icon>
          <input
            type="search"
            [value]="query()"
            (input)="query.set($any($event.target).value)"
            placeholder="Buscar pelo nome do negócio"
          />
        </label>
        <label class="select">
          <span class="visually-hidden">Linha de fornecimento</span>
          <mat-icon aria-hidden="true">category</mat-icon>
          <select [value]="line()" (change)="line.set($any($event.target).value); load(0)">
            <option value="">Todas as linhas</option>
            @for (l of lines(); track l.code) {
              <option [value]="l.code">{{ l.code }} · {{ l.shortName }}</option>
            }
          </select>
        </label>
        <button mat-flat-button type="submit">Buscar</button>
      </form>

      <div class="filters" role="group" aria-label="Território de atendimento">
        <button
          type="button"
          class="chip"
          [attr.aria-pressed]="!territory()"
          (click)="pickTerritory(null)"
        >
          Todos os territórios
        </button>
        @for (t of territories; track t.value) {
          <button
            type="button"
            class="chip"
            [attr.aria-pressed]="territory() === t.value"
            (click)="pickTerritory(t.value)"
          >
            {{ t.label }}
          </button>
        }
      </div>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (loading()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        <p class="count muted" aria-live="polite">
          {{ total() }} {{ total() === 1 ? 'credenciado encontrado' : 'credenciados encontrados' }}
        </p>
        <ul class="grid">
          @for (s of items(); track s.id) {
            <li class="card supplier">
              <div class="top">
                <span class="avatar" aria-hidden="true">{{ initials(s.name) }}</span>
                <div class="title">
                  <h2>{{ s.name }}</h2>
                  @if (s.legalName && s.legalName !== s.name) {
                    <span class="muted small">{{ s.legalName }}</span>
                  }
                </div>
              </div>
              <ul class="meta">
                @if (s.supplierTypeLabel) {
                  <li>
                    <mat-icon aria-hidden="true">storefront</mat-icon>{{ s.supplierTypeLabel }}
                  </li>
                }
                @if (s.documentMasked) {
                  <li><mat-icon aria-hidden="true">badge</mat-icon>{{ s.documentMasked }}</li>
                }
                @if (s.municipalityName) {
                  <li><mat-icon aria-hidden="true">place</mat-icon>{{ s.municipalityName }}/RJ</li>
                }
                <li>
                  <mat-icon aria-hidden="true">map</mat-icon>
                  Atende: {{ territoryCodes(s.territories) }}
                </li>
                @if (s.phone) {
                  <li>
                    <mat-icon aria-hidden="true">call</mat-icon>
                    <a [href]="'tel:+55' + s.phone">{{ phone(s.phone) }}</a>
                  </li>
                }
                @if (s.contactEmail) {
                  <li>
                    <mat-icon aria-hidden="true">mail</mat-icon>
                    <a [href]="'mailto:' + s.contactEmail">{{ s.contactEmail }}</a>
                  </li>
                }
              </ul>
              <ul class="lines" aria-label="Linhas credenciadas">
                @for (l of s.lines; track l.code) {
                  <li [title]="l.shortName">
                    <strong>{{ l.code }}</strong> {{ l.shortName }}
                  </li>
                }
              </ul>
              <div class="foot">
                <span class="badge badge-success">
                  <mat-icon aria-hidden="true">verified</mat-icon> Válido até
                  {{ date(s.validUntil) }}
                </span>
                @if (s.womanLed) {
                  <span class="badge badge-info">Liderado por mulher</span>
                }
                @if (auth.hasRole('COMMISSION', 'ADMIN')) {
                  <a class="open" [routerLink]="['/comissao/requerimentos', s.id]"
                    >Ver requerimento</a
                  >
                }
              </div>
            </li>
          } @empty {
            <li class="empty">
              <mat-icon aria-hidden="true">storefront</mat-icon>
              <p>
                @if (query() || line() || territory()) {
                  Nenhum credenciado com esses filtros.
                } @else {
                  Ainda não há fornecedores credenciados.
                }
              </p>
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

      <p class="note muted">
        Contém dados pessoais de contato: uso restrito à equipe do Instituto, para o credenciamento
        e as contratações (Política, art. 28; LGPD). Não compartilhe fora do sistema.
      </p>
    </div>
  `,
  styles: `
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      margin-bottom: 0.75rem;
    }
    .search,
    .select {
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
    .search {
      flex: 2 1 280px;
    }
    .select {
      flex: 1 1 240px;
    }
    .search:focus-within,
    .select:focus-within {
      border-color: var(--sh-primary);
      box-shadow: 0 0 0 3px var(--sh-primary-soft);
    }
    .search input,
    .select select {
      flex: 1;
      border: 0;
      outline: none !important;
      box-shadow: none !important;
      font: inherit;
      color: var(--sh-text);
      background: transparent;
      min-width: 0;
    }
    .select select {
      cursor: pointer;
    }
    .toolbar button {
      height: 56px;
    }
    .filters {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
      margin-bottom: 1rem;
    }
    .chip {
      height: 44px;
      padding: 0 1rem;
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
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
    .count {
      margin: 0 0 1rem;
    }
    .grid {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    }
    .supplier {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }
    .top {
      display: flex;
      gap: 0.85rem;
      align-items: center;
    }
    .avatar {
      display: grid;
      place-items: center;
      flex: 0 0 auto;
      width: 48px;
      height: 48px;
      border-radius: 14px;
      background: var(--sh-gradient-deep);
      color: #fff;
      font-family: var(--sh-font-heading);
      font-weight: 800;
    }
    .title {
      min-width: 0;
    }
    .title h2 {
      font-size: 1.12rem;
      margin: 0;
      overflow-wrap: anywhere;
    }
    .small {
      font-size: 0.88rem;
    }
    .meta {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.3rem;
      color: var(--sh-text-muted);
      font-size: 0.92rem;
    }
    .meta li {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      min-width: 0;
    }
    .meta a {
      overflow-wrap: anywhere;
    }
    .meta mat-icon {
      width: 18px;
      height: 18px;
      font-size: 18px;
      flex: 0 0 auto;
    }
    .lines {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .lines li {
      padding: 0.2rem 0.6rem;
      border-radius: 8px;
      background: var(--sh-primary-soft);
      color: var(--sh-navy);
      font-size: 0.85rem;
    }
    .foot {
      margin-top: auto;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
    }
    .foot mat-icon {
      width: 16px;
      height: 16px;
      font-size: 16px;
    }
    .open {
      margin-left: auto;
      font-weight: 700;
      font-size: 0.9rem;
    }
    .empty {
      grid-column: 1 / -1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.75rem;
      padding: 3rem 1rem;
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
    .pager {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      margin-top: 1.25rem;
    }
    .note {
      margin-top: 2rem;
      font-size: 0.9rem;
    }
  `,
})
export class AccreditedSuppliersPage implements OnInit {
  private readonly api = inject(AccreditedSupplierApi);
  private readonly catalog = inject(CatalogApi);
  protected readonly auth = inject(AuthService);

  protected readonly territories = TERRITORIES;
  protected readonly date = formatDate;
  protected readonly phone = formatPhone;

  protected readonly lines = signal<SupplyLine[]>([]);
  protected readonly items = signal<AccreditedSupplier[]>([]);
  protected readonly query = signal('');
  protected readonly line = signal('');
  protected readonly territory = signal<Territory | null>(null);
  protected readonly page = signal(0);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.catalog.supplyLines().subscribe((lines) => this.lines.set(lines.filter((l) => l.active)));
    this.load(0);
  }

  protected pickTerritory(t: Territory | null): void {
    this.territory.set(t);
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.error.set('');
    this.api.search(this.line(), this.territory(), this.query(), page).subscribe({
      next: (p) => {
        this.items.set(p.content);
        this.page.set(p.page);
        this.total.set(p.totalElements);
        this.totalPages.set(p.totalPages);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(problemMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected initials(name: string): string {
    const letters = name
      .split(/\s+/)
      .filter((w) => w.length > 2)
      .slice(0, 2)
      .map((w) => w[0].toUpperCase())
      .join('');
    return letters || name.trim().charAt(0).toUpperCase();
  }

  protected territoryCodes(ts: Territory[]): string {
    return TERRITORIES.filter((t) => ts.includes(t.value))
      .map((t) => t.label.split(' — ')[1] ?? t.value)
      .join(', ');
  }
}
