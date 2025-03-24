import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, Subscription } from 'rxjs';
import { tap, finalize, catchError } from 'rxjs/operators';

import { SiniestroService } from '../../services/siniestro.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog.component';
import { AlertDialogComponent } from '../../components/alert-dialog/alert-dialog.component';
import { Siniestro } from '../../models/siniestro.model';
import { Cobertura } from '../../models/cobertura.model';
import { ReservaCoberturaCert } from '../../models/reserva-cobertura-cert.model';

@Component({
  selector: 'app-siniestro',
  templateUrl: './siniestro.component.html',
  styleUrls: ['./siniestro.component.scss']
})
export class SiniestroComponent implements OnInit, OnDestroy {
  // Main form groups
  siniestroForm: FormGroup;
  polizaForm: FormGroup;
  
  // Data models
  siniestro: Siniestro;
  coberturas: Cobertura[] = [];
  reservasCoberturas: ReservaCoberturaCert[] = [];
  
  // UI control variables
  loading = false;
  isEditing = false;
  hasChanges = false;
  errorMessage: string = null;
  successMessage: string = null;
  
  // Form parameters from route
  sucursal: number;
  ramo: number;
  siniestroId: number;
  ramoContable: number;
  cobertura: string;
  
  // Global variables (equivalent to Oracle Forms globals)
  SAP: number = 0;  // Suma Asegurada Pendiente
  SAP_X: number = 0;
  remesaCarga: string = '77777';
  
