package com.yourcompany.yourapp.controller;

import com.yourcompany.yourapp.domain.*;
import com.yourcompany.yourapp.dto.*;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.service.*;
import com.yourcompany.yourapp.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing siniestro (claim) operations.
 * This controller handles the reservation adjustments for claims.
 * Converted from Oracle Forms module: SINF50104
 */
@RestController
@RequestMapping("/api/siniestros")
public class SiniestroController {
    
    private final Logger log = LoggerFactory.getLogger(SiniestroController.class);
    
    @Autowired
    private SiniestroService siniestroService;
    
    @Autowired
    private CoberturaService coberturaService;
    
    @Autowired
    private ReservaService reservaService;
    
    @Autowired
    private AjusteReservaService ajusteReservaService;
    
    @Autowired
    private PolizaService polizaService;
    
    @Autowired
    private ContabilidadService contabilidadService;
    
    @Autowired
    private UtileriasService utileriasService;
    
    /**
     * GET endpoint to retrieve claim information and coverage details.
     * Equivalent to the Oracle Forms pInfoSiniestro procedure.
     *
     * @param sucursal Branch code
     * @param ramo Branch line code
     * @param siniestro Claim number
     * @return ResponseEntity with claim and coverage information
     */
    @GetMapping("/info/{sucursal}/{ramo}/{siniestro}")
    public ResponseEntity<SiniestroInfoDTO> getSiniestroInfo(
            @PathVariable Integer sucursal,
            @PathVariable Integer ramo,
            @PathVariable Long siniestro) {
        
        log.debug("REST request to get Siniestro info: {}/{}/{}", sucursal, ramo, siniestro);
        
        try {
            // Get siniestro information
            SiniestroInfoDTO siniestroInfo = siniestroService.getSiniestroInfo(sucursal, ramo, siniestro);
            
            // Get poliza information
            PolizaDTO poliza = polizaService.getPolizaBySiniestro(sucursal, siniestro);
            siniestroInfo.setPoliza(poliza);
            
            // Get coverage information
            List<CoberturaDTO> coberturas = coberturaService.getCoberturasBySiniestro(sucursal, siniestro);
            siniestroInfo.setCoberturas(coberturas);
            
            // Calculate pending reservation amount for priority
            if (poliza != null) {
                BigDecimal pendingReservation = ajusteReservaService.calculatePendingReservation(
                    poliza.getSucursal(), 
                    poliza.getRamo(), 
                    poliza.getNumeroPoliza(), 
                    poliza.getNumeroCertificado(),
                    siniestroInfo.getFechaOcurrencia(),
                    10 // Default ramoContable value
                );
                siniestroInfo.setPendingReservation(pendingReservation);
            }
            
            return ResponseEntity.ok(siniestroInfo);
        } catch (Exception e) {
            log.error("Error retrieving siniestro info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST endpoint to validate coverage adjustments.
     * Equivalent to the Oracle Forms fAjusteReserva function.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param coberturaId Coverage ID to validate
     * @param ajusteRequest Adjustment request data
     * @return ResponseEntity with validation result
     */
    @PostMapping("/{sucursal}/{siniestro}/coberturas/{coberturaId}/validate")
    public ResponseEntity<ValidationResultDTO> validateAjusteReserva(
            @PathVariable Integer sucursal,
            @PathVariable Long siniestro,
            @PathVariable String coberturaId,
            @RequestBody AjusteReservaRequestDTO ajusteRequest) {
        
        log.debug("REST request to validate ajuste reserva: {}/{}/{}", sucursal, siniestro, coberturaId);
        
        try {
            ValidationResultDTO result = ajusteReservaService.validateAjusteReserva(
                sucursal, 
                siniestro, 
                ajusteRequest.getRamoContable(),
                coberturaId,
                ajusteRequest.getMontoAjuste(),
                ajusteRequest.isValidateOnly()
            );
            
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            log.error("Business error validating ajuste reserva", e);
            ValidationResultDTO result = new ValidationResultDTO();
            result.setValid(false);
            result.setMessage(e.getMessage());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error validating ajuste reserva", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST endpoint to apply coverage adjustments.
     * Equivalent to the Oracle Forms KEY-COMMIT trigger.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ajusteRequest Adjustment request data
     * @return ResponseEntity with operation result
     */
    @PostMapping("/{sucursal}/{siniestro}/ajuste-reserva")
    @Transactional
    public ResponseEntity<OperationResultDTO> applyAjusteReserva(
            @PathVariable Integer sucursal,
            @PathVariable Long siniestro,
            @RequestBody List<AjusteReservaRequestDTO> ajusteRequest) {
        
        log.debug("REST request to apply ajuste reserva: {}/{}", sucursal, siniestro);
        
        try {
            // Validate that there are adjustments to apply
            if (!ajusteReservaService.validateAjustes(ajusteRequest)) {
                return ResponseEntity.badRequest().body(
                    new OperationResultDTO(false, "Se debe validar los Ajustes, previamente")
                );
            }
            
            // Process each adjustment
            List<String> errors = new ArrayList<>();
            boolean success = true;
            
            for (AjusteReservaRequestDTO ajuste : ajusteRequest) {
                try {
                    boolean result = ajusteReservaService.generateAjusteReserva(
                        sucursal,
                        siniestro,
                        ajuste.getRamoContable(),
                        ajuste.getCobertura(),
                        ajuste.getRamoPoliza(),
                        ajuste.getPoliza(),
                        ajuste.getCertificado(),
                        ajuste.getMontoAjuste()
                    );
                    
                    if (!result) {
                        success = false;
                        errors.add("Error al procesar ajuste para cobertura: " + ajuste.getCobertura());
                    }
                } catch (Exception e) {
                    success = false;
                    errors.add("Error al procesar ajuste para cobertura " + ajuste.getCobertura() + ": " + e.getMessage());
                }
            }
            
            if (success) {
                String tipoMovimiento = utileriasService.getTipoMovimiento();
                String descTipoMovimiento = utileriasService.getDescripcionTipoMovimiento(tipoMovimiento);
                
                return ResponseEntity.ok(
                    new OperationResultDTO(true, "Se realizó el movimiento de " + descTipoMovimiento + " correctamente.")
                );
            } else {
                return ResponseEntity.ok(
                    new OperationResultDTO(false, "Ocurrieron errores al procesar los ajustes: " + String.join(", ", errors))
                );
            }
        } catch (Exception e) {
            log.error("Error applying ajuste reserva", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OperationResultDTO(false, "Error al procesar los ajustes: " + e.getMessage()));
        }
    }
    
    /**
     * POST endpoint to calculate priority for adjustments.
     * Equivalent to the Oracle Forms CALCULA_MT_PRIORIDAD button.
     *
     * @param sucursal Branch code
     * @param ramo Branch line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @return ResponseEntity with operation result
     */
    @PostMapping("/ajuste-prioridad")
    public ResponseEntity<OperationResultDTO> calculatePriority(
            @RequestParam Integer sucursal,
            @RequestParam Integer ramo,
            @RequestParam Long poliza,
            @RequestParam Integer certificado,
            @RequestParam LocalDate fechaOcurrencia) {
        
        log.debug("REST request to calculate priority: {}/{}/{}/{}", sucursal, ramo, poliza, certificado);
        
        try {
            String message = ajusteReservaService.calculatePriority(sucursal, ramo, poliza, certificado, fechaOcurrencia);
            
            return ResponseEntity.ok(new OperationResultDTO(true, message));
        } catch (BusinessException e) {
            log.error("Business error calculating priority", e);
            return ResponseEntity.ok(new OperationResultDTO(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error calculating priority", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OperationResultDTO(false, "Error al calcular prioridad: " + e.getMessage()));
        }
    }
    
    /**
     * POST endpoint to add a reservation coverage row.
     * Equivalent to the Oracle Forms AGREGARFILA button.
     *
     * @param sucursal Branch code
     * @param ramo Branch line code
     * @param siniestro Claim number
     * @return ResponseEntity with operation result
     */
    @PostMapping("/{sucursal}/{ramo}/{siniestro}/agregar-fila")
    public ResponseEntity<OperationResultDTO> agregarFila(
            @PathVariable Integer sucursal,
            @PathVariable Integer ramo,
            @PathVariable Long siniestro) {
        
        log.debug("REST request to add reservation row: {}/{}/{}", sucursal, ramo, siniestro);
        
        try {
            // For ramo 13, calculate priority first
            if (ramo == 13) {
                PolizaDTO poliza = polizaService.getPolizaBySiniestro(sucursal, siniestro);
                SiniestroDTO siniestroInfo = siniestroService.getSiniestroById(sucursal, siniestro);
                
                if (poliza != null && siniestroInfo != null) {
                    ajusteReservaService.calculatePriority(
                        poliza.getSucursal(),
                        poliza.getRamo(),
                        poliza.getNumeroPoliza(),
                        poliza.getNumeroCertificado(),
                        siniestroInfo.getFechaOcurrencia()
                    );
                }
            }
            
            // Clear and populate DT_SINT_RESERVA_COBERTURA_CERT block
            List<ReservaCoberturaDTO> reservaCoberturas = reservaService.getReservaCoberturasForSiniestro(sucursal, ramo, siniestro);
            
            return ResponseEntity.ok(new OperationResultDTO(true, "Filas agregadas correctamente"));
        } catch (BusinessException e) {
            log.error("Business error adding reservation row", e);
            return ResponseEntity.ok(new OperationResultDTO(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error adding reservation row", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OperationResultDTO(false, "Error al agregar fila: " + e.getMessage()));
        }
    }
    
    /**
     * DELETE endpoint to remove a reservation coverage row.
     * Equivalent to the Oracle Forms ELIMINAFILA button.
     *
     * @param sucursal Branch code
     * @param ramo Branch line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param ramoContable Accounting branch code
     * @param cobertura Coverage code
     * @return ResponseEntity with operation result
     */
    @DeleteMapping("/reserva-cobertura")
    public ResponseEntity<OperationResultDTO> deleteReservaCobertura(
            @RequestParam Integer sucursal,
            @RequestParam Integer ramo,
            @RequestParam Long poliza,
            @RequestParam Integer certificado,
            @RequestParam Integer ramoContable,
            @RequestParam String cobertura) {
        
        log.debug("REST request to delete reserva cobertura: {}/{}/{}/{}/{}/{}", 
            sucursal, ramo, poliza, certificado, ramoContable, cobertura);
        
        try {
            // Check if there's a priority value
            ReservaCoberturaDTO reservaCobertura = reservaService.getReservaCobertura(
                sucursal, ramo, poliza, certificado, ramoContable, cobertura);
            
            if (reservaCobertura != null && reservaCobertura.getPrioridad() != null && reservaCobertura.getPrioridad() > 0) {
                ajusteReservaService.deleteRemesa(sucursal, ramo, poliza, certificado, ramoContable, cobertura);
                return ResponseEntity.ok(new OperationResultDTO(true, "Registro eliminado correctamente"));
            } else if (reservaCobertura != null) {
                ajusteReservaService.deleteRemesa(sucursal, ramo, poliza, certificado, ramoContable, cobertura);
                return ResponseEntity.ok(new OperationResultDTO(true, "Registro eliminado correctamente"));
            } else {
                return ResponseEntity.ok(new OperationResultDTO(false, "No se encontró el registro a eliminar"));
            }
        } catch (Exception e) {
            log.error("Error deleting reserva cobertura", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OperationResultDTO(false, "Error al eliminar registro: " + e.getMessage()));
        }
    }
    
    /**
     * Helper method to get system information like current user and date.
     * Equivalent to the Oracle Forms P_InformaDatosToolbar procedure.
     *
     * @return Map with system information
     */
    @GetMapping("/system-info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();
        
        try {
            // Get database connection info
            String connectionInfo = utileriasService.getDatabaseConnectionInfo();
            systemInfo.put("connection", connectionInfo);
            
            // Get current date
            systemInfo.put("currentDate", LocalDateTime.now());
            
            // Get current user
            String currentUser = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
            systemInfo.put("currentUser", currentUser);
            
            return ResponseEntity.ok(systemInfo);
        } catch (Exception e) {
            log.error("Error retrieving system info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET endpoint to retrieve coverage list for LOV.
     * Equivalent to the Oracle Forms COBERTURA LOV.
     *
     * @return ResponseEntity with list of coverages
     */
    @GetMapping("/coberturas")
    public ResponseEntity<List<CoberturaLOVDTO>> getCoberturas() {
        try {
            List<CoberturaLOVDTO> coberturas = coberturaService.getAllCoberturas();
            return ResponseEntity.ok(coberturas);
        } catch (Exception e) {
            log.error("Error retrieving coberturas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Additional helper methods

    /**
     * Helper method to handle errors.
     * Equivalent to the Oracle Forms ON-ERROR trigger.
     *
     * @param errorCode Error code
     * @param errorMessage Error message
     * @return Error information
     */
    private Map<String, Object> handleError(String errorCode, String errorMessage) {
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("errorCode", errorCode);
        errorInfo.put("errorMessage", errorMessage);
        
        // Handle specific Oracle error codes
        if ("03113".equals(errorCode)) {
            errorInfo.put("userMessage", "Se ha perdido la conexión con la base de datos.");
        } else if ("03114".equals(errorCode)) {
            errorInfo.put("userMessage", "Se han perdido sus credenciales inicie sesión nuevamente.");
        } else if ("302000".equals(errorCode)) {
            errorInfo.put("userMessage", "El archivo seleccionado no existe, seleccione uno existente.");
        } else if ("04068".equals(errorCode) || "04061".equals(errorCode)) {
            errorInfo.put("userMessage", "El paquete ha sufrido cambios, vuelva iniciar sesión nuevamente.");
        } else {
            errorInfo.put("userMessage", errorMessage);
        }
        
        return errorInfo;
    }
}