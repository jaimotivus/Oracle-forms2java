import { Component, OnInit } from '@angular/core';
import { ConnectionService } from '../../services/connection.service';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * ToolbarComponent
 * 
 * This component replaces the Oracle Forms toolbar that displayed:
 * - Database connection information
 * - Current system date
 * - Current user
 * 
 * Original Oracle Forms procedure: P_InformaDatosToolbar
 */
@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.scss']
})
export class ToolbarComponent implements OnInit {
  // Properties that replace the Oracle Forms global variables
  connectionInfo: string = '';
  currentDate: Date | null = null;
  currentUser: string = '';
  isLoading: boolean = true;

  constructor(
    private connectionService: ConnectionService,
    private snackBar: MatSnackBar
  ) { }

  /**
   * ngOnInit lifecycle hook
   * Equivalent to the WHEN-NEW-FORM-INSTANCE trigger in Oracle Forms
   * Calls the method to load toolbar data when component initializes
   */
  ngOnInit(): void {
    this.loadToolbarData();
  }

  /**
   * Loads all toolbar data
   * This replaces the P_InformaDatosToolbar procedure from Oracle Forms
   */
  loadToolbarData(): void {
    this.isLoading = true;
    
    // Get connection information (replaces the first SQL query)
    this.connectionService.getConnectionInfo()
      .pipe(
        catchError(error => {
          this.connectionInfo = 'CONEXIÓN NO IDENTIFICADA';
          this.showErrorMessage('No se ha podido recuperar la información de conexión: ' + error.message);
          return of(null);
        })
      )
      .subscribe(connectionInfo => {
        if (connectionInfo) {
          this.connectionInfo = connectionInfo.cageNomConcep;
        }
        
        // Get current system date (replaces the second SQL query)
        this.getCurrentDate();
      });
  }

  /**
   * Gets the current system date
   * Replaces the second SQL query in the original procedure
   */
  private getCurrentDate(): void {
    this.connectionService.getCurrentDate()
      .pipe(
        catchError(error => {
          this.showErrorMessage('No se ha podido recuperar la fecha actual de base de datos: ' + error.message);
          return of(null);
        })
      )
      .subscribe(date => {
        if (date) {
          this.currentDate = date;
        }
        
        // Get current user (replaces the third SQL query)
        this.getCurrentUser();
      });
  }

  /**
   * Gets the current user
   * Replaces the third SQL query in the original procedure
   */
  private getCurrentUser(): void {
    this.connectionService.getCurrentUser()
      .pipe(
        catchError(error => {
          this.showErrorMessage('No se ha podido recuperar el usuario: ' + error.message);
          return of(null);
        })
      )
      .subscribe(user => {
        if (user) {
          this.currentUser = user;
        }
        this.isLoading = false;
      });
  }

  /**
   * Displays error messages
   * Replaces the PKG_MSGS.Mensaje_Error calls in Oracle Forms
   */
  private showErrorMessage(message: string): void {
    this.snackBar.open(message, 'Cerrar', {
      duration: 5000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
      panelClass: ['error-snackbar']
    });
  }

  /**
   * Format date for display
   * Utility method to format the date consistently
   */
  formatDate(date: Date | null): string {
    if (!date) return '';
    return new Intl.DateTimeFormat('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    }).format(date);
  }
}