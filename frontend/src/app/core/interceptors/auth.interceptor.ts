import { Injectable, Inject } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse,
  HTTP_INTERCEPTORS
} from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, of } from 'rxjs';
import { catchError, filter, take, switchMap, finalize, timeout } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';
import { IAuthToken } from '../models/auth-token.model';
import { ILogger, LOGGING_SERVICE } from '../services/logging.service';
import { environment } from '../../../environments/environment';

/**
 * AuthInterceptor
 *
 * This interceptor handles authentication for all HTTP requests:
 * - Adds authentication token to outgoing requests
 * - Handles 401 Unauthorized responses by refreshing tokens
 * - Manages token refresh to prevent multiple simultaneous refresh requests
 * - Redirects to login page when authentication fails completely
 * - Handles generic HTTP errors and provides user-friendly messages
 *
 * Converted from Oracle Forms application where authentication was handled
 * through session-based mechanisms in the TOOLBAR block and various triggers.
 *
 * @remarks
 * This interceptor should be provided in the AppModule or a CoreModule using `multi: true`
 * in the `HTTP_INTERCEPTORS` provider array.
 *
 * @see {@link https://angular.io/api/common/http/HttpInterceptor Angular HttpInterceptor}
 * @see {@link AuthService}
 * @see {@link AppRoutingModule}
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private refreshTokenSubject: BehaviorSubject<IAuthToken | null> = new BehaviorSubject<IAuthToken | null>(null);

  /**
   * Public URLs that do not require authentication.  These should be centralized in a configuration.
   * Consider using regex or startsWith for more robust matching.
   */
  private publicUrls: string[] = environment.publicApiUrls;

  constructor(
    private authService: AuthService,
    private router: Router,
    @Inject(LOGGING_SERVICE) private logger: ILogger
  ) { }

  /**
   * Intercept all HTTP requests to add authentication headers and handle auth errors
   *
   * In the original Oracle Forms app, authentication was maintained through session state
   * and checked in the PRE-FORM and WHEN-NEW-FORM-INSTANCE triggers.
   *
   * @param request - The outgoing HTTP request.
   * @param next - The next interceptor in the chain.
   * @returns An Observable of the HTTP event stream.
   */
  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (this.isPublicRequest(request)) {
      return next.handle(request);
    }

    const authToken = this.authService.getAuthToken();
    if (authToken) {
      request = this.addTokenToRequest(request, authToken.token);
    }

    return next.handle(request).pipe(
      timeout(environment.apiTimeout), // Add a timeout to prevent indefinite waiting
      catchError((error: any) => {
        if (error instanceof HttpErrorResponse) {
          if (error.status === 401) {
            return this.handle401Error(request, next);
          } else {
            return this.handleOtherErrors(error);
          }
        } else {
          // Handle non-HTTP errors, such as network errors or client-side exceptions
          this.logger.error('Non-HTTP error occurred:', error);
          return throwError(() => new Error('A client-side error occurred. Please try again.'));
        }
      }),
      finalize(() => {
        // Place for cleanup operations.  Currently empty, but kept for future use.
        // Example:  this.logger.debug('Request finalized.');
      })
    );
  }

  /**
   * Add authentication token to the request headers
   *
   * In Oracle Forms, this was handled by maintaining session state
   * and passing credentials through the DI_CONEXION display item.
   *
   * @param request - The outgoing HTTP request.
   * @param token - The authentication token.
   * @returns A cloned HTTP request with the Authorization header set.
   */
  private addTokenToRequest(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  /**
   * Handle 401 Unauthorized errors by attempting to refresh the token
   *
   * This replaces the session timeout handling that would have been in
   * the ON-ERROR trigger in the original Forms application.
   *
   * @param request - The outgoing HTTP request that resulted in a 401 error.
   * @param next - The next interceptor in the chain.
   * @returns An Observable of the HTTP event stream, either with the refreshed token or an error.
   */
  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      return this.authService.refreshToken().pipe(
        timeout(environment.refreshTokenTimeout), // Add timeout to refresh token call
        switchMap((token: IAuthToken) => {
          if (token && token.token) {
            this.isRefreshing = false;
            this.refreshTokenSubject.next(token);
            return next.handle(this.addTokenToRequest(request, token.token));
          } else {
            // Token refresh failed, likely due to invalid refresh token
            this.isRefreshing = false;
            this.authService.logout();
            this.router.navigate(['/login']);
            return throwError(() => new Error('Session expired. Please login again.'));
          }
        }),
        catchError((error) => {
          this.isRefreshing = false;
          this.authService.logout();
          this.router.navigate(['/login']);
          this.logger.error('Token refresh failed:', error);
          return throwError(() => new Error('Session expired. Please login again.'));
        })
      );
    } else {
      return this.refreshTokenSubject.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => {
          if (token && token.token) {
            return next.handle(this.addTokenToRequest(request, token.token));
          } else {
            // If token is unexpectedly null, redirect to login
            this.authService.logout();
            this.router.navigate(['/login']);
            return throwError(() => new Error('Session expired. Please login again.'));
          }
        })
      );
    }
  }

  /**
   * Handle other types of HTTP errors
   *
   * This is similar to the error handling in the ON-ERROR trigger
   * in the original Forms application, which had specific handling
   * for different Oracle error codes.
   *
   * @param error - The HttpErrorResponse object.
   * @returns An Observable that emits an error.
   */
  private handleOtherErrors(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An unexpected error occurred.';

    if (!navigator.onLine) {
      errorMessage = 'No internet connection. Please check your network.';
    } else if (error.status === 0) {
      errorMessage = 'Cannot connect to the server. Please try again later.';
    } else if (error.status === 403) {
      errorMessage = 'You do not have permission to access this resource.';
    } else if (error.status === 404) {
      errorMessage = 'The requested resource was not found.';
    } else if (error.status === 500) {
      errorMessage = 'Internal server error. Please try again later.';
    } else if (error.status === 503) {
      errorMessage = 'Service unavailable. Please try again later.';
    } else if (error.error && error.error.message) {
      errorMessage = error.error.message;
    } else if (error.message) {
      errorMessage = error.message;
    }

    const errorDetails = {
      status: error.status,
      url: error.url,
      message: errorMessage
    };

    this.logger.error('HTTP Error:', errorDetails);
    return throwError(() => new Error(errorMessage));
  }

  /**
   * Check if the request is for a public endpoint that doesn't need authentication
   *
   * In the original Forms application, certain forms or operations might not
   * have required authentication or had different authentication requirements.
   *
   * @param request - The outgoing HTTP request.
   * @returns True if the request is for a public endpoint, false otherwise.
   */
  private isPublicRequest(request: HttpRequest<any>): boolean {
    return this.publicUrls.some(url => request.url.includes(url));
  }
}

/**
 * Provider for the AuthInterceptor.  This is necessary for Angular to inject the interceptor.
 */
export const AuthInterceptorProvider = {
  provide: HTTP_INTERCEPTORS,
  useClass: AuthInterceptor,
  multi: true,
};