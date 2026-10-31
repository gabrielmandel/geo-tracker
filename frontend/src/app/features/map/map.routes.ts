import { Routes } from '@angular/router';

export default [
  {
    path: '',
    loadComponent: () => import('./map-page/map-page.component').then((module) => module.MapPageComponent),
    title: 'Weather stations map | GEO TRACKER',
  },
] satisfies Routes;
