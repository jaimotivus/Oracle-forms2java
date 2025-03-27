package com.example.application.controller;

import com.example.application.dto.AjusteReservaDTO;
import com.example.application.dto.CoberturaDTO;
import com.example.application.dto.ErrorResponseDTO;
import com.example.application.dto.ReservaResponseDTO;
import com.example.application.dto.SiniestroDTO;
import com.example.application.exception.CustomNotFoundException;
import com.example.application.service.SinfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for managing insurance claim reserves (SINF module).
 * This controller provides endpoints for retrieving and updating claim reserves,
 * validating adjustments, and calculating priorities.
 */
@RestController
@RequestMapping("/api/v1/sinf") // Added versioning
@RequiredArgsConstructor
@Slf4j
@Validated // Enable validation on controller level
@Tag(name = "Siniestros", description = "API para gestión de reservas de siniestros")
public class SinfController {

    private final SinfService sinfService;

    /**
     * Retrieves information about a specific insurance claim.
     *
     * @param sucursal  Branch code
     * @param ramo      Insurance line code
     * @param siniestro Claim number
     * @return Claim information with associated coverages
     */
    @GetMapping("/siniestro/{sucursal}/{ramo}/{siniestro}")
    @Operation(summary = "Obtener información de siniestro", description = "Recupera la información de un siniestro específico con sus coberturas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Siniestro encontrado",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = SiniestroDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Siniestro no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public ResponseEntity<SiniestroDTO> getSiniestroInfo(
            @Parameter(description = "Código de sucursal") @PathVariable @NotNull @Min(1) Integer sucursal,
            @Parameter(description = "Código de ramo") @PathVariable @NotNull @Min(1) Integer ramo,
            @Parameter(description = "Número de siniestro") @PathVariable @NotNull @Min(1) Long siniestro) {

        log.info("Obteniendo información del siniestro: {}/{}/{}", sucursal, ramo, siniestro);
        SiniestroDTO result = sinfService.getSiniestroInfo(sucursal, ramo, siniestro);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves the list of coverages for a specific claim.
     *
     * @param sucursal  Branch code
     * @param ramo      Insurance line code
     * @param siniestro Claim number
     * @return List of coverages associated with the claim
     */
    @GetMapping("/coberturas/{sucursal}/{ramo}/{siniestro}")
    @Operation(summary = "Obtener coberturas de siniestro", description = "Recupera las coberturas asociadas a un siniestro")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Coberturas encontradas"),
            @ApiResponse(responseCode = "404", description = "Siniestro no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public ResponseEntity<List<CoberturaDTO>> getCoberturasSiniestro(
            @Parameter(description = "Código de sucursal") @PathVariable @NotNull @Min(1) Integer sucursal,
            @Parameter(description = "Código de ramo") @PathVariable @NotNull @Min(1) Integer ramo,
            @Parameter(description = "Número de siniestro") @PathVariable @NotNull @Min(1) Long siniestro) {

        log.info("Obteniendo coberturas del siniestro: {}/{}/{}", sucursal, ramo, siniestro);
        List<CoberturaDTO> coberturas = sinfService.getCoberturasSiniestro(sucursal, ramo, siniestro);
        return ResponseEntity.ok(coberturas);
    }

    /**
     * Validates the adjustment of a claim reserve.
     *
     * @param sucursal   Branch code
     * @param ramo       Insurance line code
     * @param siniestro  Claim number
     * @param ramoCont   Accounting line code
     * @param cobertura  Coverage code
     * @param ajusteDTO  Adjustment details
     * @return Validation result
     */
    @PostMapping("/validar-ajuste/{sucursal}/{ramo}/{siniestro}/{ramoCont}/{cobertura}")
    @Operation(summary = "Validar ajuste de reserva", description = "Valida si un ajuste de reserva es válido según las reglas de negocio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ajuste validado correctamente",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ReservaResponseDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Ajuste inválido",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Siniestro o cobertura no encontrados",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    public ResponseEntity<?> validarAjusteReserva( // Using wildcard to handle different response bodies
            @Parameter(description = "Código de sucursal") @PathVariable @NotNull @Min(1) Integer sucursal,
            @Parameter(description = "Código de ramo") @PathVariable @NotNull @Min(1) Integer ramo,
            @Parameter(description = "Número de siniestro") @PathVariable @NotNull @Min(1) Long siniestro,
            @Parameter(description = "Código de ramo contable") @PathVariable @NotNull @Min(1) Integer ramoCont,
            @Parameter(description = "Código de cobertura") @PathVariable @NotNull String cobertura,
            @Valid @RequestBody AjusteReservaDTO ajusteDTO) {

        log.info("Validando ajuste de reserva para siniestro: {}/{}/{}, cobertura: {}, monto: {}",
                sucursal, ramo, siniestro, cobertura, ajusteDTO.getMontoAjuste());

        ReservaResponseDTO result = sinfService.validarAjusteReserva(
                sucursal, ramo, siniestro, ramoCont, cobertura, ajusteDTO);

        if (!result.isValido()) {
            log.warn("Ajuste de reserva inválido: {}", result.getMensaje());
            ErrorResponseDTO errorResponse = new ErrorResponseDTO("Ajuste inválido", result.getMensaje());
            return ResponseEntity.badRequest().body(errorResponse);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Calculates priorities for claim reserves based on coverage priorities.
     *
     * @param sucursal      Branch code
     * @param ramo          Insurance line code
     * @param poliza        Policy number
     * @param certificado   Certificate number
     * @param fechaOcurrencia Date of occurrence
     * @return Calculation result
     */
    @PostMapping("/calcular-prioridad")
    @Operation(summary = "Calcular prioridades de reserva", description = "Calcula las prioridades de reserva según las coberturas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Prioridades calculadas correctamente",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ReservaResponseDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    public ResponseEntity<?> calcularPrioridadReserva(
            @Parameter(description = "Código de sucursal") @RequestParam @NotNull @Min(1) Integer sucursal,
            @Parameter(description = "Código de ramo") @RequestParam @NotNull @Min(1) Integer ramo,
            @Parameter(description = "Número de póliza") @RequestParam @NotNull @Min(1) Long poliza,
            @Parameter(description = "Número de certificado") @RequestParam @NotNull @Min(1) Long certificado,
            @Parameter(description = "Fecha de ocurrencia") @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaOcurrencia) {

        log.info("Calculando prioridades para póliza: {}/{}/{}/{}", sucursal, ramo, poliza, certificado, fechaOcurrencia);
        ReservaResponseDTO result = sinfService.calcularPrioridadReserva(
                sucursal, ramo, poliza, certificado, fechaOcurrencia);

        return ResponseEntity.ok(result);
    }

    /**
     * Applies reserve adjustments for a claim.
     *
     * @param sucursal  Branch code
     * @param ramo      Insurance line code
     * @param siniestro Claim number
     * @param ajustes   List of adjustments to apply
     * @return Result of the operation
     */
    @PostMapping("/aplicar-ajustes/{sucursal}/{ramo}/{siniestro}")
    @Operation(summary = "Aplicar ajustes de reserva", description = "Aplica los ajustes de reserva a un siniestro")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ajustes aplicados correctamente",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ReservaResponseDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Ajustes inválidos",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Siniestro no encontrado",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    public ResponseEntity<?> aplicarAjustesReserva(
            @Parameter(description = "Código de sucursal") @PathVariable @NotNull @Min(1) Integer sucursal,
            @Parameter(description = "Código de ramo") @PathVariable @NotNull @Min(1) Integer ramo,
            @Parameter(description = "Número de siniestro") @PathVariable @NotNull @Min(1) Long siniestro,
            @Valid @RequestBody List<AjusteReservaDTO> ajustes) {

        log.info("Aplicando {} ajustes de reserva para siniestro: {}/{}/{}",
                ajustes.size(), sucursal, ramo, siniestro);

        ReservaResponseDTO result = sinfService.aplicarAjustesReserva(sucursal, ramo, siniestro, ajustes);

        if (!result.isValido()) {
            log.warn("Error al aplicar ajustes de reserva: {}", result.getMensaje());
            ErrorResponseDTO errorResponse = new ErrorResponseDTO("Ajustes inválidos", result.getMensaje());
            return ResponseEntity.badRequest().body(errorResponse);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Deletes a reserve adjustment for a specific coverage.
     *
     * @param sucursal    Branch code
     * @param ramo        Insurance line code
     * @param poliza      Policy number
     * @param certificado Certificate number
     * @param ramoCont    Accounting line code
     * @param cobertura   Coverage code
     * @return Result of the operation
     */
    @DeleteMapping("/remesa/{sucursal}/{ramo}/{poliza}/{certificado}/{ramoCont}/{cobertura}")
    @Operation(summary = "Eliminar ajuste de reserva", description = "Elimina un ajuste de reserva para una cobertura específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ajuste eliminado correctamente",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ReservaResponseDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Ajuste no encontrado",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    public ResponseEntity<?> eliminarRemesa(
            @Parameter(description = "Código de sucursal") @PathVariable @NotNull @Min(1) Integer sucursal,
            @Parameter(description = "Código de ramo") @PathVariable @NotNull @Min(1) Integer ramo,
            @Parameter(description = "Número de póliza") @PathVariable @NotNull @Min(1) Long poliza,
            @Parameter(description = "Número de certificado") @PathVariable @NotNull @Min(1) Long certificado,
            @Parameter(description = "Código de ramo contable") @PathVariable @NotNull @Min(1) Integer ramoCont,
            @Parameter(description = "Código de cobertura") @PathVariable @NotNull String cobertura) {

        log.info("Eliminando ajuste de reserva para póliza: {}/{}/{}/{}, cobertura: {}",
                sucursal, ramo, poliza, certificado, cobertura);

        ReservaResponseDTO result = sinfService.eliminarRemesa(
                sucursal, ramo, poliza, certificado, ramoCont, cobertura);

        if (!result.isValido()) {
            log.warn("Ajuste de reserva no encontrado: {}", result.getMensaje());
            ErrorResponseDTO errorResponse = new ErrorResponseDTO("Ajuste no encontrado", result.getMensaje());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Global exception handler for the SinfController.
     *
     * @param ex The exception that was thrown.
     * @return Error response with details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Error interno del servidor: ", ex);
        ErrorResponseDTO errorResponse = new ErrorResponseDTO("Error interno del servidor", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles MethodArgumentNotValidException for validation errors.
     *
     * @param ex The exception that was thrown.
     * @return Error response with details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Error de validación: ", ex);
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce("", (acc, error) -> acc + error + "; ");

        ErrorResponseDTO errorResponse = new ErrorResponseDTO("Error de validación", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles CustomNotFoundException.
     *
     * @param ex The exception that was thrown.
     * @return Error response with details.
     */
    @ExceptionHandler(CustomNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFoundException(CustomNotFoundException ex) {
        log.warn("Recurso no encontrado: ", ex);
        ErrorResponseDTO errorResponse = new ErrorResponseDTO("Recurso no encontrado", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
}