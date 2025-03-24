import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * Interface representing a Siniestro (Claim)
 * Converted from Oracle Forms SINF501041_fmb.xml DB_SINIESTRO block
 */
export interface Siniestro {
  sisiCasuCdSucursal: number;
  sisiSicsCarpCdRamo: number;
  sisiNuSiniestro: number;
  sisiFechaOcurrencia: Date;
  dspCacnNmApellidoRazon: string;
  sisiStSiniestro: number;
  sisiDstSiniestro: string;
}

/**
 * Interface representing a Policy
 * Converted from Oracle Forms SINF501041_fmb.xml DB_POLIZA block
 */
export interface Poliza {
  siceCaceCasuCdSucursal: number;
  siceCaceCarpCdRamo: number;
  siceCaceCapoNuPoliza: number;
  siceCaceNuCertificado: number;
  siceNuBeneficiario: number;
}

/**
 * Interface representing a Coverage
 * Converted from Oracle Forms SINF501041_fmb.xml DT_COBERTURA block
 */
export interface Cobertura {
  carbCdRamo: number;
  carbDeRamo: string;
  cacbCdCobertura: string;
  cacbDeCobertura: string;
  prioridad: number;
  siccMtSumaseg: number;
  siccMtReserva: number;
  siccMtAjustado: number;
  siccMtLiquidacion: number;
  mtSaldo: number;
  ajusteReserva: number;
  mtSaldoNvo: number;
  siccMtAjustadoNvo: number;
  siccMtAjustadoMov: number;
  marcaVal: string;
  msjValida: string;
}

/**
 * Interface representing a Coverage Reserve
 * Converted from Oracle Forms SINF501041_fmb.xml DT_SINT_RESERVA_COBERTURA_CERT block
 */
export interface ReservaCobertura {
  siccSicoSisiNuSiniestro: number;
  siccSicoCacbCarbCdRamo: number;
  siccSicoCacbCdCobertura: string;
  dspCarbCdRamo: string;
  siccMtSumaseg: number;
  siccMtReserva: number;
  siccMtAjustado: number;
  siccMtLiquidacion: number;
  nsaldo: number;
  cobPrioridad: number;
  siccMtAjustadoMov: number;
}

/**
 * Service for handling Siniestro (Claim) operations
 * Converted from Oracle Forms SINF501041_fmb.xml
 */
@Injectable({
  providedIn: 'root'
})
export class SiniestroService {
  private apiUrl = `${environment.apiUrl}/siniestros`;
  
  // BehaviorSubjects to manage state across components
  private siniestroSubject = new BehaviorSubject<Siniestro | null>(null);
  private polizaSubject = new BehaviorSubject<Poliza | null>(null);
  private coberturasSubject = new BehaviorSubject<Cobertura[]>([]);
  private reservasCoberturasSubject = new BehaviorSubject<ReservaCobertura[]>([]);
  
  // Global variables converted from Oracle Forms
  private sapValue = 0;
  private sapXValue = 0;
  private remesaCarga = '77777';

  // Observable streams
  siniestro$ = this.siniestroSubject.asObservable();
  poliza$ = this.polizaSubject.asObservable();
  coberturas$ = this.coberturasSubject.asObservable();
  reservasCoberturas$ = this.reservasCoberturasSubject.asObservable();

  constructor(
    private http: HttpClient,
    private snackBar: MatSnackBar
  ) { }

