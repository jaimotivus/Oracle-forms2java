/**
 * Siniestro Model
 * 
 * This model represents a claim (siniestro) in the insurance system.
 * Converted from Oracle Forms source: SINF501041_fmb.xml
 * 
 * The model includes properties for claim details, policy information,
 * coverage details, and reserve adjustments.
 */
export interface Siniestro {
  // Basic claim information
  sisiCasuCdSucursal: number;       // Branch code
  sisiSicsCarpCdRamo: number;       // Line of business code
  sisiNuSiniestro: number;          // Claim number
  sisiFechaOcurrencia: Date;        // Occurrence date
  sisiStSiniestro: number;          // Claim status
  sisiDstSiniestro: string;         // Claim status description
  
  // Policyholder information
  dspCacnNmApellidoRazon: string;   // Policyholder name/company name
  
  // Policy information
  siceCaceCasuCdSucursal: number;   // Policy branch code
  siceCaceCarpCdRamo: number;       // Policy line of business code
  siceCaceCapoNuPoliza: number;     // Policy number
  siceCaceNuCertificado: number;    // Certificate number
  siceNuBeneficiario: number;       // Beneficiary number
  
  // Coverage information
  coberturas: Cobertura[];          // List of coverages
  
  // Reserve adjustment information
  reservaCoberturaCert: ReservaCoberturaCert[]; // List of coverage reserves
}

/**
 * Cobertura Model
 * 
 * This model represents a coverage associated with a claim.
 * It includes details about the coverage, sum insured, and reserve amounts.
 */
export interface Cobertura {
  carbCdRamo: number;               // Accounting line of business code
  carbDeRamo: string;               // Accounting line of business description
  cacbCdCobertura: string;          // Coverage code
  cacbDeCobertura: string;          // Coverage description
  prioridad: number;                // Priority (used for LUC calculations)
  
  // Financial amounts
  siccMtSumaseg: number;            // Sum insured amount
  siccMtReserva: number;            // Initial reserve amount
  siccMtAjustado: number;           // Adjusted amount
  siccMtLiquidacion: number;        // Liquidation amount
  siccMtRechazo: number;            // Rejected amount
  mtSaldo: number;                  // Current balance
  
  // Adjustment values
  ajusteReserva: number;            // Reserve adjustment amount
  mtSaldoNvo: number;               // New balance after adjustment
  siccMtAjustadoNvo: number;        // New adjusted amount
  siccMtAjustadoMov: number;        // Movement adjusted amount
  
  // Validation fields
  marcaVal: string;                 // Validation mark (S, N, P, K)
  msjValida: string;                // Validation message
  marcaPrio: number;                // Priority mark
}

/**
 * ReservaCoberturaCert Model
 * 
 * This model represents the reserve details for a specific coverage certificate.
 * It includes information about the reserve amounts, adjustments, and related policy details.
 */
export interface ReservaCoberturaCert {
  // Claim and coverage identification
  siccSisiCasuCdSucursa: number;    // Claim branch code
  siccSicoSisiNuSiniestro: number;  // Claim number
  siccSicoCacbCarbCdRamo: number;   // Coverage accounting line of business code
  siccSicoCacbCdCobertura: string;  // Coverage code
  
  // Policy identification
  siccCaceCasuCdSucursa: number;    // Policy branch code
  siccSiceCaceCarpCdRamo: number;   // Policy line of business code
  siccCaceCapoNuPoliza: number;     // Policy number
  siccCaceNuCertificado: number;    // Certificate number
  siccCarcNuAsegurado: number;      // Insured number
  
  // Financial amounts
  siccMtPago: number;               // Payment amount
  siccMtReserva: number;            // Reserve amount
  siccMtAjustado: number;           // Adjusted amount
  siccMtAjustadoMov: number;        // Movement adjusted amount
  siccMtLiquidacion: number;        // Liquidation amount
  siccMtRechazo: number;            // Rejected amount
  siccMtSumaseg: number;            // Sum insured amount
  siccMtDeducible: number;          // Deductible amount
  
  // Display fields
  dspCarbCdRamo: string;            // Accounting line of business description
  dspCacbDeCobertura: string;       // Coverage description
  
  // Balance and priority
  nSaldo: number;                   // Current balance
  cobPrioridad: number;             // Coverage priority
  
