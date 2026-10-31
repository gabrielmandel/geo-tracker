import { Routes } from '@angular/router';

export default [
  {
    path: '',
    loadComponent: () => import('./login-page/login-page.component').then((module) => module.LoginPageComponent),
    title: 'Sign in | GEO TRACKER',
  },
] satisfies Routes;
