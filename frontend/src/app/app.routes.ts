import { Routes } from '@angular/router';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'login' },
	{ path: 'login', loadChildren: () => import('./features/auth/auth.routes') },
	{
		path: '',
		loadComponent: () => import('./core/layout/app-shell/app-shell.component').then((module) => module.AppShellComponent),
		// Add canActivate: [authGuard] here once the backend issues tokens.
		children: [{ path: 'home', loadChildren: () => import('./features/map/map.routes') }],
	},
	{ path: '**', redirectTo: 'home' },
];
