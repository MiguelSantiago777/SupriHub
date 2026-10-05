import { Routes } from '@angular/router';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    title: 'Administração',
    loadComponent: () => import('./admin-home.page').then((m) => m.AdminHomePage),
  },
  {
    path: 'parametros',
    title: 'Parâmetros',
    loadComponent: () => import('./parameters.page').then((m) => m.ParametersPage),
  },
  {
    path: 'usuarios',
    title: 'Usuários',
    loadComponent: () => import('./users.page').then((m) => m.UsersPage),
  },
];
