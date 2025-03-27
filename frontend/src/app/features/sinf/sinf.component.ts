import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subject, of } from 'rxjs';
import { takeUntil, catchError, finalize, tap, switchMap } from 'rxjs/operators';

import { SinfService } from './sinf.service';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { AlertDialogComponent } from '../../shared/components/alert-dialog/alert-dialog.component';
import { AuthService } from '../../core/services/auth.service';

// Interfaces for type safety
interface Siniestro {
  sisi_casu_cd_sucursal: number;
  sisi_sics_carp_cd_ramo: number;
  sisi_nu_siniestro: number;
  sisi_fe_ocurrencia: Date;
  dsp_cacn_nm_apellido_razon: string;
  sisi_st_siniestro: string;
  sisi_dst_siniestro: string;
}

interface Poliza {
  sice_cace_casu_cd_sucursal: number;
  sice_cace_carp_cd_ramo: number;
  sice_cace_capo_nu_poliza: number;
  sice_cace_nu_certificado: number;
  sice_nu_beneficiario: number;
}

interface Cobertura {
  carb_cd_ramo: number;
  carb_de_ramo: string;
  cacb_cd_cobertura: string;
  cacb_de_cobertura: string;
  sicc_mt_sumaseg: number;
  sicc_mt_sumaseg_nvo: number;
  sicc_mt_reserva: number;
  sicc_mt_reserva_nvo: number;
  sicc_mt_ajustado: number;
  sicc_mt_ajustado_nvo: number;
  sicc_mt_ajustado_mov: number | null;
  sicc_mt_liquidacion: number;
  sicc_mt_liquidacion_nvo: number;
  sicc_mt_rechazo: number;
  mt_saldo: number;
  mt_saldo_nvo: number;
  prioridad: number | null;
  marca_prio: string | null;
  marca_val: string | null;
  msj_valida: string | null;
  ajuste_reserva: number | null;
}

interface ReservaCoberturaCert {
  sicc_sico_cacb_carb_cd_ramo: number;
  dsp_carb_cd_ramo: string;
  sicc_sico_cacb_cd_cobertura: string;
  dsp_cacb_de_cobertura: string;
  sicc_mt_sumaseg: number;
  sicc_mt_reserva: number;
  sicc_mt_ajustado: number;
  sicc_mt_ajustado_mov: number | null;
  sicc_mt_liquidacion: number;
  sicc_mt_rechazo: number;
  nsaldo: number;
  cob_prioridad: number | null;
}

/**
 * SINF Component - Converted from Oracle Forms SINF501041_fmb.xml
 *
 * This component handles the "Modificación Reserva de Coberturas" functionality
 * which allows users to adjust insurance coverage reserves.
 */
@Component({
  selector: 'app-sinf',
  templateUrl: './sinf.component.html',
  styleUrls: ['./sinf.component.scss']
})
export class SinfComponent implements OnInit, OnDestroy {
  // Main form groups
  mainForm: FormGroup;

  // Data models
  siniestro: Siniestro;
  poliza: Poliza;
  coberturasData: Cobertura[] = [];
  reservaCoberturaData: ReservaCoberturaCert[] = [];

  // UI control variables
  loading = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  isRamo13: boolean = false; // Controls the state of the MT_PRIORIDAD button

  // Global variables (equivalent to Oracle Forms globals)
  globalSAP: number = 0;
  globalSAPX: number = 0;
  globalRemesaCarga: string = '77777';

  // Parameters (equivalent to Oracle Forms parameters)
  sucursal: number;
  ramo: number;
  siniestro_num: number;
  ramoC: number;
  cobertura: string;

  // User information
  currentUser: string;
  currentDate: Date = new Date();
  connectionInfo: string;

