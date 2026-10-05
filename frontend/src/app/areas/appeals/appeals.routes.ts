import { Routes } from '@angular/router';

/** Recursos da inabilitação: Comissão, Diretoria e ADMIN. */
export const APPEAL_ROUTES: Routes = [
  {
    path: '',
    title: 'Recursos',
    loadComponent: () => import('./appeals.page').then((m) => m.AppealsPage),
  },
  {
    path: ':id',
    title: 'Recurso',
    loadComponent: () => import('./appeal-detail.page').then((m) => m.AppealDetailPage),
  },
];
