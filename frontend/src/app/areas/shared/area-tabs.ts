import { Component, computed, inject, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppRole } from '../../core/models';

interface Tab {
  label: string;
  icon: string;
  link: string;
  exact?: boolean;
  roles: AppRole[];
}

const SUPPLIER_TABS: Tab[] = [
  {
    label: 'Meu credenciamento',
    icon: 'assignment_ind',
    link: '/fornecedor',
    exact: true,
    roles: ['SUPPLIER'],
  },
  {
    label: 'Editais em aberto',
    icon: 'campaign',
    link: '/fornecedor/editais',
    roles: ['SUPPLIER'],
  },
  {
    label: 'Meus contratos',
    icon: 'receipt_long',
    link: '/fornecedor/contratos',
    roles: ['SUPPLIER'],
  },
];

const STAFF_TABS: Tab[] = [
  {
    label: 'Requerimentos',
    icon: 'how_to_reg',
    link: '/comissao/requerimentos',
    roles: ['COMMISSION', 'ADMIN'],
  },
  { label: 'Editais', icon: 'campaign', link: '/comissao/editais', roles: ['COMMISSION', 'ADMIN'] },
  {
    label: 'Contratos',
    icon: 'receipt_long',
    link: '/contratos',
    roles: ['COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN'],
  },
  {
    label: 'Preços',
    icon: 'price_check',
    link: '/precos',
    roles: ['COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN'],
  },
  {
    label: 'Sanções',
    icon: 'report',
    link: '/sancoes',
    roles: ['COMMISSION', 'BOARD', 'INTERNAL_CONTROL', 'ADMIN'],
  },
  {
    label: 'Dossiês',
    icon: 'inventory_2',
    link: '/controle',
    roles: ['INTERNAL_CONTROL', 'ADMIN'],
  },
  {
    label: 'Indicadores',
    icon: 'insights',
    link: '/indicadores',
    roles: ['COMMISSION', 'BOARD', 'INTERNAL_CONTROL', 'FINANCE', 'ADMIN'],
  },
  { label: 'Atas', icon: 'verified', link: '/diretoria', exact: true, roles: ['BOARD'] },
  { label: 'Recursos', icon: 'gavel', link: '/recursos', roles: ['COMMISSION', 'BOARD', 'ADMIN'] },
  {
    label: 'Credenciados',
    icon: 'storefront',
    link: '/credenciados',
    roles: ['COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN'],
  },
];

/** Abas de navegação de uma área (fornecedor ou equipe interna). */
@Component({
  selector: 'app-area-tabs',
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  template: `
    <nav
      class="area-tabs"
      [attr.aria-label]="area() === 'supplier' ? 'Área do fornecedor' : 'Área interna'"
    >
      <div class="container inner">
        @for (t of tabs(); track t.link) {
          <a
            [routerLink]="t.link"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{ exact: !!t.exact }"
            ariaCurrentWhenActive="page"
          >
            <mat-icon aria-hidden="true">{{ t.icon }}</mat-icon>
            {{ t.label }}
            @if (badge() && t.link === badgeLink()) {
              <span class="dot">{{ badge() }}</span>
            }
          </a>
        }
      </div>
    </nav>
  `,
  styles: `
    .area-tabs {
      background: var(--sh-surface);
      border-bottom: 1px solid var(--sh-border);
    }
    .inner {
      display: flex;
      gap: 0.25rem;
      overflow-x: auto;
    }
    a {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      min-height: 52px;
      padding: 0 1rem;
      border-bottom: 3px solid transparent;
      color: var(--sh-text-muted);
      font-weight: 700;
      text-decoration: none;
      white-space: nowrap;
    }
    a:hover {
      color: var(--sh-primary);
    }
    a.active {
      color: var(--sh-navy);
      border-bottom-color: var(--sh-primary);
    }
    mat-icon {
      width: 20px;
      height: 20px;
      font-size: 20px;
    }
    .dot {
      min-width: 1.4rem;
      padding: 0 0.4rem;
      border-radius: 999px;
      background: var(--sh-amber);
      color: #fff;
      font-size: 0.78rem;
      line-height: 1.4rem;
      text-align: center;
    }
  `,
})
export class AreaTabs {
  private readonly auth = inject(AuthService);
  readonly area = input<'supplier' | 'staff'>('staff');
  /** Contador opcional numa aba (ex.: editais aguardando resposta). */
  readonly badge = input<number>(0);
  readonly badgeLink = input<string>('');

  protected readonly tabs = computed(() =>
    (this.area() === 'supplier' ? SUPPLIER_TABS : STAFF_TABS).filter((t) =>
      this.auth.hasRole(...t.roles),
    ),
  );
}
