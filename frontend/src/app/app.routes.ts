import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'literature', pathMatch: 'full' },
  {
    path: 'literature',
    loadComponent: () => import('./features/literature/literature.component').then(m => m.LiteratureComponent)
  },
  {
    path: 'academic',
    loadComponent: () => import('./features/academic/academic.component').then(m => m.AcademicComponent)
  },
  {
    path: 'quotes',
    loadComponent: () => import('./features/quotes/quotes.component').then(m => m.QuotesComponent)
  },
  { path: '**', redirectTo: 'literature' }
];
