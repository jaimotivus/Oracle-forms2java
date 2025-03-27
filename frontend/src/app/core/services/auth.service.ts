import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError, of, Subject } from 'rxjs';
import { catchError, map, tap, finalize, switchMap, takeUntil } from 'rxjs/operators';
import { Router } from '@angular/router';
import { environment } from 'src/environments/environment';
import { UserNotificationService } from './user-notification.service'; // Added notification service
import { ErrorHandlingService } from './error-handling.service'; // Added error handling service

/**
 * User interface representing authenticated user information
 * Derived from Oracle Forms user data structure in CG$CTRL block
 */
export interface User {
  username: string;
  token: string;
  connectionInfo?: string;
  systemDate?: Date;
  permissions?: string[];
}

/**
 * Interface for the authentication response from the backend.
 * Enforces type safety for the response.
 */
interface AuthResponse {
  username: string;
  token: string;
  expiresIn: number;
  connectionInfo?: string;
  systemDate?: string; // Expecting string format from the backend
  permissions?: string[];
  refreshToken?: string; // Optional refresh token
}

/**
 * Authentication service that handles user login, session management, and related functionality
 * Converted from Oracle Forms SINF501041_fmb.xml
 *
 * This service replaces the following Oracle Forms functionality:
 * - User authentication (CGC$USER in CG$CTRL block)
 * - Session tracking (CGC$SYSDATE in CG$CTRL block)
 * - Connection information (DI_CONEXION in TOOLBAR block)
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService implements OnDestroy {
  private apiUrl = environment.apiUrl;
  private currentUserSubject: BehaviorSubject<User | null>;
  public currentUser: Observable<User | null>;
  private tokenExpirationTimer: any;
  private refreshTokenTimeout: any; // Timer for refresh token expiration
  private ngUnsubscribe$ = new Subject<void>(); // Subject to unsubscribe from observables on destroy

  constructor(
    private http: HttpClient,
    private router: Router,
    private notificationService: UserNotificationService, // Inject notification service
    private errorHandlingService: ErrorHandlingService // Inject error handling service
  ) {
    // Initialize the BehaviorSubject with the user from localStorage if available
    // This replaces the Oracle Forms session persistence mechanism
    this.currentUserSubject = new BehaviorSubject<User | null>(this.getUserFromStorage());
    this.currentUser = this.currentUserSubject.asObservable();
  }

  ngOnDestroy(): void {
    // Unsubscribe from all observables
    this.ngUnsubscribe$.next();
    this.ngUnsubscribe$.complete();

    // Clear any active timers
    clearTimeout(this.tokenExpirationTimer);
    clearTimeout(this.refreshTokenTimeout);
  }

  /**
   * Get the current authenticated user
   * @returns The current user or null if not authenticated
   */
  public get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Login user with credentials
   * Replaces the Oracle Forms login mechanism
   *
   * @param username User's username
   * @param password User's password
   * @returns Observable with user information
   */
  login(username: string, password: string): Observable<User> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, { username, password })
      .pipe(
        tap(response => {
          if (!response || !response.token) {
            // Handle the case where the token is missing in the response
            throw new Error('Authentication failed: Token is missing in the response.');
          }
        }),
        map(response => {
          // Store user details and JWT token in local storage to keep user logged in
          const user: User = {
            username: response.username,
            token: response.token,
            connectionInfo: response.connectionInfo,
            systemDate: response.systemDate ? new Date(response.systemDate) : undefined, // Handle potential null systemDate
            permissions: response.permissions
          };

          this.storeUserInStorage(user);
          this.currentUserSubject.next(user);

          // Set auto logout timer based on token expiration
          this.autoLogout(response.expiresIn * 1000);

          // If refresh token is supported, schedule a token refresh
          if (response.refreshToken) {
            this.scheduleRefreshToken(response.expiresIn * 1000);
          }

          return user;
        }),
        catchError(error => {
          // Enhanced error handling using the injected service
          return this.handleError(error);
        })
      );
  }

  /**
   * Logout the current user
   * Clears local storage and navigates to login page
   */
  logout(): void {
    // Clear user from storage
    localStorage.removeItem('currentUser');
    this.currentUserSubject.next(null);

    // Clear the token timer if it exists
    if (this.tokenExpirationTimer) {
      clearTimeout(this.tokenExpirationTimer);
    }
    this.tokenExpirationTimer = null;

    // Clear the refresh token timer if it exists
    if (this.refreshTokenTimeout) {
      clearTimeout(this.refreshTokenTimeout);
    }
    this.refreshTokenTimeout = null;

    // Navigate to login page
    this.router.navigate(['/login']);
  }

  /**
   * Auto logout when token expires
   * @param expirationDuration Time in milliseconds until token expires
   */
  autoLogout(expirationDuration: number): void {
    if (this.tokenExpirationTimer) {
      clearTimeout(this.tokenExpirationTimer);
    }

    this.tokenExpirationTimer = setTimeout(() => {
      this.logout();
      this.notificationService.showNotification('Your session has expired. Please log in again.', 'warn'); // Notify user
    }, expirationDuration);
  }

  /**
   * Check if user is authenticated
   * @returns True if user is authenticated, false otherwise
   */
  isAuthenticated(): boolean {
    return !!this.currentUserValue;
  }

  /**
   * Get connection information
   * Replaces the P_InformaDatosToolbar procedure in Oracle Forms
   * @returns Observable with connection information
   */
  getConnectionInfo(): Observable<string> {
    return this.http.get<{ connectionInfo: string }>(`${this.apiUrl}/auth/connection-info`)
      .pipe(
        map(response => response.connectionInfo),
        catchError(error => this.handleError(error))
      );
  }

  /**
   * Refresh the current user's system date
   * Replaces the CGC$SYSDATE update in Oracle Forms
   */
  refreshSystemDate(): Observable<Date> {
    return this.http.get<{ systemDate: string }>(`${this.apiUrl}/auth/system-date`)
      .pipe(
        map(response => {
          const systemDate = new Date(response.systemDate);

          // Update the current user with the new system date
          if (this.currentUserValue) {
            const updatedUser = {
              ...this.currentUserValue,
              systemDate
            };

            this.storeUserInStorage(updatedUser);
            this.currentUserSubject.next(updatedUser);
          }

          return systemDate;
        }),
        catchError(error => this.handleError(error))
      );
  }

  /**
   * Check if user has a specific permission
   * @param permission Permission to check
   * @returns True if user has permission, false otherwise
   */
  hasPermission(permission: string): boolean {
    return this.currentUserValue?.permissions?.includes(permission) || false;
  }

  /**
   * Refresh the JWT token using the refresh token.
   *
   * @returns An Observable that emits the new User object on success, or an error on failure.
   */
  refreshToken(): Observable<User> {
    const storedUser = this.getUserFromStorage();

    if (!storedUser || !storedUser.token) {
      // If there's no user or refresh token, redirect to login
      this.router.navigate(['/login']);
      return throwError(() => new Error('Not authenticated'));
    }

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${storedUser.token}` // Pass the current token for verification
    });

    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/refresh-token`, {}, { headers })
      .pipe(
        map(response => {
          // Check if the response is valid and contains a new token
          if (!response || !response.token) {
            throw new Error('Failed to refresh token: New token is missing.');
          }

          // Create a new user object with the updated token and expiration
          const updatedUser: User = {
            username: storedUser.username, // Keep the existing username
            token: response.token, // Use the new token
            connectionInfo: response.connectionInfo,
            systemDate: response.systemDate ? new Date(response.systemDate) : undefined,
            permissions: response.permissions
          };

          // Store the updated user in local storage and update the BehaviorSubject
          this.storeUserInStorage(updatedUser);
          this.currentUserSubject.next(updatedUser);

          // Clear the old auto-logout timer and set a new one
          this.autoLogout(response.expiresIn * 1000);

          // Reschedule the refresh token if the backend provides a new refresh token
          if (response.refreshToken) {
            this.scheduleRefreshToken(response.expiresIn * 1000);
          }

          return updatedUser;
        }),
        catchError(error => {
          // Handle errors during token refresh (e.g., invalid refresh token)
          this.logout(); // Logout the user if refresh fails
          return this.handleError(error);
        })
      );
  }

  /**
   * Schedules a token refresh.
   * @param expiresIn The expiration time in milliseconds.
   */
  private scheduleRefreshToken(expiresIn: number): void {
    // Clear any existing refresh token timeout
    clearTimeout(this.refreshTokenTimeout);

    // Set a new timeout to refresh the token before it expires
    this.refreshTokenTimeout = setTimeout(() => {
      this.refreshToken()
        .pipe(takeUntil(this.ngUnsubscribe$)) // Unsubscribe when the component is destroyed
        .subscribe({
          next: () => {
            console.log('Token refreshed successfully.');
          },
          error: (error) => {
            console.error('Failed to refresh token:', error);
            this.logout(); // Logout the user if refresh fails
          }
        });
    }, expiresIn - (60 * 1000)); // Refresh a minute before expiration
  }

  /**
   * Handle HTTP errors
   * Replaces the ON-ERROR trigger in Oracle Forms
   * @param error HTTP error
   * @returns Observable with error message
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    return this.errorHandlingService.handleHttpError(error);
  }

  /**
   * Get user from local storage
   * @returns User object from storage or null if not found
   */
  private getUserFromStorage(): User | null {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        // Convert string date back to Date object
        if (user.systemDate) {
          user.systemDate = new Date(user.systemDate);
        }
        return user;
      } catch (e) {
        console.error('Error parsing stored user', e);
        return null;
      }
    }
    return null;
  }

  /**
   * Store user in local storage
   * @param user User to store
   */
  private storeUserInStorage(user: User): void {
    localStorage.setItem('currentUser', JSON.stringify(user));
  }
}