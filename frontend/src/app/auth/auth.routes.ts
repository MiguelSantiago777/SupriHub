import { Routes } from '@angular/router';

export const AUTH_ROUTES: Routes = [
  {
    path: 'entrar',
    title: 'Entrar',
    loadComponent: () => import('./login.page').then((m) => m.LoginPage),
  },
  {
    path: 'criar-conta',
    title: 'Criar conta',
    loadComponent: () => import('./register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'esqueci-senha',
    title: 'Esqueci minha senha',
    loadComponent: () => import('./email-request.page').then((m) => m.EmailRequestPage),
    data: { mode: 'forgot-password' },
  },
  {
    path: 'reenviar-confirmacao',
    title: 'Reenviar confirmação de e-mail',
    loadComponent: () => import('./email-request.page').then((m) => m.EmailRequestPage),
    data: { mode: 'resend-confirmation' },
  },
];
