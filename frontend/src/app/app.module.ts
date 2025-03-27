import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { RouterModule } from '@angular/router';

// Angular Material Imports - Moved to MaterialModule
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatBadgeModule } from '@angular/material/badge';

// Core Components
import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { AuthInterceptor } from './core/interceptors/auth.interceptor';
import { ErrorInterceptor } from './core/interceptors/error.interceptor';
import { NavbarComponent } from './core/components/navbar/navbar.component';
import { SidebarComponent } from './core/components/sidebar/sidebar.component';
import { PageNotFoundComponent } from './core/components/page-not-found/page-not-found.component';
import { LoginComponent } from './core/components/login/login.component';
import { HomeComponent } from './core/components/home/home.component';

// Shared Components
import { AlertDialogComponent } from './shared/components/alert-dialog/alert-dialog.component';
import { ConfirmDialogComponent } from './shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from './shared/components/loading-spinner/loading-spinner.component';
import { DataTableComponent } from './shared/components/data-table/data-table.component';
import { FormFieldComponent } from './shared/components/form-field/form-field.component';
import { ErrorMessageComponent } from './shared/components/error-message/error-message.component';

// Feature Components - Converted from Oracle Forms
import { ReserveAdjustmentComponent } from './features/reserve-adjustment/reserve-adjustment.component';
import { SiniestroFormComponent } from './features/reserve-adjustment/components/siniestro-form/siniestro-form.component';
import { PolizaFormComponent } from './features/reserve-adjustment/components/poliza-form/poliza-form.component';
import { CoberturaTableComponent } from './features/reserve-adjustment/components/cobertura-table/cobertura-table.component';
import { ReservaTableComponent } from './features/reserve-adjustment/components/reserva-table/reserva-table.component';
import { ToolbarComponent } from './features/reserve-adjustment/components/toolbar/toolbar.component';

// Services
import { AuthService } from './core/services/auth.service';
import { AlertService } from './shared/services/alert.service';
import { SiniestroService } from './features/reserve-adjustment/services/siniestro.service';
import { PolizaService } from './features/reserve-adjustment/services/poliza.service';
import { CoberturaService } from './features/reserve-adjustment/services/cobertura.service';
import { ReservaService } from './features/reserve-adjustment/services/reserva.service';
import { AjusteReservaService } from './features/reserve-adjustment/services/ajuste-reserva.service';
import { UtileriasService } from './shared/services/utilerias.service';
import { DmlService } from './shared/services/dml.service';
import { MensajesService } from './shared/services/mensajes.service';

// Feature Modules (Example - if Reserve Adjustment becomes large)
// import { ReserveAdjustmentModule } from './features/reserve-adjustment/reserve-adjustment.module';

// Custom Modules
import { MaterialModule } from './material.module'; // NEW: Encapsulates Angular Material imports
import { CoreModule } from './core/core.module'; // NEW: Encapsulates core components and services
import { SharedModule } from './shared/shared.module'; // NEW: Encapsulates shared components and services

/**
 * Main application module
 *
 * This module is the entry point for the application and includes all the necessary
 * components, services, and modules required for the application to function.
 *
 * The original Oracle Forms application (SINF50104) has been converted to a modern
 * Angular application with a component-based architecture, reactive forms, and
 * proper separation of concerns.
 *
 * @remarks
 * This module is responsible for bootstrapping the application and configuring
 * global providers and interceptors.  As the application grows, consider
 * refactoring into feature modules for lazy loading and improved maintainability.
 *
 * @see AppRoutingModule - For application routing configuration.
 * @see AuthInterceptor - For authentication handling.
 * @see ErrorInterceptor - For global error handling.
 */