  // Subscriptions
  private subscriptions = new Subscription();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private siniestroService: SiniestroService,
    private authService: AuthService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
    this.initForms();
    this.getRouteParams();
    this.loadSiniestroData();
  }

  ngOnDestroy(): void {
    // Clean up subscriptions to prevent memory leaks
    this.subscriptions.unsubscribe();
  }

  /**
   * Initialize reactive forms
   * Equivalent to Oracle Forms block initialization
   */
  initForms(): void {
    // Initialize siniestro form (equivalent to DB_SINIESTRO block)
    this.siniestroForm = this.fb.group({
      SISI_CASU_CD_SUCURSAL: [null],
      SISI_SICS_CARP_CD_RAMO: [null],
      SISI_NU_SINIESTRO: [null],
      SISI_FE_OCURRENCIA: [null],
      DSP_CACN_NM_APELLIDO_RAZON: [null],
      SISI_ST_SINIESTRO: [null],
      SISI_DST_SINIESTRO: [null]
    });

    // Initialize poliza form (equivalent to DB_POLIZA block)
    this.polizaForm = this.fb.group({
      SICE_CACE_CASU_CD_SUCURSAL: [null],
      SICE_CACE_CARP_CD_RAMO: [null],
      SICE_CACE_CAPO_NU_POLIZA: [null],
      SICE_CACE_NU_CERTIFICADO: [null],
      SICE_NU_BENEFICIARIO: [null]
    });

    // Initialize coberturas form array (equivalent to DT_COBERTURA block)
    this.coberturasFormArray = this.fb.array([]);

    // Initialize reservas coberturas form array (equivalent to DT_SINT_RESERVA_COBERTURA_CERT block)
    this.reservasCoberturasFormArray = this.fb.array([]);
  }

  /**
   * Get route parameters
   * Equivalent to Oracle Forms parameter passing
   */
  getRouteParams(): void {
    this.subscriptions.add(
      this.route.params.subscribe(params => {
        this.sucursal = +params['sucursal'] || null;
        this.ramo = +params['ramo'] || null;
        this.siniestroId = +params['siniestro'] || null;
        this.ramoContable = +params['ramoContable'] || null;
        this.cobertura = params['cobertura'] || null;
        
        // Set form values from parameters
        this.siniestroForm.patchValue({
          SISI_CASU_CD_SUCURSAL: this.sucursal,
          SISI_SICS_CARP_CD_RAMO: this.ramo,
          SISI_NU_SINIESTRO: this.siniestroId
        });
      })
    );
  }

  /**
   * Load siniestro data
   * Equivalent to Oracle Forms PKG_AJUSTE_RESERVA.pInfoSiniestro procedure
   */
  loadSiniestroData(): void {
    if (!this.sucursal || !this.ramo || !this.siniestroId) {
      this.showError('Parámetros incompletos para cargar el siniestro');
      return;
    }

    this.loading = true;
    
    this.subscriptions.add(
      this.siniestroService.getSiniestroInfo(this.sucursal, this.ramo, this.siniestroId)
        .pipe(
          tap(data => {
            // Set siniestro data
            this.siniestro = data.siniestro;
            
            // Update siniestro form
            this.siniestroForm.patchValue({
              SISI_FE_OCURRENCIA: data.siniestro.fechaOcurrencia,
              DSP_CACN_NM_APELLIDO_RAZON: data.siniestro.nombreAsegurado,
              SISI_ST_SINIESTRO: data.siniestro.estatusSiniestro,
              SISI_DST_SINIESTRO: data.siniestro.descripcionEstatus
            });
            
            // Update poliza form
            this.polizaForm.patchValue({
              SICE_CACE_CASU_CD_SUCURSAL: data.poliza.sucursal,
              SICE_CACE_CARP_CD_RAMO: data.poliza.ramo,
              SICE_CACE_CAPO_NU_POLIZA: data.poliza.poliza,
              SICE_CACE_NU_CERTIFICADO: data.poliza.certificado,
              SICE_NU_BENEFICIARIO: data.poliza.beneficiario
            });
            
            // Calculate SAP (Suma Asegurada Pendiente)
            this.SAP = data.sumaAseguradaPendiente || 0;
            this.SAP_X = this.SAP;
            
            // Load coberturas
            this.loadCoberturas(data.coberturas);
          }),
          finalize(() => {
            this.loading = false;
          }),
          catchError(error => {
            this.showError('Error al cargar información del siniestro: ' + error.message);
            this.loading = false;
            throw error;
          })
        )
        .subscribe()
    );
  }

  /**
   * Load coberturas data
   * Equivalent to Oracle Forms coberturas loading in PKG_AJUSTE_RESERVA.pInfoSiniestro
   */
  loadCoberturas(coberturas: Cobertura[]): void {
    this.coberturas = coberturas;
    
    // Clear existing form array
    while (this.coberturasFormArray.length) {
      this.coberturasFormArray.removeAt(0);
    }
    
    // Add each cobertura to form array
    coberturas.forEach(cobertura => {
      this.coberturasFormArray.push(this.createCoberturaFormGroup(cobertura));
    });
  }

  /**
   * Create form group for a cobertura
   * @param cobertura Cobertura data
   */
  createCoberturaFormGroup(cobertura: Cobertura): FormGroup {
    return this.fb.group({
      CARB_CD_RAMO: [cobertura.ramoContableCodigo],
      CARB_DE_RAMO: [cobertura.ramoContableDescripcion],
      CACB_CD_COBERTURA: [cobertura.coberturaCodigo],
      CACB_DE_COBERTURA: [cobertura.coberturaDescripcion],
      PRIORIDAD: [cobertura.prioridad],
      SICC_MT_SUMASEG: [cobertura.montoSumaAsegurada],
      SICC_MT_RESERVA: [cobertura.montoReserva],
      SICC_MT_AJUSTADO: [cobertura.montoAjustado],
      SICC_MT_LIQUIDACION: [cobertura.montoLiquidacion],
      MT_SALDO: [cobertura.montoSaldo],
      AJUSTE_RESERVA: [null, Validators.min(0)],
      MT_SALDO_NVO: [cobertura.montoSaldo],
      SICC_MT_AJUSTADO_NVO: [cobertura.montoAjustado],
      SICC_MT_AJUSTADO_MOV: [null],
      MARCA_VAL: [null],
      MSJ_VALIDA: [null],
      SICC_MT_RECHAZO: [cobertura.montoRechazo || 0]
    });
  }

  /**
   * Get coberturas form array
   */
  get coberturasFormArray(): FormArray {
    return this.fb.array([]);
  }

  /**
   * Get reservas coberturas form array
   */
  get reservasCoberturasFormArray(): FormArray {
    return this.fb.array([]);
  }

  /**
   * Validate ajuste reserva
   * Equivalent to Oracle Forms PKG_AJUSTE_RESERVA.fAjusteReserva function
   * @param coberturaIndex Index of cobertura in form array
   */
  validateAjusteReserva(coberturaIndex: number): boolean {
    const coberturaForm = this.coberturasFormArray.at(coberturaIndex) as FormGroup;
    const ajusteReserva = coberturaForm.get('AJUSTE_RESERVA').value;
    const montoSaldo = coberturaForm.get('MT_SALDO').value;
    const sumaAsegurada = coberturaForm.get('SICC_MT_SUMASEG').value;
    const estatusSiniestro = this.siniestroForm.get('SISI_ST_SINIESTRO').value;
    
    // Clear previous validation message
    coberturaForm.get('MSJ_VALIDA').setValue(null);
    
    // Validate for estatus 26
    if (estatusSiniestro === 26) {
      if (ajusteReserva < 0) {
        coberturaForm.get('MSJ_VALIDA').setValue('Error: No es permitido ingresar montos negativos en el ajuste.');
        return false;
      }
      
      if (ajusteReserva === 0) {
        const confirmed = this.confirmDialog('El Monto a ajustar es igual a cero, ¿desea continuar?');
        if (!confirmed) {
          return false;
        }
      }
      
      if (montoSaldo === ajusteReserva) {
        coberturaForm.get('MSJ_VALIDA').setValue(`Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: ${ajusteReserva} Saldo: ${montoSaldo}`);
        return false;
      }
    } else {
      // Other estatus validations
      if (ajusteReserva < 0) {
        coberturaForm.get('MSJ_VALIDA').setValue('Error: No es permitido ingresar montos negativos en el ajuste.');
        return false;
      }
      
      if (ajusteReserva === 0) {
        const confirmed = this.confirmDialog('El Monto a ajustar es igual a cero, ¿desea continuar?');
        if (!confirmed) {
          return false;
        }
      }
      
      if (montoSaldo === ajusteReserva) {
        coberturaForm.get('MSJ_VALIDA').setValue(`Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: ${ajusteReserva} Saldo: ${montoSaldo}`);
        return false;
      }
    }
    
    // Validate suma asegurada
    if (sumaAsegurada === 0) {
      coberturaForm.get('MSJ_VALIDA').setValue('Advertencia: Monto de suma asegurada es igual a cero');
      return false;
    }
    
    // Calculate new values
    const montoAjustadoNvo = coberturaForm.get('SICC_MT_AJUSTADO').value + (ajusteReserva - montoSaldo);
    const montoAjustadoMov = ajusteReserva - montoSaldo;
    const montoSaldoNvo = ajusteReserva;
    
    // Update form values
    coberturaForm.get('SICC_MT_AJUSTADO_NVO').setValue(montoAjustadoNvo);
    coberturaForm.get('SICC_MT_AJUSTADO_MOV').setValue(montoAjustadoMov);
    coberturaForm.get('MT_SALDO_NVO').setValue(montoSaldoNvo);
    
    return true;
  }

  /**
   * Calculate prioridad
   * Equivalent to Oracle Forms PKG_AJUSTE_RESERVA.P_Ajuste_Prioridad_R procedure
   */
  calculatePrioridad(): void {
    this.loading = true;
    
    // Reset SAP_X to SAP
    this.SAP_X = this.SAP;
    
    this.subscriptions.add(
      this.siniestroService.calculatePrioridad(
        this.polizaForm.get('SICE_CACE_CASU_CD_SUCURSAL').value,
        this.polizaForm.get('SICE_CACE_CARP_CD_RAMO').value,
        this.polizaForm.get('SICE_CACE_CAPO_NU_POLIZA').value,
        this.polizaForm.get('SICE_CACE_NU_CERTIFICADO').value,
        this.siniestroForm.get('SISI_FE_OCURRENCIA').value
      ).pipe(
        tap(result => {
          // Update coberturas with validation results
          result.coberturas.forEach((cobertura, index) => {
            const coberturaForm = this.coberturasFormArray.at(index) as FormGroup;
            
            if (cobertura.mensajeValidacion) {
              coberturaForm.get('MSJ_VALIDA').setValue(cobertura.mensajeValidacion);
              coberturaForm.get('MARCA_VAL').setValue('S');
            }
          });
        }),
        finalize(() => {
          this.loading = false;
        }),
        catchError(error => {
          this.showError('Error al calcular prioridad: ' + error.message);
          this.loading = false;
          throw error;
        })
      ).subscribe()
    );
  }

  /**
   * Assign marca
   * Equivalent to Oracle Forms PKG_AJUSTE_RESERVA.pAsignaMarca procedure
   */
  assignMarca(): void {
    for (let i = 0; i < this.coberturasFormArray.length; i++) {
      const coberturaForm = this.coberturasFormArray.at(i) as FormGroup;
      
      if (coberturaForm.get('SICC_MT_AJUSTADO_MOV').value !== null && 
          coberturaForm.get('PRIORIDAD').value !== null) {
        coberturaForm.get('MARCA_VAL').setValue('P');
      } else if (coberturaForm.get('SICC_MT_AJUSTADO_MOV').value !== null && 
                coberturaForm.get('PRIORIDAD').value === null) {
        coberturaForm.get('MARCA_VAL').setValue('N');
      } else {
        coberturaForm.get('MARCA_VAL').setValue('S');
      }
    }
  }

  /**
   * Assign montos
   * Equivalent to Oracle Forms PKG_AJUSTE_RESERVA.pAsignaMontos procedure
   * @param coberturaIndex Index of cobertura in form array
   */
  assignMontos(coberturaIndex: number): void {
    const coberturaForm = this.coberturasFormArray.at(coberturaIndex) as FormGroup;
    
    coberturaForm.get('SICC_MT_RESERVA_NVO').setValue(coberturaForm.get('SICC_MT_RESERVA').value);
    coberturaForm.get('SICC_MT_SUMASEG_NVO').setValue(coberturaForm.get('SICC_MT_SUMASEG').value);
    coberturaForm.get('SICC_MT_AJUSTADO_NVO').setValue(coberturaForm.get('SICC_MT_AJUSTADO').value);
    coberturaForm.get('MT_SALDO_NVO').setValue(coberturaForm.get('MT_SALDO').value);
    coberturaForm.get('SICC_MT_LIQUIDACION_NVO').setValue(coberturaForm.get('SICC_MT_LIQUIDACION').value);
    coberturaForm.get('MARCA_VAL').setValue('S');
  }

  /**
   * Clean montos
   * Equivalent to Oracle Forms PKG_AJUSTE_RESERVA.pLimpiaMontos procedure
   * @param coberturaIndex Index of cobertura in form array
   */
  cleanMontos(coberturaIndex: number): void {
    const coberturaForm = this.coberturasFormArray.at(coberturaIndex) as FormGroup;
    
    coberturaForm.get('AJUSTE_RESERVA').setValue(null);
    coberturaForm.get('SICC_MT_AJUSTADO_NVO').setValue(null);
    coberturaForm.get('MT_SALDO_NVO').setValue(null);
    coberturaForm.get('MSJ_VALIDA').setValue(null);
    coberturaForm.get('SICC_MT_AJUSTADO_MOV').setValue(null);
    coberturaForm.get('MARCA_VAL').setValue(null);
  }

  /**
   * Add row to reservas coberturas
   * Equivalent to Oracle Forms AGREGARFILA button
   */
  addReservaCobertura(): void {
    // First validate all coberturas
    if (this.polizaForm.get('SICE_CACE_CARP_CD_RAMO').value === 13) {
      this.calculatePrioridad();
    }
    
    // Clear reservas coberturas form array
    while (this.reservasCoberturasFormArray.length) {
      this.reservasCoberturasFormArray.removeAt(0);
    }
    
    // Add validated coberturas to reservas
    let validCount = 0;
    
    for (let i = 0; i < this.coberturasFormArray.length; i++) {
      const coberturaForm = this.coberturasFormArray.at(i) as FormGroup;
      
      if (['K', 'N'].includes(coberturaForm.get('MARCA_VAL').value)) {
        validCount++;
        
        // Add to reservas coberturas
        this.reservasCoberturasFormArray.push(this.createReservaCoberturaCertFormGroup(coberturaForm));
      }
    }
    
    if (validCount === 0) {
      this.showError('Se debe validar los Ajustes, previamente');
    }
    
    // Clean montos for all coberturas
    for (let i = 0; i < this.coberturasFormArray.length; i++) {
      this.cleanMontos(i);
    }
  }

  /**
   * Create form group for reserva cobertura cert
   * @param coberturaForm Source cobertura form group
   */
  createReservaCoberturaCertFormGroup(coberturaForm: FormGroup): FormGroup {
    return this.fb.group({
      SICC_SICO_CACB_CARB_CD_RAMO: [coberturaForm.get('CARB_CD_RAMO').value],
      DSP_CARB_CD_RAMO: [coberturaForm.get('CARB_DE_RAMO').value],
      SICC_SICO_CACB_CD_COBERTURA: [coberturaForm.get('CACB_CD_COBERTURA').value],
      DSP_CACB_DE_COBERTURA: [coberturaForm.get('CACB_DE_COBERTURA').value],
      SICC_MT_SUMASEG: [coberturaForm.get('SICC_MT_SUMASEG').value],
      SICC_MT_RESERVA: [coberturaForm.get('SICC_MT_RESERVA').value],
      SICC_MT_AJUSTADO: [coberturaForm.get('SICC_MT_AJUSTADO').value],
      SICC_MT_LIQUIDACION: [coberturaForm.get('SICC_MT_LIQUIDACION').value],
      NSALDO: [coberturaForm.get('MT_SALDO').value],
      COB_PRIORIDAD: [coberturaForm.get('PRIORIDAD').value]
    });
  }

  /**
   * Delete row from reservas coberturas
   * Equivalent to Oracle Forms ELIMINAFILA button
   * @param index Index of reserva cobertura to delete
   */
  deleteReservaCobertura(index: number): void {
    const reservaForm = this.reservasCoberturasFormArray.at(index) as FormGroup;
    
    if (reservaForm.get('COB_PRIORIDAD').value > 0) {
      this.siniestroService.deleteRemesa(
        this.polizaForm.get('SICE_CACE_CASU_CD_SUCURSAL').value,
        this.polizaForm.get('SICE_CACE_CARP_CD_RAMO').value,
        this.polizaForm.get('SICE_CACE_CAPO_NU_POLIZA').value,
        this.polizaForm.get('SICE_CACE_NU_CERTIFICADO').value,
        reservaForm.get('SICC_SICO_CACB_CARB_CD_RAMO').value,
        reservaForm.get('SICC_SICO_CACB_CD_COBERTURA').value
      ).subscribe();
    }
    
    this.reservasCoberturasFormArray.removeAt(index);
  }

  /**
   * Save changes
   * Equivalent to Oracle Forms KEY-COMMIT trigger
   */
  saveChanges(): void {
    // Validate if there are reservas coberturas to save
    if (this.reservasCoberturasFormArray.length === 0) {
      this.showError('No hay ajustes de reserva por aplicar');
      return;
    }
    
    this.loading = true;
    let hasErrors = false;
    
    // Process each reserva cobertura
    const reservasToProcess = this.reservasCoberturasFormArray.value;
    
    this.subscriptions.add(
      this.siniestroService.processReservasCoberturas(
        this.sucursal,
        this.siniestroId,
        this.ramo,
        reservasToProcess
      ).pipe(
        tap(result => {
          if (result.success) {
            this.showSuccess('Se realizó el movimiento de Ajuste de Reserva correctamente');
            // Navigate back or reload data
            this.router.navigate(['/siniestros']);
          } else {
            this.showError(result.errorMessage || 'Error al procesar los ajustes de reserva');
            hasErrors = true;
          }
        }),
        finalize(() => {
          this.loading = false;
        }),
        catchError(error => {
          this.showError('Error al guardar los cambios: ' + error.message);
          this.loading = false;
          hasErrors = true;
          throw error;
        })
      ).subscribe()
    );
    
    // If no errors, commit changes
    if (!hasErrors) {
      // Clear form and navigate back
      this.router.navigate(['/siniestros']);
    }
  }

  /**
   * Cancel and return
   */
  cancel(): void {
    if (this.hasChanges) {
      const confirmed = this.confirmDialog('¿Está seguro que desea cancelar? Se perderán los cambios no guardados.');
      if (!confirmed) {
        return;
      }
    }
    
    this.router.navigate(['/siniestros']);
  }

  /**
   * Show error message
   * @param message Error message to display
   */
  showError(message: string): void {
    this.errorMessage = message;
    this.snackBar.open(message, 'Cerrar', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }

  /**
   * Show success message
   * @param message Success message to display
   */
  showSuccess(message: string): void {
    this.successMessage = message;
    this.snackBar.open(message, 'Cerrar', {
      duration: 5000,
      panelClass: ['success-snackbar']
    });
  }

  /**
   * Show confirmation dialog
   * @param message Confirmation message
   * @returns Boolean indicating if confirmed
   */
  confirmDialog(message: string): boolean {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '350px',
      data: { message }
    });
    
    let result = false;
    dialogRef.afterClosed().subscribe(confirmed => {
      result = confirmed;
    });
    
    return result;
  }

  /**
   * Show alert dialog
   * @param message Alert message
   * @param title Alert title
   */
  showAlert(message: string, title: string): void {
    this.dialog.open(AlertDialogComponent, {
      width: '350px',
      data: { message, title }
    });
  }
}