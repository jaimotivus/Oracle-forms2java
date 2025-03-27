/**
 * SINF Model - Angular TypeScript Model
 * Converted from Oracle Forms: SINF501041_fmb.xml
 *
 * This file contains the data models/interfaces for the SINF module, which was
 * originally an Oracle Forms application for managing insurance claim reserves.
 * The module handles adjustment of coverage reserves for insurance claims.
 *
 * Mapping from Oracle Forms:
 * - Blocks: DB_SINIESTRO, DB_POLIZA, DT_COBERTURA, DT_SINT_RESERVA_COBERTURA_CERT, DT_GLOBAL
 * - Packages: PKG_AJUSTE_RESERVA, PKG_DML, PKG_UTILERIAS
 */

import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Validators, AbstractControl, ValidatorFn } from '@angular/forms';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

/**
 * Siniestro (Claim) Model
 * Represents the main claim information from DB_SINIESTRO block
 */
export interface Siniestro {
  /** Branch office code */
  sucursal: number;
  /** Insurance line code */
  ramoPoliza: number;
  /** Claim number */
  numeroSiniestro: number;
  /** Occurrence date */
  fechaOcurrencia: Date;
  /** Insured name */
  nombreAsegurado: string;
  /** Claim status */
  estatusSiniestro: number;
  /** Claim status description */
  descripcionEstatusSiniestro: string;
}

/**
 * Policy Model
 * Represents the policy information from DB_POLIZA block
 */
export interface Poliza {
  /** Branch office code */
  sucursal: number;
  /** Insurance line code */
  ramoPoliza: number;
  /** Policy number */
  numeroPoliza: number;
  /** Certificate number */
  numeroCertificado: number;
  /** Beneficiary number */
  numeroBeneficiario?: number;
}

/**
 * Coverage Model
 * Represents the coverage information from DT_COBERTURA block
 */
export interface Cobertura {
  /** Accounting line code */
  ramoContable: number;
  /** Accounting line description */
  descripcionRamoContable: string;
  /** Coverage code */
  codigoCobertura: string;
  /** Coverage description */
  descripcionCobertura: string;
  /** Priority for LUC validation */
  prioridad?: number;
  /** Insured amount */
  montoSumaAsegurada: number;
  /** Initial reserve amount */
  montoReserva: number;
  /** Adjusted amount */
  montoAjustado: number;
  /** Payment amount */
  montoLiquidacion: number;
  /** Rejection amount */
  montoRechazo: number;
  /** Current balance */
  saldo: number;
  /** New adjusted amount */
  montoAjustadoNuevo?: number;
  /** Adjustment movement amount */
  montoAjustadoMovimiento?: number;
  /** New balance */
  saldoNuevo?: number;
  /** Adjustment amount (user input) */
  ajusteReserva?: number;
  /** Validation mark (K, N, P, S) */
  marcaValidacion?: string;
  /** Validation message */
  mensajeValidacion?: string;
}

/**
 * Reserve Coverage Certificate Model
 * Represents the reserve coverage certificate information from DT_SINT_RESERVA_COBERTURA_CERT block
 */
export interface ReservaCoberturasCertificado {
  /** Accounting line code */
  ramoContable: number;
  /** Accounting line description */
  descripcionRamoContable: string;
  /** Coverage code */
  codigoCobertura: string;
  /** Coverage description */
  descripcionCobertura: string;
  /** Insured amount */
  montoSumaAsegurada: number;
  /** Initial reserve amount */
  montoReserva: number;
  /** Adjusted amount */
  montoAjustado: number;
  /** Adjustment movement amount */
  montoAjustadoMovimiento?: number;
  /** Payment amount */
  montoLiquidacion: number;
  /** Current balance */
  saldo: number;
  /** Priority for LUC validation */
  prioridad?: number;
}

/**
 * Global Data Model
 * Represents the global data from DT_GLOBAL block
 */
export interface DatosGlobales {
  /** Effective date */
  fechaEfectiva?: Date;
  /** Plan code */
  codigoPlan?: number;
  /** Insured amount */
  montoSumaAsegurada?: number;
  /** Pending sum insured for reserve-priority */
  saldoAseguradoPendiente?: number;
}

/**
 * Form Validation Constants
 * Validation rules extracted from the original Oracle Forms
 */
export const VALIDACION_FORMULARIO = {
  ajusteReserva: {
    required: true,
    min: 0,
    validators: [Validators.required, Validators.min(0), Validators.pattern(/^\d+(\.\d{1,2})?$/)] // Added pattern validator for numeric with up to 2 decimals
  },
  // Additional validation rules can be added here
};

/**
 * Alert Types
 * Represents the different alert types from the original Oracle Forms
 */
export enum TipoAlerta {
  RESERVAS = 'RESERVAS',
  CEROS = 'CEROS',
  INFO = 'INFO_A',
  PREGUNTA = 'ASK_INFO',
  ERROR = 'ERROR_A'
}

/**
 * Movement Types
 * Represents the different movement types for reserve adjustments
 */
export enum TipoMovimiento {
  RESERVA_AJUSTE = 'RA',
  RESERVA_LIBERACION = 'RL',
  RESERVA_RECHAZO = 'RX'
}

/**
 * SINF Module Configuration
 * Configuration settings for the SINF module
 */
export interface ConfiguracionSINF {
  titulo: string;
  version: string;
  fechaVersion: string;
  conexion: string;
}

/**
 * SINF Module Parameters
 * Parameters for the SINF module
 */
export interface ParametrosSINF {
  sucursal: number;
  ramo: number;
  siniestro: number;
  ramoContable?: number;
  cobertura?: string;
}

