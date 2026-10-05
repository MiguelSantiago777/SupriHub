import { Routes } from '@angular/router';

/** Área do fornecedor: credenciamento e editais em aberto. */
export const SUPPLIER_ROUTES: Routes = [
  {
    path: '',
    title: 'Meu credenciamento',
    loadComponent: () =>
      import('./supplier-application.page').then((m) => m.SupplierApplicationPage),
  },
  {
    path: 'editais',
    title: 'Editais em aberto',
    loadComponent: () =>
      import('../opportunities/opportunities.page').then((m) => m.OpportunitiesPage),
  },
  {
    path: 'editais/:id',
    title: 'Oportunidade',
    loadComponent: () => import('../opportunities/opportunity.page').then((m) => m.OpportunityPage),
  },
];
