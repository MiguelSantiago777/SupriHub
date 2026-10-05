import { Routes } from '@angular/router';
import { guestGuard, roleGuard } from './core/auth/guards';

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
    path: 'recursos',
    canMatch: [roleGuard('COMMISSION', 'BOARD', 'ADMIN')],
    loadChildren: () => import('./areas/appeals/appeals.routes').then((m) => m.APPEAL_ROUTES),
  },
  {
    path: 'gestor',
    title: 'Gestor da demanda',
    canMatch: [roleGuard('DEMAND_MANAGER')],
    loadComponent: () => import('./areas/area-home.page').then((m) => m.AreaHomePage),
    data: { area: 'DEMAND_MANAGER' },
  },
  {
    path: 'financeiro',
    title: 'Financeiro',
    canMatch: [roleGuard('FINANCE')],
    loadComponent: () => import('./areas/area-home.page').then((m) => m.AreaHomePage),
    data: { area: 'FINANCE' },
  },
  {
    path: 'controle',
    title: 'Controle Interno',
    canMatch: [roleGuard('INTERNAL_CONTROL')],
    loadComponent: () => import('./areas/area-home.page').then((m) => m.AreaHomePage),
    data: { area: 'INTERNAL_CONTROL' },
  },
  {
    path: 'diretoria',
    title: 'Diretoria',
    canMatch: [roleGuard('BOARD')],
    loadComponent: () => import('./areas/area-home.page').then((m) => m.AreaHomePage),
    data: { area: 'BOARD' },
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