  // Effective date
  siccCarcFeEfectiva: Date;         // Effective date
}

/**
 * GlobalData Model
 * 
 * This model represents global data used across the application.
 */
export interface GlobalData {
  siccCarcFeEfectiva: Date;         // Effective date
  cdPlan: number;                   // Plan code
  mtSumaAsegurada: number;          // Sum insured amount
  remesaCarga: string;              // Remittance load
  sap: number;                      // Pending sum insured amount
  sapX: number;                     // Temporary pending sum insured amount
}

/**
 * AjusteReservaRequest Model
 * 
 * This model represents a request to adjust a reserve.
 */
export interface AjusteReservaRequest {
  sucursal: number;                 // Branch code
  siniestro: number;                // Claim number
  ramo: number;                     // Line of business code
  ramoContable: number;             // Accounting line of business code
  cobertura: string;                // Coverage code
  ajusteReserva: number;            // Reserve adjustment amount
}

/**
 * AjusteReservaResponse Model
 * 
 * This model represents a response from a reserve adjustment operation.
 */
export interface AjusteReservaResponse {
  success: boolean;                 // Operation success indicator
  message: string;                  // Response message
  tipoMovimiento: string;           // Movement type
  descTipoMovimiento: string;       // Movement type description
  numeroMovimiento: number;         // Movement number
}

/**
 * ValidationResult Model
 * 
 * This model represents the result of a validation operation.
 */
export interface ValidationResult {
  valid: boolean;                   // Validation result
  message: string;                  // Validation message
  data?: any;                       // Additional data
}

/**
 * Enums for status values and validation marks
 */
export enum EstadoSiniestro {
  ABIERTO = 24,
  CERRADO = 25,
  RECHAZADO = 26
}

export enum MarcaValidacion {
  PENDIENTE = 'S',
  VALIDADO = 'N',
  PRIORIDAD = 'P',
  AJUSTADO = 'K'
}

/**
 * Helper functions for working with siniestro data
 */
export class SiniestroUtils {
  /**
   * Calculates the new balance after an adjustment
   * 
   * @param currentBalance Current balance
   * @param adjustmentAmount Adjustment amount
   * @returns New balance
   */
  static calculateNewBalance(currentBalance: number, adjustmentAmount: number): number {
    return adjustmentAmount;
  }

  /**
   * Calculates the new adjusted amount
   * 
   * @param currentAdjusted Current adjusted amount
   * @param currentBalance Current balance
   * @param adjustmentAmount Adjustment amount
   * @returns New adjusted amount
   */
  static calculateNewAdjustedAmount(
    currentAdjusted: number, 
    currentBalance: number, 
    adjustmentAmount: number
  ): number {
    return currentAdjusted + (adjustmentAmount - currentBalance);
  }

  /**
   * Validates if an adjustment amount is valid
   * 
   * @param adjustmentAmount Adjustment amount
   * @param currentBalance Current balance
   * @param sumInsured Sum insured amount
   * @param claimStatus Claim status
   * @returns Validation result
   */
  static validateAdjustment(
    adjustmentAmount: number,
    currentBalance: number,
    sumInsured: number,
    claimStatus: number
  ): ValidationResult {
    // Negative adjustment validation
    if (adjustmentAmount < 0) {
      return {
        valid: false,
        message: 'Error: No es permitido ingresar montos negativos en el ajuste.'
      };
    }

    // Zero adjustment validation
    if (adjustmentAmount === 0) {
      return {
        valid: true,
        message: 'El Monto a ajustar es igual a cero, ¿desea continuar?'
      };
    }

    // Same as current balance validation
    if (currentBalance === adjustmentAmount) {
      return {
        valid: false,
        message: `Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: ${adjustmentAmount} Saldo: ${currentBalance}`
      };
    }

    // Sum insured validation
    if (sumInsured === 0) {
      return {
        valid: false,
        message: 'Advertencia: Monto de suma asegurada es igual a cero'
      };
    }

    // Exceeds sum insured validation
    if (adjustmentAmount > sumInsured && claimStatus !== EstadoSiniestro.RECHAZADO) {
      return {
        valid: false,
        message: `Monto de la reserva debe ser menor o igual que la suma asegurada que es de ${sumInsured}`
      };
    }

    return { valid: true, message: 'OK' };
  }
}