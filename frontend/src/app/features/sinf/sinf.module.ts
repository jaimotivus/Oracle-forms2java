import { NgModule, ErrorHandler } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatNativeDateModule } from '@angular/material/core';

// Components
import { SinfComponent } from './sinf.component';
import { ReserveAdjustmentComponent } from './components/reserve-adjustment/reserve-adjustment.component';
import { CoverageTableComponent } from './components/coverage-table/coverage-table.component';
import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { AlertDialogComponent } from './components/alert-dialog/alert-dialog.component';
import { ConfirmDialogComponent } from './components/confirm-dialog/confirm-dialog.component';

// Services
import { SinfService } from './services/sinf.service';
import { AlertService } from './services/alert.service';
import { ValidationService } from './services/validation.service';
import { AdjustmentService } from './services/adjustment.service';
import { AuthService } from './services/auth.service';
import { GlobalErrorHandler } from './services/global-error-handler.service'; // Import global error handler

// Guards
import { AuthGuard } from './guards/auth.guard';

// Directives
import { NumericOnlyDirective } from './directives/numeric-only.directive';
import { CurrencyFormatterDirective } from './directives/currency-formatter.directive';

// Pipes
import { StatusPipe } from './pipes/status.pipe';
import { CurrencyFormatPipe } from './pipes/currency-format.pipe';

// Routing Module
import { SinfRoutingModule } from './sinf-routing.module';

/**
 * SINF Module
 *
 * This module handles the conversion of Oracle Forms SINF50104 to Angular.
 * It provides functionality for insurance claim reserve adjustments.
 *
 * Original Oracle Forms:
 * - Form: SINF50104
 * - Title: "Modificación Reserva de Coberturas"
 * - Purpose: Adjust insurance claim reserves by coverage
 */
@NgModule({
  declarations: [
    SinfComponent,
    ReserveAdjustmentComponent,
    CoverageTableComponent,
    ToolbarComponent,
    AlertDialogComponent,
    ConfirmDialogComponent,
    NumericOnlyDirective,
    CurrencyFormatterDirective,
    StatusPipe,
    CurrencyFormatPipe
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    SinfRoutingModule, // Use the separate routing module
    // Angular Material Modules
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatGridListModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatNativeDateModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatSortModule,
    MatTableModule,
    MatTabsModule,
    MatToolbarModule,
    MatTooltipModule
  ],
  providers: [
    SinfService, // Consider providedIn: 'root' if used application-wide
    AlertService, // Consider providedIn: 'root' if used application-wide
    ValidationService, // Consider providedIn: 'root' if used application-wide
    AdjustmentService,
    AuthService, // Consider providedIn: 'root' if used application-wide
    AuthGuard,
    { provide: ErrorHandler, useClass: GlobalErrorHandler } // Register global error handler
  ],
  exports: [
    RouterModule // Export RouterModule for feature routing
  ]
})
export class SinfModule {
  /**
   * Constructor for the SINF module
   *
   * This module encapsulates all components, services, and functionality
   * related to the insurance claim reserve adjustment process.
   *
   * The original Oracle Forms application (SINF50104) has been converted to:
   * - A main component (ReserveAdjustmentComponent)
   * - Supporting components for tables, alerts, and toolbars
   * - Services for data access, validation, and business logic
   * - Angular Material UI components for modern styling
   *
   * Key conversions:
   * - Oracle Forms blocks -> Angular components with reactive forms
   * - Forms triggers -> Angular event handlers and services
   * - Alerts -> Angular Material dialog components
   * - Validation logic -> Angular form validators and custom validation services
   */
  constructor() {
    if (!('production' === process.env.NODE_ENV)) {
      console.log('SINF Module initialized'); // Only log in non-production environments
    }
  }
}