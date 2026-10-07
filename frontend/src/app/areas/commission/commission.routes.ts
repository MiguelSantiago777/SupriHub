import { Routes } from '@angular/router';

/** Área da Comissão de Credenciamento (o ADMIN também acessa). */
export const COMMISSION_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'requerimentos',
  },
  {
    path: 'requerimentos',
    title: 'Requerimentos de credenciamento',
    loadComponent: () => import('./applications.page').then((m) => m.ApplicationsPage),
  },
  {
    path: 'manutencao',
    title: 'Manutenção do cadastro',
    loadComponent: () => import('./maintenance.page').then((m) => m.MaintenancePage),
  },
  {
    path: 'editais',
    title: 'Editais',
    loadComponent: () => import('../tenders/tenders.page').then((m) => m.TendersPage),
  },
  {
    path: 'editais/novo',
    title: 'Nova oportunidade',
    loadComponent: () => import('../tenders/tender-form.page').then((m) => m.TenderFormPage),
  },
  {
    path: 'editais/:id',
    title: 'Oportunidade',
    loadComponent: () => import('../tenders/tender.page').then((m) => m.TenderPage),
  },
  {
    path: 'editais/:id/editar',
    title: 'Editar oportunidade',
    loadComponent: () => import('../tenders/tender-form.page').then((m) => m.TenderFormPage),
  },
  {
    path: 'requerimentos/:id',
    title: 'Análise do requerimento',
    loadComponent: () => import('./application-review.page').then((m) => m.ApplicationReviewPage),
  },
];