  /**
   * Loads siniestro information
   * Converted from Oracle Forms pInfoSiniestro procedure
   */
  loadSiniestroInfo(sucursal: number, ramo: number, siniestro: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${sucursal}/${ramo}/${siniestro}`)
      .pipe(
        tap(response => {
          // Set siniestro data
          this.siniestroSubject.next(response.siniestro);
          
          // Set poliza data
          this.polizaSubject.next(response.poliza);
          
          // Set coberturas data
          this.coberturasSubject.next(response.coberturas);
          
          // Calculate SAP (Suma Asegurada Pendiente)
          this.sapValue = response.sapValue || 0;
          this.sapXValue = this.sapValue;
        }),
        catchError(this.handleError)
      );
  }

  /**
   * Assigns marks to coverages
   * Converted from Oracle Forms pAsignaMarca procedure
   */
  asignarMarcas(): void {
    const coberturas = this.coberturasSubject.getValue();
    
    if (!coberturas || coberturas.length === 0) return;
    
    const updatedCoberturas = coberturas.map(cobertura => {
      if (cobertura.siccMtAjustadoMov !== null && cobertura.prioridad !== null) {
        cobertura.marcaVal = 'P';
      } else if (cobertura.siccMtAjustadoMov !== null && cobertura.prioridad === null) {
        cobertura.marcaVal = 'N';
      } else {
        cobertura.marcaVal = 'S';
      }
      return cobertura;
    });
    
    this.coberturasSubject.next(updatedCoberturas);
  }

  /**
   * Assigns amounts to coverages
   * Converted from Oracle Forms pAsignaMontos procedure
   */
  asignarMontos(cobertura: Cobertura): Cobertura {
    const updatedCobertura = { ...cobertura };
    updatedCobertura.siccMtReserva = cobertura.siccMtReserva;
    updatedCobertura.siccMtSumaseg = cobertura.siccMtSumaseg;
    updatedCobertura.siccMtAjustadoNvo = cobertura.siccMtAjustado;
    updatedCobertura.mtSaldoNvo = cobertura.mtSaldo;
    updatedCobertura.marcaVal = 'S';
    return updatedCobertura;
  }

  /**
   * Cleans amounts from coverages
   * Converted from Oracle Forms pLimpiaMontos procedure
   */
  limpiarMontos(cobertura: Cobertura): Cobertura {
    const updatedCobertura = { ...cobertura };
    updatedCobertura.ajusteReserva = null;
    updatedCobertura.siccMtAjustadoNvo = null;
    updatedCobertura.mtSaldoNvo = null;
    updatedCobertura.msjValida = null;
    updatedCobertura.siccMtAjustadoMov = null;
    updatedCobertura.marcaVal = null;
    return updatedCobertura;
  }

  /**
   * Validates adjustment amount
   * Converted from Oracle Forms pValMt_Ajuste procedure
   */
  validarMontoAjuste(cobertura: Cobertura): { isValid: boolean, message: string } {
    let message = '';
    let isValid = true;

    if (cobertura.ajusteReserva < 0) {
      message = 'El ajuste no puede ser menor a 0(CERO)';
      isValid = false;
    } else if (cobertura.ajusteReserva < 0 && (cobertura.ajusteReserva * -1) > cobertura.siccMtSumaseg) {
      message = 'El ajuste de menos no debe ser mayor al saldo de la reserva.';
      isValid = false;
    }

    return { isValid, message };
  }

  /**
   * Validates adjustment reserve
   * Converted from Oracle Forms fAjusteReserva function
   */
  validarAjusteReserva(cobertura: Cobertura, validar: boolean = true): { isValid: boolean, message: string } {
    let message = '';
    let isValid = true;
    const siniestro = this.siniestroSubject.getValue();

    // Get current balance
    const saldo = this.calcularSaldo(cobertura);

    if (siniestro.sisiStSiniestro === 26) {
      if (cobertura.ajusteReserva < 0) {
        message = 'Error: No es permitido ingresar montos negativos en el ajuste.';
        isValid = false;
      } else if (cobertura.ajusteReserva === 0) {
        // In Angular we would handle this confirmation in the component
        // Here we just validate and return the message
        message = 'El Monto a ajustar es igual a cero, ¿desea continuar?';
        isValid = true; // This would be determined by user confirmation in the component
      }

      if (cobertura.mtSaldo === cobertura.ajusteReserva) {
        message = `Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: ${cobertura.ajusteReserva || 0} Saldo: ${cobertura.mtSaldo}`;
        isValid = false;
      }
    } else {
      // Check for payments
      // This would be a separate API call in a real implementation
      const hasPayments = false; // Placeholder for API response

      if (validar) {
        if (cobertura.ajusteReserva < 0) {
          message = 'Error: No es permitido ingresar montos negativos en el ajuste.';
          isValid = false;
        } else if (cobertura.ajusteReserva === 0) {
          message = 'El Monto a ajustar es igual a cero, ¿desea continuar?';
          isValid = true; // This would be determined by user confirmation in the component
        }

        if (cobertura.ajusteReserva < 0 && (cobertura.ajusteReserva * -1) > saldo) {
          message = 'Error: El ajuste de menos no debe ser mayor al saldo de la reserva.';
          isValid = false;
        }
      }

      if (cobertura.mtSaldo === cobertura.ajusteReserva) {
        message = `Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: ${cobertura.ajusteReserva || 0} Saldo: ${cobertura.mtSaldo}`;
        isValid = false;
      }

      // Validate suma asegurada
      if (cobertura.siccMtSumaseg !== 0) {
        // Check if coverage should be validated against suma asegurada
        const shouldValidate = true; // This would come from an API call in a real implementation

        if (cobertura.mtSaldoNvo > cobertura.siccMtSumaseg && shouldValidate) {
          // Get maximum days for indemnization
          const maxDays = 1; // This would come from an API call in a real implementation

          if (siniestro.sisiStSiniestro !== 26) {
            if (cobertura.mtSaldoNvo > (cobertura.siccMtSumaseg * maxDays)) {
              message = `Monto de la reserva debe ser menor o igual que la suma asegurada que es de ${cobertura.siccMtSumaseg * maxDays}`;
              isValid = false;
            }
          }
        }
      } else {
        message = 'Advertencia: Monto de suma asegurada es igual a cero';
        isValid = false;
      }

      // Additional validations for specific coverages would go here
      // For example, ACP coverage 003 validation, LUC validation, etc.
    }

    return { isValid, message };
  }

  /**
   * Calculates balance for a coverage
   * Converted from Oracle Forms fCalculaSaldo function
   */
  calcularSaldo(cobertura: Cobertura): number {
    // This would be an API call in a real implementation
    // Here we're just simulating the calculation
    return (cobertura.siccMtReserva + cobertura.siccMtAjustado) - 
           (cobertura.siccMtLiquidacion) + 
           (cobertura.siccMtReserva || 0);
  }

  /**
   * Validates if there are adjustments to apply
   * Converted from Oracle Forms fValidaAjuste function
   */
  validarAjustes(): { isValid: boolean, message: string } {
    const reservasCoberturas = this.reservasCoberturasSubject.getValue();
    let count = 0;
    
    reservasCoberturas.forEach(reserva => {
      if (reserva.siccSicoCacbCarbCdRamo !== null && 
          reserva.siccSicoCacbCdCobertura !== null) {
        count++;
      }
    });
    
    if (count === 0) {
      return { 
        isValid: false, 
        message: 'No existen ajustes de reservar por aplicar.' 
      };
    }
    
    return { isValid: true, message: '' };
  }

  /**
   * Applies priority to adjustments
   * Converted from Oracle Forms P_Ajuste_Prioridad_R procedure
   */
  ajustePrioridad(
    sucursal: number,
    ramo: number,
    poliza: number,
    certificado: number,
    fechaOcurrencia: Date
  ): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/ajuste-prioridad`, {
      sucursal,
      ramo,
      poliza,
      certificado,
      fechaOcurrencia
    }).pipe(
      tap(response => {
        // Update coberturas with validation results
        const coberturas = this.coberturasSubject.getValue();
        const updatedCoberturas = coberturas.map(cobertura => {
          const validationResult = response.validations.find(
            v => v.carbCdRamo === cobertura.carbCdRamo && 
                 v.cacbCdCobertura === cobertura.cacbCdCobertura
          );
          
          if (validationResult && validationResult.message) {
            cobertura.marcaVal = 'S';
            cobertura.msjValida = validationResult.message;
          }
          
          return cobertura;
        });
        
        this.coberturasSubject.next(updatedCoberturas);
      }),
      catchError(this.handleError)
    );
  }

  /**
   * Saves adjustment reserves
   * Converted from Oracle Forms KEY-COMMIT trigger
   */
  guardarAjustesReserva(): Observable<any> {
    const validacionAjustes = this.validarAjustes();
    
    if (!validacionAjustes.isValid) {
      return throwError(() => new Error(validacionAjustes.message));
    }
    
    const reservasCoberturas = this.reservasCoberturasSubject.getValue();
    const siniestro = this.siniestroSubject.getValue();
    const poliza = this.polizaSubject.getValue();
    
    // Prepare data for API call
    const ajustesData = reservasCoberturas.map(reserva => ({
      sucursal: siniestro.sisiCasuCdSucursal,
      siniestro: siniestro.sisiNuSiniestro,
      ramoCont: reserva.siccSicoCacbCarbCdRamo,
      cobertura: reserva.siccSicoCacbCdCobertura,
      ramo: poliza.siceCaceCarpCdRamo,
      poliza: poliza.siceCaceCapoNuPoliza,
      certificado: poliza.siceCaceNuCertificado,
      montoAjuste: reserva.siccMtAjustadoMov
    }));
    
    return this.http.post<any>(`${this.apiUrl}/guardar-ajustes`, ajustesData)
      .pipe(
        tap(response => {
          if (response.success) {
            const tipoMovto = response.tipoMovimiento;
            const descTipoMovto = response.descripcionMovimiento;
            
            this.snackBar.open(
              `Se realizó el movimiento de ${descTipoMovto} correctamente.`,
              'Cerrar',
              { duration: 5000 }
            );
            
            // Clear data after successful save
            this.siniestroSubject.next(null);
            this.polizaSubject.next(null);
            this.coberturasSubject.next([]);
            this.reservasCoberturasSubject.next([]);
          }
        }),
        catchError(this.handleError)
      );
  }

  /**
   * Inserts a remesa record
   * Converted from Oracle Forms P_Insert_Remesa procedure
   */
  insertarRemesa(
    sucursal: number,
    ramo: number,
    poliza: number,
    certificado: number,
    fechaOcurrencia: Date,
    prioridad: number,
    ramoCont: number,
    cobertura: string,
    monto: number
  ): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/remesa`, {
      sucursal,
      ramo,
      poliza,
      certificado,
      fechaOcurrencia,
      prioridad,
      ramoCont,
      cobertura,
      monto,
      idRemesa: this.remesaCarga
    }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Deletes a remesa record
   * Converted from Oracle Forms P_Delete_Remesa procedure
   */
  eliminarRemesa(
    sucursal: number,
    ramo: number,
    poliza: number,
    certificado: number,
    ramoCont: number,
    cobertura: string
  ): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/remesa`, {
      body: {
        sucursal,
        ramo,
        poliza,
        certificado,
        ramoCont,
        cobertura,
        idRemesa: this.remesaCarga
      }
    }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Error handler for HTTP requests
   */
  private handleError(error: HttpErrorResponse) {
    let errorMessage = '';
    
    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side error
      if (error.status === 0) {
        errorMessage = 'Se ha perdido la conexión con el servidor.';
      } else if (error.status === 401) {
        errorMessage = 'Se han perdido sus credenciales. Inicie sesión nuevamente.';
      } else if (error.status === 404) {
        errorMessage = 'El recurso solicitado no existe.';
      } else {
        errorMessage = `${error.error.message || 'Error en el servidor'}`;
      }
    }
    
    // Display error in UI
    this.snackBar.open(errorMessage, 'Cerrar', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
    
    // Return an observable with a user-facing error message
    return throwError(() => new Error(errorMessage));
  }
}