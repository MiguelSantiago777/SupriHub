import { Routes } from '@angular/router';

/** Área do fornecedor: credenciamento, editais em aberto e contratos. */
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
  {
    path: 'contratos',
    title: 'Meus contratos',
    loadComponent: () =>
      import('../contracts/supplier-contracts.page').then((m) => m.SupplierContractsPage),
  },
  {
    path: 'contratos/:id',
    title: 'Ordem de fornecimento',
    loadComponent: () =>
      import('../contracts/supplier-contract.page').then((m) => m.SupplierContractPage),
  },
  {
    path: 'sancoes',
    title: 'Processos',
    loadComponent: () =>
      import('../sanctions/supplier-sanctions.page').then((m) => m.SupplierSanctionsPage),
  },
  {
    path: 'sancoes/:id',
    title: 'Processo',
    loadComponent: () =>
      import('../sanctions/supplier-sanction.page').then((m) => m.SupplierSanctionPage),
  },
];
