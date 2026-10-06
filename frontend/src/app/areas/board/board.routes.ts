import { Routes } from '@angular/router';

/** Diretoria: atas da Faixa 3 para aprovar (os recursos ficam em /recursos). */
export const BOARD_ROUTES: Routes = [
  {
    path: '',
    title: 'Diretoria',
    loadComponent: () => import('./board-home.page').then((m) => m.BoardHomePage),
  },
  {
    path: 'atas/:id',
    title: 'Ata de julgamento',
    loadComponent: () => import('./board-judgment.page').then((m) => m.BoardJudgmentPage),
  },
];