@NgModule({
  declarations: [
    // Core Components
    AppComponent,
    // NavbarComponent, // Moved to CoreModule
    // SidebarComponent, // Moved to CoreModule
    PageNotFoundComponent,
    LoginComponent,
    HomeComponent,

    // Shared Components
    // AlertDialogComponent, // Moved to SharedModule
    // ConfirmDialogComponent, // Moved to SharedModule
    // LoadingSpinnerComponent, // Moved to SharedModule
    // DataTableComponent, // Moved to SharedModule
    // FormFieldComponent, // Moved to SharedModule
    // ErrorMessageComponent, // Moved to SharedModule

    // Feature Components - Converted from Oracle Forms
    ReserveAdjustmentComponent,
    SiniestroFormComponent,
    PolizaFormComponent,
    CoberturaTableComponent,
    ReservaTableComponent,
    ToolbarComponent
  ],
  imports: [
    // Angular Core Modules
    BrowserModule,
    BrowserAnimationsModule,
    ReactiveFormsModule,
    FormsModule,
    HttpClientModule,
    // RouterModule, // Potentially redundant, check AppRoutingModule
    AppRoutingModule,

    // Angular Material Modules - Imported via MaterialModule
    // MatTableModule,
    // MatPaginatorModule,
    // MatSortModule,
    // MatInputModule,
    // MatSelectModule,
    // MatButtonModule,
    // MatIconModule,
    // MatCardModule,
    // MatToolbarModule,
    // MatSidenavModule,
    // MatListModule,
    // MatDialogModule,
    // MatSnackBarModule,
    // MatDatepickerModule,
    // MatNativeDateModule,
    // MatProgressSpinnerModule,
    // MatTabsModule,
    // MatGridListModule,
    // MatTooltipModule,
    // MatCheckboxModule,
    // MatExpansionModule,
    // MatDividerModule,
    // MatBadgeModule

    // Custom Modules
    MaterialModule, // Import the MaterialModule
    CoreModule, // Import the CoreModule
    SharedModule, // Import the SharedModule

    // Feature Module (Example)
    // ReserveAdjustmentModule, // Import if ReserveAdjustmentModule is created
  ],
  providers: [
    // Core Services
    // AuthService, // Moved to CoreModule
    // AlertService, // Moved to SharedModule

    // Feature Services
    SiniestroService,
    PolizaService,
    CoberturaService,
    ReservaService,
    AjusteReservaService,
    UtileriasService,
    DmlService,
    MensajesService,

    // HTTP Interceptors
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {
  /**
   * This module represents the conversion of the Oracle Forms application SINF50104.
   *
   * The original application was a form for adjusting insurance reserves with the following
   * main components:
   *
   * 1. Siniestro (Claim) information display
   * 2. Poliza (Policy) information display
   * 3. Cobertura (Coverage) table with adjustment capabilities
   * 4. Reserva (Reserve) table showing reserve details
   * 5. Toolbar with action buttons
   *
   * The conversion approach:
   *
   * 1. Each Oracle Forms block has been converted to an Angular component:
   *    - DB_SINIESTRO block -> SiniestroFormComponent
   *    - DB_POLIZA block -> PolizaFormComponent
   *    - DT_COBERTURA block -> CoberturaTableComponent
   *    - DT_SINT_RESERVA_COBERTURA_CERT block -> ReservaTableComponent
   *    - TOOLBAR block -> ToolbarComponent
   *
   * 2. Oracle Forms triggers have been converted to Angular event handlers and services:
   *    - WHEN-BUTTON-PRESSED triggers -> click event handlers
   *    - WHEN-VALIDATE-ITEM triggers -> form validators and value change handlers
   *    - KEY-COMMIT trigger -> save methods in services
   *
   * 3. Oracle Forms packages have been converted to Angular services:
   *    - PKG_AJUSTE_RESERVA -> AjusteReservaService
   *    - PKG_UTILERIAS -> UtileriasService
   *    - PKG_DML -> DmlService
   *    - PKG_MSGS -> MensajesService
   *
   * 4. Oracle Forms alerts have been converted to Angular Material dialogs:
   *    - RESERVAS, CEROS, INFO_A, ASK_INFO, ERROR_A alerts -> AlertDialogComponent
   *
   * The application maintains the same business logic and workflow as the original
   * Oracle Forms application but with a modern, responsive UI and improved user experience.
   *
   * @remarks
   * This AppModule has been refactored to improve maintainability and scalability.
   * Angular Material modules are now encapsulated in {@link MaterialModule}.
   * Core components and services are encapsulated in {@link CoreModule}.
   * Shared components and services are encapsulated in {@link SharedModule}.
   * Consider creating feature modules for lazy loading as the application grows.
   */
  constructor() {
    // Optional: Perform any global initialization here.  Avoid heavy operations.
    console.log('AppModule initialized.');
  }
}