  // Subject for handling unsubscribe on component destroy
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private sinfService: SinfService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private authService: AuthService
  ) { }

  ngOnInit(): void {
    this.initializeForm();
    this.loadUserInfo();

    // Get route parameters (equivalent to Oracle Forms parameters)
    this.route.params.pipe(
      takeUntil(this.destroy$)
    ).subscribe(params => {
      this.sucursal = +params['sucursal'] || 5; // Default value from original form
      this.ramo = +params['ramo'] || 13; // Default value from original form
      this.siniestro_num = +params['siniestro'] || 130001125; // Default value from original form
      this.ramoC = +params['ramoC'] || null;
      this.cobertura = params['cobertura'] || null;

      // Load initial data
      this.loadSiniestroInfo();
    });
  }

  ngOnDestroy(): void {
    // Clean up subscriptions
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Initialize reactive form structure
   * Equivalent to the form blocks in Oracle Forms
   */
  initializeForm(): void {
    this.mainForm = this.fb.group({
      siniestro: this.fb.group({
        sisi_casu_cd_sucursal: [null],
        sisi_sics_carp_cd_ramo: [null],
        sisi_nu_siniestro: [null],
        sisi_fe_ocurrencia: [null],
        dsp_cacn_nm_apellido_razon: [null],
        sisi_st_siniestro: [null],
        sisi_dst_siniestro: [null]
      }),
      poliza: this.fb.group({
        sice_cace_casu_cd_sucursal: [null],
        sice_cace_carp_cd_ramo: [null],
        sice_cace_capo_nu_poliza: [null],
        sice_cace_nu_certificado: [null],
        sice_nu_beneficiario: [null]
      }),
      coberturas: this.fb.array([]),
      reservaCoberturaCert: this.fb.array([])
    });
  }

  /**
   * Load user information
   * Equivalent to P_InformaDatosToolbar procedure in Oracle Forms
   */
  loadUserInfo(): void {
    this.currentUser = this.authService.getCurrentUser();
    this.connectionInfo = 'PRODUCCIÓN'; // This would come from a service in a real implementation
  }

  /**
   * Load siniestro information
   * Equivalent to pInfoSiniestro procedure in Oracle Forms
   */
  loadSiniestroInfo(): void {
    this.loading = true;

    this.sinfService.getSiniestroInfo(this.sucursal, this.ramo, this.siniestro_num)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.loading = false),
        catchError(error => {
          this.handleError('Error al cargar información del siniestro', error);
          return of(null);
        })
      )
      .subscribe(data => {
        if (data) {
          // Update form with siniestro data
          this.siniestro = data.siniestro;
          this.poliza = data.poliza;

          // Update form controls
          this.mainForm.get('siniestro').patchValue(this.siniestro);
          this.mainForm.get('poliza').patchValue(this.poliza);

          // Load coberturas
          this.loadCoberturas();

          // Set global SAP value
          this.globalSAP = this.sinfService.calculateRvaPendiente(
            this.poliza.sice_cace_casu_cd_sucursal,
            this.poliza.sice_cace_carp_cd_ramo,
            this.poliza.sice_cace_capo_nu_poliza,
            this.poliza.sice_cace_nu_certificado,
            this.siniestro.sisi_fe_ocurrencia,
            10 // Default value for ramoContable
          );

          this.globalSAPX = this.globalSAP;

          // Enable/disable MT_PRIORIDAD button based on ramo
          // Equivalent to the conditional in WHEN-NEW-FORM-INSTANCE
          this.isRamo13 = this.ramo === 13;
        }
      });
  }

  /**
   * Load coberturas data
   * Equivalent to the query in the DT_COBERTURA block
   */
  loadCoberturas(): void {
    this.loading = true;

    this.sinfService.getCoberturas(this.sucursal, this.ramo, this.siniestro_num)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.loading = false),
        catchError(error => {
          this.handleError('Error al cargar coberturas', error);
          return of([]);
        })
      )
      .subscribe(data => {
        this.coberturasData = data;

        // Clear existing form array
        this.clearFormArray(this.coberturas);

        // Add coberturas to form array
        data.forEach(cobertura => {
          this.coberturas.push(this.createCoberturaFormGroup(cobertura));
        });
      });
  }

  /**
   * Helper method to clear a FormArray
   * @param formArray The FormArray to clear
   */
  private clearFormArray(formArray: FormArray): void {
    while (formArray.length !== 0) {
      formArray.removeAt(0);
    }
  }

  /**
   * Create form group for a cobertura
   * @param cobertura The cobertura data
   */
  createCoberturaFormGroup(cobertura: Cobertura): FormGroup {
    return this.fb.group({
      carb_cd_ramo: [cobertura.carb_cd_ramo],
      carb_de_ramo: [cobertura.carb_de_ramo],
      cacb_cd_cobertura: [cobertura.cacb_cd_cobertura],
      cacb_de_cobertura: [cobertura.cacb_de_cobertura],
      sicc_mt_sumaseg: [cobertura.sicc_mt_sumaseg],
      sicc_mt_sumaseg_nvo: [cobertura.sicc_mt_sumaseg],
      sicc_mt_reserva: [cobertura.sicc_mt_reserva],
      sicc_mt_reserva_nvo: [cobertura.sicc_mt_reserva],
      sicc_mt_ajustado: [cobertura.sicc_mt_ajustado],
      sicc_mt_ajustado_nvo: [cobertura.sicc_mt_ajustado],
      sicc_mt_ajustado_mov: [null],
      sicc_mt_liquidacion: [cobertura.sicc_mt_liquidacion],
      sicc_mt_liquidacion_nvo: [cobertura.sicc_mt_liquidacion],
      sicc_mt_rechazo: [cobertura.sicc_mt_rechazo],
      mt_saldo: [cobertura.mt_saldo],
      mt_saldo_nvo: [cobertura.mt_saldo],
      prioridad: [cobertura.prioridad],
      marca_prio: [null],
      marca_val: [null],
      msj_valida: [null],
      ajuste_reserva: [null, [Validators.min(0)]]
    });
  }

  /**
   * Get coberturas form array
   */
  get coberturas(): FormArray {
    return this.mainForm.get('coberturas') as FormArray;
  }

  /**
   * Get reserva cobertura cert form array
   */
  get reservaCoberturaCert(): FormArray {
    return this.mainForm.get('reservaCoberturaCert') as FormArray;
  }

  /**
   * Handle form submission
   * Equivalent to KEY-COMMIT trigger
   */
  onSubmit(): void {
    if (this.mainForm.invalid) {
      this.snackBar.open('Por favor corrija los errores en el formulario', 'Cerrar', {
        duration: 5000
      });
      return;
    }

    // Validate adjustments
    if (!this.validateAjustes()) {
      return;
    }

    // Confirm submission
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Confirmar Ajustes',
        message: '¿Está seguro que desea guardar los ajustes de reserva?'
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.saveAjustes();
      }
    });
  }

  /**
   * Validate ajustes
   * Equivalent to fValidaAjuste function in PKG_UTILERIAS
   */
  validateAjustes(): boolean {
    const reservaCoberturaCert = this.mainForm.get('reservaCoberturaCert') as FormArray;

    if (reservaCoberturaCert.length === 0) {
      this.showErrorMessage('No existen ajustes de reservar por aplicar.');
      return false;
    }

    return true;
  }

  /**
   * Save ajustes
   * Equivalent to the logic in KEY-COMMIT trigger
   */
  saveAjustes(): void {
    this.loading = true;

    const reservaCoberturaCert = this.reservaCoberturaCert.value;

    this.sinfService.saveAjustes(
      this.sucursal,
      this.siniestro_num,
      reservaCoberturaCert
    ).pipe(
      takeUntil(this.destroy$),
      finalize(() => this.loading = false),
      catchError(error => {
        this.handleError('Error al guardar ajustes', error);
        return of(null);
      })
    ).subscribe(response => {
      if (response) {
        this.showSuccessMessage('Se realizó el movimiento de Ajuste de Reserva correctamente.');

        // Navigate back or clear form
        this.showConfirmationAndNavigate('/siniestros', 'Ajustes guardados correctamente.');
      }
    });
  }

  /**
   * Handle ajuste reserva change
   * Equivalent to WHEN-VALIDATE-ITEM trigger on AJUSTE_RESERVA
   * @param index Index of the cobertura in the form array
   */
  onAjusteReservaChange(index: number): void {
    const coberturaFormGroup = this.coberturas.at(index) as FormGroup;
    const ajusteReserva = coberturaFormGroup.get('ajuste_reserva').value;

    // Clear validation message
    coberturaFormGroup.get('msj_valida').setValue(null);

    if (ajusteReserva === null) {
      this.asignaMontos(coberturaFormGroup);
    } else {
      this.validateAjusteReserva(coberturaFormGroup)
        .subscribe(isValid => {
          if (!isValid) {
            return; // Validation failed, do not proceed
          }
        });
    }
  }

  /**
   * Validate ajuste reserva
   * Equivalent to fAjusteReserva function in PKG_AJUSTE_RESERVA
   * @param coberturaFormGroup The cobertura form group
   * @returns Observable<boolean> - Indicates whether the validation was successful
   */
  validateAjusteReserva(coberturaFormGroup: FormGroup): Observable<boolean> {
    const ajusteReserva = coberturaFormGroup.get('ajuste_reserva').value;
    const mtSaldo = coberturaFormGroup.get('mt_saldo').value;
    const siccMtSumaseg = coberturaFormGroup.get('sicc_mt_sumaseg').value;

    // Validate that ajuste is not negative
    if (ajusteReserva < 0) {
      coberturaFormGroup.get('msj_valida').setValue('Error: No es permitido ingresar montos negativos en el ajuste.');
      coberturaFormGroup.get('marca_val').setValue('S');
      return of(false);
    }

    // Validate that ajuste is not zero
    if (ajusteReserva === 0) {
      const dialogRef = this.dialog.open(ConfirmDialogComponent, {
        width: '400px',
        data: {
          title: 'Confirmar',
          message: 'El Monto a ajustar es igual a cero, ¿desea continuar?'
        }
      });

      return dialogRef.afterClosed().pipe(
        tap(result => {
          if (!result) {
            // User cancelled the operation
            coberturaFormGroup.get('ajuste_reserva').setValue(null); // Clear the input
          }
        }),
        map(result => result ? true : false) // Return true if user confirms, false otherwise
      );
    }

    // Validate that ajuste is not equal to saldo
    if (mtSaldo === ajusteReserva) {
      coberturaFormGroup.get('msj_valida').setValue(`Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: ${ajusteReserva} Saldo: ${mtSaldo}`);
      return of(false);
    }

    // Calculate new values
    const siccMtAjustadoNvo = ajusteReserva - mtSaldo;
    const siccMtAjustadoMov = ajusteReserva - mtSaldo;
    const mtSaldoNvo = ajusteReserva;

    coberturaFormGroup.get('sicc_mt_ajustado_nvo').setValue(siccMtAjustadoNvo);
    coberturaFormGroup.get('sicc_mt_ajustado_mov').setValue(siccMtAjustadoMov);
    coberturaFormGroup.get('mt_saldo_nvo').setValue(mtSaldoNvo);

    // Validate against suma asegurada
    if (siccMtSumaseg !== 0 && mtSaldoNvo > siccMtSumaseg) {
      coberturaFormGroup.get('msj_valida').setValue(`Monto de la reserva debe ser menor o igual que la suma asegurada que es de ${siccMtSumaseg}`);
      return of(false);
    }

    return of(true);
  }

  /**
   * Assign montos
   * Equivalent to pAsignaMontos procedure in PKG_AJUSTE_RESERVA
   * @param coberturaFormGroup The cobertura form group
   */
  asignaMontos(coberturaFormGroup: FormGroup): void {
    coberturaFormGroup.patchValue({
      sicc_mt_reserva_nvo: coberturaFormGroup.get('sicc_mt_reserva').value,
      sicc_mt_sumaseg_nvo: coberturaFormGroup.get('sicc_mt_sumaseg').value,
      sicc_mt_ajustado_nvo: coberturaFormGroup.get('sicc_mt_ajustado').value,
      mt_saldo_nvo: coberturaFormGroup.get('mt_saldo').value,
      sicc_mt_liquidacion_nvo: coberturaFormGroup.get('sicc_mt_liquidacion').value,
      marca_val: 'S'
    });
  }

  /**
   * Clean montos
   * Equivalent to pLimpiaMontos procedure in PKG_AJUSTE_RESERVA
   * @param coberturaFormGroup The cobertura form group
   */
  limpiaMontos(coberturaFormGroup: FormGroup): void {
    coberturaFormGroup.patchValue({
      ajuste_reserva: null,
      sicc_mt_ajustado_nvo: null,
      mt_saldo_nvo: null,
      msj_valida: null,
      sicc_mt_ajustado_mov: null,
      marca_val: null
    });
  }

  /**
   * Assign marca
   * Equivalent to pAsignaMarca procedure in PKG_AJUSTE_RESERVA
   */
  asignaMarca(): void {
    this.coberturas.controls.forEach(cobertura => {
      const coberturaGroup = cobertura as FormGroup;
      const siccMtAjustadoMov = coberturaGroup.get('sicc_mt_ajustado_mov').value;
      const prioridad = coberturaGroup.get('prioridad').value;

      let marcaVal = 'S';

      if (siccMtAjustadoMov !== null && prioridad !== null) {
        marcaVal = 'P';
      } else if (siccMtAjustadoMov !== null && prioridad === null) {
        marcaVal = 'N';
      }

      coberturaGroup.get('marca_val').setValue(marcaVal);
    });
  }

  /**
   * Handle validation of MT_PRIORIDAD
   * Equivalent to WHEN-BUTTON-PRESSED trigger on CALCULA_MT_PRIORIDAD
   */
  onCalculaMtPrioridad(): void {
    // Reset global variable with pending adjustment amount for priority
    this.globalSAPX = this.globalSAP;

    // Validate sum and priority
    this.asignaMarca();

    // Call priority adjustment procedure
    this.ajustePrioridadR();
  }

  /**
   * Priority adjustment procedure
   * Equivalent to P_Ajuste_Prioridad_R procedure in PKG_AJUSTE_RESERVA
   */
  ajustePrioridadR(): void {
    this.loading = true;

    this.sinfService.ajustePrioridadR(
      this.poliza.sice_cace_casu_cd_sucursal,
      this.poliza.sice_cace_carp_cd_ramo,
      this.poliza.sice_cace_capo_nu_poliza,
      this.poliza.sice_cace_nu_certificado,
      this.siniestro.sisi_fe_ocurrencia
    ).pipe(
      takeUntil(this.destroy$),
      finalize(() => this.loading = false),
      catchError(error => {
        this.handleError('Error al ajustar prioridad', error);
        return of(null);
      })
    ).subscribe(response => {
      if (response) {
        // Update coberturas with new values
        this.updateCoberturasAfterPriorityAdjustment(response);
      }
    });
  }

  /**
   * Update coberturas after priority adjustment
   * @param response The response from the priority adjustment service
   */
  updateCoberturasAfterPriorityAdjustment(response: any): void {
    this.coberturas.controls.forEach((c, index) => {
      const coberturaGroup = c as FormGroup;
      const updatedCobertura = response.coberturas.find(uc =>
        coberturaGroup.get('carb_cd_ramo').value === uc.carb_cd_ramo &&
        coberturaGroup.get('cacb_cd_cobertura').value === uc.cacb_cd_cobertura
      );

      if (updatedCobertura) {
        coberturaGroup.patchValue({
          msj_valida: updatedCobertura.msj_valida,
          marca_val: updatedCobertura.marca_val
        });
        this.coberturas.updateValueAndValidity();
      }
    });
  }

  /**
   * Handle navigation to next item
   * Equivalent to WHEN-BUTTON-PRESSED trigger on AGREGARFILA
   */
  onAgregarFila(): void {
    // If ramo is 13, calculate priority
    if (this.poliza.sice_cace_carp_cd_ramo === 13) {
      this.onCalculaMtPrioridad();
    }

    // Clear block and populate with data
    this.loadReservaCoberturaCert();
  }

  /**
   * Load reserva cobertura cert data
   * Equivalent to the logic in AGREGARFILA button
   */
  loadReservaCoberturaCert(): void {
    // Clear existing form array
    this.clearFormArray(this.reservaCoberturaCert);

    // Add items from coberturas that are marked for processing
    this.coberturas.controls.forEach(cobertura => {
      const coberturaGroup = cobertura as FormGroup;
      const marcaVal = coberturaGroup.get('marca_val').value;

      if (marcaVal === 'K' || marcaVal === 'N') {
        this.reservaCoberturaCert.push(this.createReservaFormGroup(coberturaGroup));
      }
    });
  }

  /**
   * Create form group for reserva cobertura cert
   * @param coberturaGroup The cobertura form group
   */
  createReservaFormGroup(coberturaGroup: FormGroup): FormGroup {
    return this.fb.group({
      sicc_sico_cacb_carb_cd_ramo: [coberturaGroup.get('carb_cd_ramo').value],
      dsp_carb_cd_ramo: [coberturaGroup.get('carb_de_ramo').value],
      sicc_sico_cacb_cd_cobertura: [coberturaGroup.get('cacb_cd_cobertura').value],
      dsp_cacb_de_cobertura: [coberturaGroup.get('cacb_de_cobertura').value],
      sicc_mt_sumaseg: [coberturaGroup.get('sicc_mt_sumaseg').value],
      sicc_mt_reserva: [coberturaGroup.get('sicc_mt_reserva').value],
      sicc_mt_ajustado: [coberturaGroup.get('sicc_mt_ajustado').value],
      sicc_mt_ajustado_mov: [coberturaGroup.get('sicc_mt_ajustado_mov').value],
      sicc_mt_liquidacion: [coberturaGroup.get('sicc_mt_liquidacion').value],
      sicc_mt_rechazo: [coberturaGroup.get('sicc_mt_rechazo').value],
      nsaldo: [coberturaGroup.get('mt_saldo_nvo').value],
      cob_prioridad: [coberturaGroup.get('prioridad').value]
    });
  }

  /**
   * Delete a row from reserva cobertura cert
   * Equivalent to WHEN-BUTTON-PRESSED trigger on ELIMINAFILA
   * @param index Index of the row to delete
   */
  onDeleteReservaRow(index: number): void {
    const reservaFormArray = this.reservaCoberturaCert;
    const reservaGroup = reservaFormArray.at(index) as FormGroup;

    const prioridad = reservaGroup.get('cob_prioridad').value;
    const ramoContable = reservaGroup.get('sicc_sico_cacb_carb_cd_ramo').value;
    const cobertura = reservaGroup.get('sicc_sico_cacb_cd_cobertura').value;

    this.deleteRemesa(
      this.poliza.sice_cace_casu_cd_sucursal,
      this.poliza.sice_cace_carp_cd_ramo,
      this.poliza.sice_cace_capo_nu_poliza,
      this.poliza.sice_cace_nu_certificado,
      ramoContable,
      cobertura,
      index
    );
  }

  /**
   * Helper method to delete a remesa
   * @param sucursal The sucursal
   * @param ramo The ramo
   * @param poliza The poliza
   * @param certificado The certificado
   * @param ramoContable The ramo contable
   * @param cobertura The cobertura
   * @param index The index of the row to delete
   */
  private deleteRemesa(sucursal: number, ramo: number, poliza: number, certificado: number, ramoContable: number, cobertura: string, index: number): void {
    this.sinfService.deleteRemesa(
      sucursal,
      ramo,
      poliza,
      certificado,
      ramoContable,
      cobertura
    ).pipe(
      takeUntil(this.destroy$),
      catchError(error => {
        this.handleError('Error al eliminar remesa', error);
        return of(null);
      })
    ).subscribe(() => {
      this.reservaCoberturaCert.removeAt(index);
      this.reservaCoberturaCert.markAsDirty();
    });
  }

  /**
   * Check if the record is the last one in the array
   * @param index Index of the record
   * @param formArray The form array
   */
  isLastRecord(index: number, formArray: FormArray): boolean {
    return index === formArray.length - 1;
  }

  /**
   * Handle error display
   * @param message Error message
   * @param error Error object
   */
  handleError(message: string, error: any): void {
    console.error(error);
    this.errorMessage = `${message}: ${error.message || error}`;

    this.snackBar.open(this.errorMessage, 'Cerrar', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }

  /**
   * Show success message
   * @param message Success message
   */
  showSuccessMessage(message: string): void {
    this.successMessage = message;

    this.snackBar.open(this.successMessage, 'Cerrar', {
      duration: 5000,
      panelClass: ['success-snackbar']
    });
  }

  /**
   * Show error message using the AlertDialogComponent
   * @param message The error message to display
   */
  showErrorMessage(message: string): void {
    this.dialog.open(AlertDialogComponent, {
      width: '400px',
      data: {
        title: 'Error',
        message: message
      }
    });
  }

  /**
   * Show a confirmation message and then navigate to a specified route
   * @param route The route to navigate to
   * @param message The confirmation message to display
   */
  showConfirmationAndNavigate(route: string, message: string): void {
    this.snackBar.open(message, 'Cerrar', {
      duration: 3000
    }).afterDismissed().subscribe(() => {
      this.router.navigate([route]);
    });
  }

  /**
   * Navigate back
   * Equivalent to EXIT_FORM button
   */
  onExit(): void {
    this.router.navigate(['/siniestros']);
  }
}