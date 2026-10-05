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
    path: 'requerimentos/:id',
    title: 'Análise do requerimento',
    loadComponent: () => import('./application-review.page').then((m) => m.ApplicationReviewPage),
  },
];
