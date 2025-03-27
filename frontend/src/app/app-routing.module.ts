import { NgModule } from '@angular/core';
import { RouterModule, Routes, PreloadAllModules } from '@angular/router';
import { environment } from '../environments/environment';

// Import components that will be used in routes
import { HomeComponent } from './components/home/home.component';
import { AuthGuard } from './guards/auth.guard';
import { ReserveAdjustmentComponent } from './components/reserve-adjustment/reserve-adjustment.component';
import { PageNotFoundComponent } from './components/page-not-found/page-not-found.component';
import { LoginComponent } from './components/login/login.component';

/**
 * Application routes configuration
 *
 * This routing configuration maps URLs to components, implementing navigation.
 */
const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
    canActivate: [AuthGuard],
    data: { title: 'Home' }
  },
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'reserve-adjustment',
    component: ReserveAdjustmentComponent,
    canActivate: [AuthGuard],
    data: { title: 'Reserve Adjustment' }
  },
  {
    path: 'reserve-adjustment/:claimId',
    component: ReserveAdjustmentComponent,
    canActivate: [AuthGuard],
    data: { title: 'Reserve Adjustment' }
  },
  // Lazy-loaded modules for other parts of the application
  {
    path: 'claims',
    loadChildren: () => import('./modules/claims/claims.module').then(m => m.ClaimsModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'policies',
    loadChildren: () => import('./modules/policies/policies.module').then(m => m.PoliciesModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'reports',
    loadChildren: () => import('./modules/reports/reports.module').then(m => m.ReportsModule),
    canActivate: [AuthGuard]
  },
  // Wildcard route for 404 page
  {
    path: '**',
    component: PageNotFoundComponent,
    data: { title: 'Page Not Found' }
  }
];

/**
 * Routing module configuration
 *
 * This module configures the Angular Router to handle navigation in the application.
 */
@NgModule({
  imports: [
    RouterModule.forRoot(routes, {
      enableTracing: !environment.production, // Enable route tracing for debugging in development
      useHash: false, // Use HTML5 History API for cleaner URLs
      scrollPositionRestoration: 'enabled', // Restore scroll position when navigating back
      preloadingStrategy: PreloadAllModules, // Preload all lazy-loaded modules in the background
      onSameUrlNavigation: 'reload', // Don't reuse routes
      relativeLinkResolution: 'corrected' // Use relative link resolution.  'corrected' is the modern default.
    })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule {
  constructor() {
    if (!environment.production) {
      console.log('Routing module initialized');
    }
  }
}