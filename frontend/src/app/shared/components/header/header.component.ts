import { Component, OnInit, Input, OnDestroy } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { Subject, takeUntil } from 'rxjs';
import { NotificationService } from '../../../core/services/notification.service'; // Import NotificationService

/**
 * HeaderComponent
 *
 * This component represents the application header converted from Oracle Forms TOOLBAR canvas.
 * It displays the application title, user information, connection details, and date.
 *
 * Original Oracle Forms elements:
 * - TEXT4: Application title
 * - GRAPHIC487: Form code (SINF50104)
 * - GRAPHIC611: Version/date code
 * - CGC$USER: Username display
 * - CGC$SYSDATE: Current date display
 * - DI_CONEXION: Connection information
 */
@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit, OnDestroy {
  @Input() title = 'SISTEMA DE ACTUALIZACION RESERVAS';
  @Input() formCode = 'SINF50104';
  @Input() versionCode = '10022021';

  currentUser: string = '';
  currentDate: Date = new Date();
  connectionInfo: string = '';

  private destroy$ = new Subject<void>(); // Subject to manage subscriptions

  constructor(
    private authService: AuthService,
    private notificationService: NotificationService // Inject NotificationService
  ) { }

  /**
   * Initialize component and load user information
   * Equivalent to the P_InformaDatosToolbar procedure in Oracle Forms
   */
  ngOnInit(): void {
    this.loadUserInformation();
    this.loadConnectionInfo();
  }

  /**
   * Clean up subscriptions to prevent memory leaks
   */
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load current user information from the authentication service
   * In Oracle Forms, this was retrieved using:
   * SELECT User INTO :CG$CTRL.CGC$USER FROM Sys.Dual;
   */
  private loadUserInformation(): void {
    this.authService.getCurrentUser()
      .pipe(takeUntil(this.destroy$)) // Use takeUntil to automatically unsubscribe
      .subscribe(
        (user) => {
          this.currentUser = user.username;
        },
        (error) => {
          console.error('Error retrieving user information:', error);
          this.currentUser = 'USUARIO NO IDENTIFICADO';
          this.notificationService.showError('Error retrieving user information. Using default.', 'User Info Error'); // Show error notification
        }
      );
  }

  /**
   * Load database connection information
   * In Oracle Forms, this was retrieved using:
   * SELECT A.Cage_Nom_Concep INTO :DI_CONEXION FROM Sai_Cat_General A WHERE A.Cage_Cd_Catalogo = 0;
   */
  private loadConnectionInfo(): void {
    this.authService.getConnectionInfo()
      .pipe(takeUntil(this.destroy$)) // Use takeUntil to automatically unsubscribe
      .subscribe(
        (info) => {
          this.connectionInfo = info.connectionName;
        },
        (error) => {
          console.error('Error retrieving connection information:', error);
          this.connectionInfo = 'CONEXIÓN NO IDENTIFICADA';
          this.notificationService.showError('Error retrieving connection information. Using default.', 'Connection Info Error'); // Show error notification
        }
      );
  }
}