/**
 * Reserve Adjustment Request
 * Data structure for submitting a reserve adjustment
 */
export interface SolicitudAjusteReserva {
  parametros: ParametrosSINF;
  siniestro: Siniestro;
  poliza: Poliza;
  coberturas: Cobertura[];
  reservasCoberturasCertificado: ReservaCoberturasCertificado[];
  datosGlobales: DatosGlobales;
}

/**
 * Reserve Adjustment Response
 * Data structure for the response from a reserve adjustment operation
 */
export interface RespuestaAjusteReserva {
  exito: boolean;
  mensaje: string;
  tipoMovimiento?: string;
  descripcionTipoMovimiento?: string;
  numeroMovimiento?: number;
  fechaMovimiento?: Date;
}

/**
 * Utility class for reserve adjustment operations
 * Equivalent to PKG_AJUSTE_RESERVA in Oracle Forms
 */
@Injectable({
  providedIn: 'root'
})
export class AjusteReservaUtil {

  /**
   * Validates if the adjustment amount is valid
   * @param cobertura The coverage to validate
   * @returns An object with validation result and message
   */
  validarAjusteReserva(cobertura: Cobertura): { valido: boolean, mensaje: string } {
    if (cobertura.ajusteReserva === undefined) {
      return { valido: false, mensaje: 'El ajuste de reserva es requerido.' };
    }

    if (cobertura.ajusteReserva < 0) {
      return { valido: false, mensaje: 'El ajuste de reserva no puede ser negativo.' };
    }

    if (cobertura.ajusteReserva > cobertura.montoSumaAsegurada) {
      return { valido: false, mensaje: 'El ajuste de reserva no puede ser mayor al monto asegurado.' };
    }

    // Add more complex validation rules here based on business requirements
    // Example: Check against a maximum adjustment percentage

    return { valido: true, mensaje: '' };
  }

  /**
   * Calculates the new balance after an adjustment
   * @param cobertura The coverage to calculate
   * @returns The new balance
   */
  calcularSaldoNuevo(cobertura: Cobertura): number {
    if (cobertura.ajusteReserva === undefined || cobertura.montoReserva === undefined || cobertura.montoAjustado === undefined || cobertura.montoLiquidacion === undefined || cobertura.montoRechazo === undefined) {
      console.error('Missing required properties for calculating saldoNuevo', cobertura);
      return cobertura.saldo; // Return the original balance if any of the required properties are missing.
    }

    return (cobertura.montoReserva + cobertura.montoAjustado + cobertura.ajusteReserva) - (cobertura.montoLiquidacion + cobertura.montoRechazo);
  }

  /**
   * Calculates the adjustment movement amount
   * @param cobertura The coverage to calculate
   * @returns The adjustment movement amount
   */
  calcularMontoAjustadoMovimiento(cobertura: Cobertura): number {
    if (cobertura.ajusteReserva === undefined || cobertura.saldo === undefined) {
      console.warn('ajusteReserva or saldo is undefined. Returning 0.');
      return 0;
    }

    return cobertura.ajusteReserva - cobertura.saldo;
  }
}

/**
 * Utility class for DML operations
 * Equivalent to PKG_DML in Oracle Forms
 */
@Injectable({
  providedIn: 'root'
})
export class DMLUtil {

  private apiUrl = '/api/ajusteReserva'; // Replace with your actual API endpoint

  constructor(private http: HttpClient) { }

  /**
   * Registers a reserve adjustment
   * @param solicitud The reserve adjustment request
   * @returns An Observable with the operation result
   */
  registrarAjusteReserva(solicitud: SolicitudAjusteReserva): Observable<RespuestaAjusteReserva> {
    return this.http.post<RespuestaAjusteReserva>(this.apiUrl, solicitud)
      .pipe(
        map((response: RespuestaAjusteReserva) => {
          // Optionally transform the response if needed
          return response;
        }),
        catchError((error: HttpErrorResponse) => {
          console.error('Error registering reserve adjustment', error);
          let errorMessage = 'An unknown error occurred!';
          if (error.error instanceof ErrorEvent) {
            // Client-side errors
            errorMessage = `Error: ${error.error.message}`;
          } else {
            // Server-side errors
            errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
          }
          return throwError(() => new Error(errorMessage)); // Re-throw the error for the component to handle
        })
      );
  }
}

/**
 * Utility class for general operations
 * Equivalent to PKG_UTILERIAS in Oracle Forms
 */
@Injectable({
  providedIn: 'root'
})
export class Utilerias {
  /**
   * Gets the movement type description
   * @param tipoMovimiento The movement type code
   * @returns The movement type description
   */
  obtenerDescripcionTipoMovimiento(tipoMovimiento: string): string {
    const descripciones: { [key: string]: string } = {
      'RA': 'Ajuste de Reserva',
      'RL': 'Liberación de Reserva',
      'RX': 'Rechazo de Reserva'
    };
    return descripciones[tipoMovimiento] || tipoMovimiento;
  }

  /**
   * Calculates the balance for a coverage
   * @param cobertura The coverage to calculate
   * @returns The calculated balance
   */
  calcularSaldo(cobertura: Cobertura): number {
    if (cobertura.montoReserva === undefined || cobertura.montoAjustado === undefined || cobertura.montoLiquidacion === undefined || cobertura.montoRechazo === undefined) {
      console.error('Missing required properties for calculating saldo', cobertura);
      return 0; // Or throw an error, depending on the desired behavior
    }
    return (cobertura.montoReserva + cobertura.montoAjustado) -
      (cobertura.montoLiquidacion) +
      cobertura.montoRechazo;
  }
}