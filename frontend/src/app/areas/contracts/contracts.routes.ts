import { Routes } from '@angular/router';

/** Contratação (equipe interna): ordens de fornecimento, recebimento e pagamento. */
export const CONTRACT_ROUTES: Routes = [
  {
    path: '',
    title: 'Ordens de fornecimento',
    loadComponent: () => import('./contracts.page').then((m) => m.ContractsPage),
  },
  {
    path: ':id',
    title: 'Ordem de fornecimento',
    loadComponent: () => import('./contract.page').then((m) => m.ContractPage),
  },
];
