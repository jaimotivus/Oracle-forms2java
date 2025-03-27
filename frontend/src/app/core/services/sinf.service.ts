import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, of } from 'rxjs';
import { catchError, map, tap, retryWhen, delay, take } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { ErrorDTO } from '../../models/error.dto'; // Assuming you have a standard Error DTO
import { LogService } from '../log.service'; // Assuming you have a logging service
import { ClaimInfo } from '../../models/claim-info.model'; // Example: ClaimInfo model
import { Coverage } from '../../models/coverage.model'; // Example: Coverage model
import { Remesa } from '../../models/remesa.model'; // Example: Remesa model
import { AdjustmentData } from '../../models/adjustment-data.model'; // Example: AdjustmentData model

/**
 * Service for handling SINF module operations
 * Converted from Oracle Forms SINF501041_fmb.xml
 * This service handles reserve adjustment operations for insurance claims
 */
@Injectable({
  providedIn: 'root'
})
export class SinfService {
  private apiUrl = `${environment.apiUrl}/sinf`;
  private readonly API_ENDPOINTS = {
    CLAIMS: '/claims',
    SUM_ASSURED_PENDING: '/sum-assured-pending',
    COVERAGES: '/coverages',
    VALIDATE_ADJUSTMENT: '/validate-adjustment',
    ASSIGN_PRIORITY_MARKS: '/assign-priority-marks',
    ADJUST_RESERVE_PRIORITY: '/adjust-reserve-priority',
    GENERATE_ADJUSTMENT: '/generate-adjustment',
    INSERT_ADJUSTMENT_MOVEMENT: '/insert-adjustment-movement',
    DELETE_REMESA: '/delete-remesa',
    INSERT_REMESA: '/insert-remesa',
    VALIDATE_CAUSE_OF_DEATH: '/validate-cause-of-death',
    VALIDATE_SUM_PRIORITY: '/validate-sum-priority',
    COMMIT_ADJUSTMENTS: '/commit-adjustments',
    GENERATE_REMESA_ID: '/generate-remesa-id',
    LOG_MESSAGE: '/log-message',
    CONFIRM_DIALOG: '/confirm-dialog'
  };

  // BehaviorSubjects to store and share state across components
  private sumAssuredPendingSource = new BehaviorSubject<number>(0);
  sumAssuredPending$ = this.sumAssuredPendingSource.asObservable();

  private remesaCargaSource = new BehaviorSubject<string>('77777');
  remesaCarga$ = this.remesaCargaSource.asObservable();

  // Error code mappings (can be moved to a separate config file)
  private errorCodeMappings: { [key: number]: string } = {
    3113: 'Se ha perdido la conexión con la base de datos.',
    3114: 'Se han perdido sus credenciales inicie sesión nuevamente.',
    4068: 'El paquete ha sufrido cambios, vuelva iniciar sesión nuevamente.',
    4061: 'El paquete ha sufrido cambios, vuelva iniciar sesión nuevamente.'
  };

  constructor(
    private http: HttpClient,
    private logService: LogService // Inject the logging service
  ) { }

