import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/guards';

/** URLs em português; cada área é carregada sob demanda (lazy loading). */
export const routes: Routes = [
  {
    path: '',
    title: 'Início',
    loadComponent: () => import('./public/home.page').then((m) => m.HomePage),
  },
  {
    path: 'catalogo',
    title: 'Catálogo de linhas de fornecimento',
    loadComponent: () => import('./public/catalog.page').then((m) => m.CatalogPage),
  },
  {
    path: 'credenciados',
    title: 'Credenciados',
    canMatch: [
      roleGuard('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN'),
    ],
    loadComponent: () =>
      import('./areas/accredited/accredited-suppliers.page').then((m) => m.AccreditedSuppliersPage),
  },
  {
    path: 'edital',
    title: 'Edital e anexos',
    loadComponent: () => import('./public/documents.page').then((m) => m.DocumentsPage),
  },
  {
    path: '',
    canActivateChild: [guestGuard],
    loadChildren: () => import('./auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'confirmar-email',
    title: 'Confirmar e-mail',
    loadComponent: () => import('./auth/confirm-email.page').then((m) => m.ConfirmEmailPage),
  },
  {
    path: 'nova-senha',
    title: 'Criar nova senha',
    loadComponent: () => import('./auth/reset-password.page').then((m) => m.ResetPasswordPage),
  },
  {
    path: 'confirmar-novo-email',
    title: 'Confirmar novo e-mail',
    loadComponent: () =>
      import('./auth/confirm-email-change.page').then((m) => m.ConfirmEmailChangePage),
  },
  {
    path: 'minha-conta',
    title: 'Minha conta',
    canMatch: [authGuard],
    loadComponent: () => import('./account/account.page').then((m) => m.AccountPage),
  },
  {
    path: 'fornecedor',
    canMatch: [roleGuard('SUPPLIER')],
    loadChildren: () => import('./areas/supplier/supplier.routes').then((m) => m.SUPPLIER_ROUTES),
  },
  {
    path: 'comissao',
    canMatch: [roleGuard('COMMISSION', 'ADMIN')],
    loadChildren: () =>
      import('./areas/commission/commission.routes').then((m) => m.COMMISSION_ROUTES),
  },
  {
    path: 'contratos',
    canMatch: [
      roleGuard('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN'),
    ],
    loadChildren: () => import('./areas/contracts/contracts.routes').then((m) => m.CONTRACT_ROUTES),
  },
  {
    path: 'recursos',
    canMatch: [roleGuard('COMMISSION', 'BOARD', 'ADMIN')],
    loadChildren: () => import('./areas/appeals/appeals.routes').then((m) => m.APPEAL_ROUTES),
  },
  { path: 'gestor', redirectTo: 'contratos', pathMatch: 'full' },
  { path: 'financeiro', redirectTo: 'contratos', pathMatch: 'full' },
  {
    path: 'controle',
    title: 'Revisão de dossiês',
    canMatch: [roleGuard('INTERNAL_CONTROL', 'ADMIN')],
    loadComponent: () => import('./areas/contracts/dossiers.page').then((m) => m.DossiersPage),
  },
  {
    path: 'sancoes',
    canMatch: [roleGuard('COMMISSION', 'BOARD', 'INTERNAL_CONTROL', 'ADMIN')],
    children: [
      {
        path: '',
        title: 'Processos sancionadores',
        loadComponent: () =>
          import('./areas/sanctions/sanctions.page').then((m) => m.SanctionsPage),
      },
      {
        path: 'novo',
        title: 'Novo processo sancionador',
        canMatch: [roleGuard('COMMISSION', 'ADMIN')],
        loadComponent: () =>
          import('./areas/sanctions/sanction-new.page').then((m) => m.SanctionNewPage),
      },
      {
        path: ':id',
        title: 'Processo sancionador',
        loadComponent: () => import('./areas/sanctions/sanction.page').then((m) => m.SanctionPage),
      },
    ],
  },
  {
    path: 'precos',
    title: 'Banco de Preços',
    canMatch: [
      roleGuard('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'INTERNAL_CONTROL', 'BOARD', 'ADMIN'),
    ],
    loadComponent: () => import('./areas/prices/price-bank.page').then((m) => m.PriceBankPage),
  },
  {
    path: 'indicadores',
    title: 'Indicadores',
    canMatch: [roleGuard('COMMISSION', 'BOARD', 'INTERNAL_CONTROL', 'FINANCE', 'ADMIN')],
    loadComponent: () => import('./areas/indicators/indicators.page').then((m) => m.IndicatorsPage),
  },
  {
    path: 'diretoria',
    canMatch: [roleGuard('BOARD')],
    loadChildren: () => import('./areas/board/board.routes').then((m) => m.BOARD_ROUTES),
  },
  {
    path: 'admin',
    canMatch: [roleGuard('ADMIN')],
    loadChildren: () => import('./areas/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: 'acesso-negado',
    title: 'Acesso negado',
    loadComponent: () => import('./public/status.page').then((m) => m.ForbiddenPage),
  },
  {
    path: '**',
    title: 'Página não encontrada',
    loadComponent: () => import('./public/status.page').then((m) => m.NotFoundPage),
  },
];
