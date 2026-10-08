import { Routes } from '@angular/router';

// El `title` es una clave de core/textos.ts: TituloTraducido la traduce.
export const routes: Routes = [
  {
    path: '',
    title: 'titulo.equipo',
    loadComponent: () => import('./paginas/equipo/equipo-pagina').then((m) => m.EquipoPagina),
  },
  {
    path: 'jugador/:slug',
    loadComponent: () => import('./paginas/jugador/jugador-pagina').then((m) => m.JugadorPagina),
  },
  {
    path: 'comparar',
    title: 'titulo.comparar',
    loadComponent: () => import('./paginas/comparar/comparar-pagina').then((m) => m.CompararPagina),
  },
  {
    path: 'ranking',
    title: 'titulo.ranking',
    loadComponent: () => import('./paginas/ranking/ranking-pagina').then((m) => m.RankingPagina),
  },
  {
    path: '**',
    title: 'titulo.noEncontrada',
    loadComponent: () => import('./paginas/no-encontrada').then((m) => m.NoEncontradaPagina),
  },
];