  /**
   * Get claim information
   * Equivalent to pInfoSiniestro procedure in the original form
   * @param branch Branch code
   * @param line Insurance line code
   * @param claimNumber Claim number
   * @returns Observable<ClaimInfo>
   */
  getClaimInfo(branch: number, line: number, claimNumber: number): Observable<ClaimInfo> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.CLAIMS}/${branch}/${line}/${claimNumber}`;
    return this.http.get<ClaimInfo>(url)
      .pipe(
        tap(response => {
          // Calculate and store sum assured pending
          this.calculateSumAssuredPending(
            branch,
            line,
            response.policy.policyNumber,
            response.policy.certificateNumber,
            response.occurrenceDate
          );
        }),
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Calculate sum assured pending
   * Equivalent to the functionality in PKG_SINT_LUC.fCalcula_Rva_Pendiente
   * @param branch Branch code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param occurrenceDate Occurrence date
   */
  private calculateSumAssuredPending(
    branch: number,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    occurrenceDate: Date
  ): void {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.SUM_ASSURED_PENDING}/${branch}/${line}/${policyNumber}/${certificateNumber}`;
    this.http.get<number>(url, { params: { occurrenceDate: occurrenceDate.toISOString() } })
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(error => {
          this.logService.logError(`Error fetching sum assured pending: ${error.message}`);
          this.sumAssuredPendingSource.next(0);
          return of(0); // Return a default value to avoid breaking the stream
        })
      )
      .subscribe(sumAssured => this.sumAssuredPendingSource.next(sumAssured));
  }

  /**
   * Get coverages for a claim
   * Equivalent to the query in the DT_COBERTURA block
   * @param branch Branch code
   * @param line Insurance line code
   * @param claimNumber Claim number
   * @returns Observable<Coverage[]>
   */
  getCoverages(branch: number, line: number, claimNumber: number): Observable<Coverage[]> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.COVERAGES}/${branch}/${line}/${claimNumber}`;
    return this.http.get<Coverage[]>(url)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Validate adjustment
   * Equivalent to fAjusteReserva function in PKG_AJUSTE_RESERVA
   * @param branch Branch code
   * @param line Insurance line code
   * @param claimNumber Claim number
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @param adjustmentAmount Adjustment amount
   * @param balance Balance amount
   * @param status Status code
   * @returns Observable<{ valid: boolean, message: string }>
   */
  validateAdjustment(
    branch: number,
    line: number,
    claimNumber: number,
    accountingLine: number,
    coverage: string,
    adjustmentAmount: number,
    balance: number,
    status: number
  ): Observable<{ valid: boolean, message: string }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.VALIDATE_ADJUSTMENT}`;
    const payload = {
      branch,
      line,
      claimNumber,
      accountingLine,
      coverage,
      adjustmentAmount,
      balance,
      status
    };

    return this.http.post<{ valid: boolean, message: string }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Assign priority marks to coverages
   * Equivalent to pAsignaMarca procedure in PKG_AJUSTE_RESERVA
   * @param coverages Array of coverage objects
   * @returns Observable<Coverage[]>
   */
  assignPriorityMarks(coverages: Coverage[]): Observable<Coverage[]> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.ASSIGN_PRIORITY_MARKS}`;
    return this.http.post<Coverage[]>(url, coverages)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Adjust reserve priority
   * Equivalent to P_Ajuste_Prioridad_R procedure in PKG_AJUSTE_RESERVA
   * @param branch Branch code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param occurrenceDate Occurrence date
   * @returns Observable<{ success: boolean, message: string }>
   */
  adjustReservePriority(
    branch: number,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    occurrenceDate: Date
  ): Observable<{ success: boolean, message: string }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.ADJUST_RESERVE_PRIORITY}`;
    const payload = {
      branch,
      line,
      policyNumber,
      certificateNumber,
      occurrenceDate: occurrenceDate.toISOString()
    };

    return this.http.post<{ success: boolean, message: string }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Generate reserve adjustment
   * Equivalent to fGeneraAjusteR function in PKG_DML
   * @param branch Branch code
   * @param claimNumber Claim number
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param adjustmentData Array of adjustment data
   * @returns Observable<{ success: boolean, message: string }>
   */
  generateReserveAdjustment(
    branch: number,
    claimNumber: number,
    accountingLine: number,
    coverage: string,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    adjustmentData: AdjustmentData[]
  ): Observable<{ success: boolean, message: string }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.GENERATE_ADJUSTMENT}`;
    const payload = {
      branch,
      claimNumber,
      accountingLine,
      coverage,
      line,
      policyNumber,
      certificateNumber,
      adjustmentData
    };

    return this.http.post<{ success: boolean, message: string }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Insert adjustment movement
   * Equivalent to fInsertaMovtoAjuste function in PKG_DML
   * @param branch Branch code
   * @param claimNumber Claim number
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @param movementNumber Movement number
   * @param amount Amount of movement
   * @param movementType Type of movement
   * @returns Observable<{ success: boolean, message: string }>
   */
  insertAdjustmentMovement(
    branch: number,
    claimNumber: number,
    accountingLine: number,
    coverage: string,
    movementNumber: number,
    amount: number,
    movementType: string
  ): Observable<{ success: boolean, message: string }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.INSERT_ADJUSTMENT_MOVEMENT}`;
    const payload = {
      branch,
      claimNumber,
      accountingLine,
      coverage,
      movementNumber,
      amount,
      movementType
    };

    return this.http.post<{ success: boolean, message: string }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Delete remesa
   * Equivalent to P_Delete_Remesa procedure in PKG_AJUSTE_RESERVA
   * @param branch Branch code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @returns Observable<{ success: boolean }>
   */
  deleteRemesa(
    branch: number,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    accountingLine: number,
    coverage: string
  ): Observable<{ success: boolean }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.DELETE_REMESA}`;
    const payload = {
      branch,
      line,
      policyNumber,
      certificateNumber,
      accountingLine,
      coverage
    };

    return this.http.post<{ success: boolean }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Insert remesa
   * Equivalent to P_Insert_Remesa procedure in PKG_AJUSTE_RESERVA
   * @param branch Branch code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param occurrenceDate Occurrence date
   * @param priority Priority number
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @param amount Amount of remesa
   * @returns Observable<{ success: boolean }>
   */
  insertRemesa(
    branch: number,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    occurrenceDate: Date,
    priority: number,
    accountingLine: number,
    coverage: string,
    amount: number
  ): Observable<{ success: boolean }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.INSERT_REMESA}`;
    const payload: Remesa = {
      branch,
      line,
      policyNumber,
      certificateNumber,
      occurrenceDate: occurrenceDate.toISOString(),
      priority,
      accountingLine,
      coverage,
      amount,
      remesaId: this.remesaCargaSource.value
    };

    return this.http.post<{ success: boolean }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Validate cause of death
   * Equivalent to fValida_CausaFallece function in PKG_AJUSTE_RESERVA
   * @param branch Branch code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param occurrenceDate Occurrence date
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @param movementType Type of movement
   * @param reserveAmount Reserve amount
   * @returns Observable<{ valid: boolean, message: string }>
   */
  validateCauseOfDeath(
    branch: number,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    occurrenceDate: Date,
    accountingLine: number,
    coverage: string,
    movementType: string,
    reserveAmount: number
  ): Observable<{ valid: boolean, message: string }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.VALIDATE_CAUSE_OF_DEATH}`;
    const payload = {
      branch,
      line,
      policyNumber,
      certificateNumber,
      occurrenceDate: occurrenceDate.toISOString(),
      accountingLine,
      coverage,
      movementType,
      reserveAmount
    };

    return this.http.post<{ valid: boolean, message: string }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Validate sum priority
   * Equivalent to F_ValidaSum_Prioridad function in PKG_AJUSTE_RESERVA
   * @param branch Branch code
   * @param line Insurance line code
   * @param policyNumber Policy number
   * @param certificateNumber Certificate number
   * @param occurrenceDate Occurrence date
   * @param accountingLine Accounting line number
   * @param coverage Coverage code
   * @param reserveAmount Reserve amount
   * @returns Observable<{ valid: boolean, message: string, adjustedAmount: number }>
   */
  validateSumPriority(
    branch: number,
    line: number,
    policyNumber: number,
    certificateNumber: number,
    occurrenceDate: Date,
    accountingLine: number,
    coverage: string,
    reserveAmount: number
  ): Observable<{ valid: boolean, message: string, adjustedAmount: number }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.VALIDATE_SUM_PRIORITY}`;
    const payload = {
      branch,
      line,
      policyNumber,
      certificateNumber,
      occurrenceDate: occurrenceDate.toISOString(),
      accountingLine,
      coverage,
      reserveAmount
    };

    return this.http.post<{ valid: boolean, message: string, adjustedAmount: number }>(url, payload)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Commit adjustments
   * Equivalent to KEY-COMMIT trigger
   * @param adjustmentData Array of adjustment data
   * @returns Observable<{ success: boolean, message: string }>
   */
  commitAdjustments(adjustmentData: any[]): Observable<{ success: boolean, message: string }> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.COMMIT_ADJUSTMENTS}`;
    return this.http.post<{ success: boolean, message: string }>(url, adjustmentData)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Generate a new remesa ID
   * Equivalent to the sequence generation in the original form
   */
  generateRemesaId(): void {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.GENERATE_REMESA_ID}`;
    this.http.get<{ remesaId: string }>(url)
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(error => {
          this.logService.logError(`Error generating remesa ID: ${error.message}`);
          this.remesaCargaSource.next('77777'); // Default value as in the original form
          return of({ remesaId: '77777' }); // Return a default value to avoid breaking the stream
        })
      )
      .subscribe(response => this.remesaCargaSource.next(response.remesaId));
  }

  /**
   * Display info message
   * Equivalent to Mensaje_Info procedure in PKG_MSGS
   * @param message The message to display
   * @returns Observable<void>
   */
  showInfoMessage(message: string): Observable<void> {
    return this.logMessage('INFO', message);
  }

  /**
   * Display error message
   * Equivalent to Mensaje_Error procedure in PKG_MSGS
   * @param message The message to display
   * @returns Observable<void>
   */
  showErrorMessage(message: string): Observable<void> {
    return this.logMessage('ERROR', message);
  }

  /**
   * Centralized logging function
   * @param type Log type (INFO, ERROR, WARNING)
   * @param message Log message
   * @returns Observable<void>
   */
  private logMessage(type: string, message: string): Observable<void> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.LOG_MESSAGE}`;
    return this.http.post<void>(url, { type, message })
      .pipe(
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Display confirmation dialog
   * Equivalent to Confirmar function in PKG_MSGS
   * @param message The message to display in the confirmation dialog
   * @returns Observable<boolean>
   */
  showConfirmation(message: string): Observable<boolean> {
    const url = `${this.apiUrl}${this.API_ENDPOINTS.CONFIRM_DIALOG}`;
    return this.http.post<{ confirmed: boolean }>(url, { message })
      .pipe(
        map(response => response.confirmed),
        retryWhen(errors => errors.pipe(delay(500), take(3))), // Retry mechanism
        catchError(this.handleError)
      );
  }

  /**
   * Error handler for HTTP requests
   * Centralized error handling logic
   * @param error HttpErrorResponse
   * @returns Observable<never>
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorDTO: ErrorDTO = {
      message: 'An unexpected error occurred.',
      code: error.status,
      friendlyMessage: 'An unexpected error occurred. Please try again later.'
    };

    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorDTO.message = `Client-side error: ${error.error.message}`;
      errorDTO.friendlyMessage = errorDTO.message;
    } else {
      // Server-side error
      errorDTO.message = `Server-side error: Code ${error.status}, Message: ${error.message}`;
      errorDTO.code = error.status;

      // Map specific error codes to user-friendly messages
      if (this.errorCodeMappings[error.status]) {
        errorDTO.friendlyMessage = this.errorCodeMappings[error.status];
      } else if (error.error && error.error.message) {
        errorDTO.friendlyMessage = error.error.message; // Use backend's friendly message if available
      }
    }

    this.logService.logError(errorDTO.message); // Log the detailed error message

    // Optionally, you can perform additional error handling tasks here,
    // such as displaying a global error notification or redirecting to an error page.

    return throwError(errorDTO); // Throw the error DTO for the component to handle
  }
}