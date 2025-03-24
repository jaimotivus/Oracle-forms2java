package com.yourcompany.yourapp.service;

import com.yourcompany.yourapp.domain.*;
import com.yourcompany.yourapp.dto.*;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.repository.*;
import com.yourcompany.yourapp.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing insurance claims (siniestros) and their reserves.
 * This service handles the business logic for adjusting reserves for insurance claims,
 * including validation, calculation, and persistence of reserve adjustments.
 * 
 * Converted from Oracle Forms: SINF501041_fmb.xml
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiniestroService {

    private final SiniestroRepository siniestroRepository;
    private final PolizaRepository polizaRepository;
    private final CoberturaRepository coberturaRepository;
    private final ReservaCoberturaCertificadoRepository reservaCoberturaCertificadoRepository;
    private final MovimientoSiniestroRepository movimientoSiniestroRepository;
    private final MovimientoCoberturaRepository movimientoCoberturaRepository;
    private final MovimientoContableRepository movimientoContableRepository;
    private final RamoComponenteRepository ramoComponenteRepository;
    private final SintTmpMasivoRepository sintTmpMasivoRepository;
    private final UserContextService userContextService;
    private final AlertService alertService;

    /**
     * Retrieves claim information including policy details, coverages, and reserves.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param siniestro Claim number
     * @return ClaimDetailsDTO with all claim information
     */
    @Transactional(readOnly = true)
    public ClaimDetailsDTO getClaimInfo(Long sucursal, Long ramo, Long siniestro) {
        // Retrieve basic claim information
        Siniestro siniestroEntity = siniestroRepository.findBySucursalAndRamoAndNumero(sucursal, ramo, siniestro)
                .orElseThrow(() -> new BusinessException("Siniestro no encontrado"));
        
        // Retrieve policy information
        CertificadoSiniestro certSiniestro = siniestroEntity.getCertificadoSiniestro();
        Poliza poliza = polizaRepository.findBySucursalAndRamoAndNumeroAndCertificado(
                certSiniestro.getSucursal(),
                certSiniestro.getRamo(),
                certSiniestro.getPoliza(),
                certSiniestro.getCertificado())
                .orElseThrow(() -> new BusinessException("Póliza no encontrada"));
        
        // Calculate pending sum assured
        BigDecimal sumaPendiente = calculatePendingSumAssured(
                certSiniestro.getSucursal(),
                certSiniestro.getRamo(),
                certSiniestro.getPoliza(),
                certSiniestro.getCertificado(),
                siniestroEntity.getFechaOcurrencia(),
                10L); // Default ramoContable = 10
        
        // Build and return the DTO with all information
        return ClaimDetailsDTO.builder()
                .siniestro(siniestroEntity)
                .poliza(poliza)
                .coberturas(getCoveragesForClaim(siniestroEntity))
                .sumaPendiente(sumaPendiente)
                .build();
    }

    /**
     * Retrieves all coverages for a claim with their reserves and adjustments.
     * 
     * @param siniestro The claim entity
     * @return List of coverage DTOs with reserve information
     */
    private List<CoberturaSiniestroDTO> getCoveragesForClaim(Siniestro siniestro) {
        List<ReservaCoberturaCertificado> reservas = reservaCoberturaCertificadoRepository
                .findBySiniestro(siniestro.getId());
        
        return reservas.stream()
                .map(reserva -> {
                    Cobertura cobertura = coberturaRepository.findById(reserva.getCobertura().getId())
                            .orElseThrow(() -> new BusinessException("Cobertura no encontrada"));
                    
                    // Calculate balance
                    BigDecimal saldo = calculateBalance(
                            siniestro.getSucursal(),
                            siniestro.getNumero(),
                            reserva.getCobertura().getRamoContable(),
                            reserva.getCobertura().getCodigo());
                    
                    return CoberturaSiniestroDTO.builder()
                            .ramoContable(cobertura.getRamoContable())
                            .ramoContableDescripcion(cobertura.getRamoContableDescripcion())
                            .codigoCobertura(cobertura.getCodigo())
                            .descripcionCobertura(cobertura.getDescripcion())
                            .prioridad(reserva.getPrioridad())
                            .sumaAsegurada(reserva.getSumaAsegurada())
                            .reservaInicial(reserva.getReserva())
                            .ajustes(reserva.getAjustado())
                            .liquidacion(reserva.getLiquidacion())
                            .rechazo(reserva.getRechazo())
                            .saldo(saldo)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Validates and processes reserve adjustments for a claim.
     * 
     * @param request The reserve adjustment request containing claim and adjustment details
     * @return Result of the adjustment operation
     */
    @Transactional
    public ReserveAdjustmentResultDTO processReserveAdjustments(ReserveAdjustmentRequestDTO request) {
        // Validate that there are adjustments to process
        if (request.getAdjustments() == null || request.getAdjustments().isEmpty()) {
            throw new BusinessException("No existen ajustes de reservar por aplicar.");
        }
        
        ReserveAdjustmentResultDTO result = new ReserveAdjustmentResultDTO();
        List<String> messages = new ArrayList<>();
        
        // Process each adjustment
        for (ReserveAdjustmentDTO adjustment : request.getAdjustments()) {
            try {
                processReserveAdjustment(
                    request.getSucursal(),
                    request.getSiniestro(),
                    request.getRamo(),
                    adjustment.getRamoContable(),
                    adjustment.getCobertura(),
                    adjustment.getAjusteReserva()
                );
                result.getSuccessfulAdjustments().add(adjustment);
            } catch (Exception e) {
                log.error("Error processing adjustment for coverage {}: {}", 
                    adjustment.getCobertura(), e.getMessage(), e);
                messages.add(String.format("Error en cobertura %s: %s", 
                    adjustment.getCobertura(), e.getMessage()));
                result.getFailedAdjustments().add(adjustment);
            }
        }
        
        result.setMessages(messages);
        result.setSuccess(result.getFailedAdjustments().isEmpty());
        
        return result;
    }

    /**
     * Processes a single reserve adjustment for a claim coverage.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramo Insurance line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param ajusteReserva Adjustment amount
     * @throws BusinessException If validation fails or processing encounters an error
     */
    private void processReserveAdjustment(Long sucursal, Long siniestro, Long ramo, 
                                         Long ramoContable, String cobertura, BigDecimal ajusteReserva) {
        // Get claim and policy information
        Siniestro siniestroEntity = siniestroRepository.findBySucursalAndRamoAndNumero(sucursal, ramo, siniestro)
                .orElseThrow(() -> new BusinessException("Siniestro no encontrado"));
        
        CertificadoSiniestro certSiniestro = siniestroEntity.getCertificadoSiniestro();
        
        // Validate adjustment
        validateAdjustment(siniestroEntity, ramoContable, cobertura, ajusteReserva);
        
        // Generate movement number
        Long movimientoNumero = generateMovementNumber(sucursal, siniestro);
        
        // Register adjustment
        registerReserveAdjustment(
            sucursal, 
            siniestro, 
            ramo, 
            ramoContable, 
            cobertura, 
            certSiniestro.getSucursal(),
            certSiniestro.getRamo(),
            certSiniestro.getPoliza(),
            certSiniestro.getCertificado(),
            ajusteReserva,
            movimientoNumero
        );
        
        // Update coverage amounts
        updateCoverageAmounts(
            sucursal, 
            siniestro, 
            ramoContable, 
            cobertura, 
            certSiniestro.getSucursal(),
            certSiniestro.getRamo(),
            certSiniestro.getPoliza(),
            certSiniestro.getCertificado(),
            ajusteReserva
        );
    }

    /**
     * Validates a reserve adjustment before processing.
     * 
     * @param siniestro Claim entity
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param ajusteReserva Adjustment amount
     * @throws BusinessException If validation fails
     */
    private void validateAdjustment(Siniestro siniestro, Long ramoContable, 
                                   String cobertura, BigDecimal ajusteReserva) {
        // Get coverage information
        ReservaCoberturaCertificado reserva = reservaCoberturaCertificadoRepository
            .findBySiniestroAndRamoContableAndCobertura(
                siniestro.getId(), ramoContable, cobertura)
            .orElseThrow(() -> new BusinessException("Cobertura no encontrada para el siniestro"));
        
        // Calculate current balance
        BigDecimal saldo = calculateBalance(
            siniestro.getSucursal(), 
            siniestro.getNumero(), 
            ramoContable, 
            cobertura);
        
        // Validate adjustment amount
        if (ajusteReserva == null) {
            throw new BusinessException("Debe indicar Monto Ajustado a la Cobertura");
        }
        
        if (ajusteReserva.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Error: No es permitido ingresar montos negativos en el ajuste.");
        }
        
        if (ajusteReserva.compareTo(saldo) == 0) {
            throw new BusinessException("Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: " 
                + ajusteReserva + " Saldo: " + saldo);
        }
        
        // For ARP claims (status 26)
        if (siniestro.getEstado() == 26) {
            // No additional validations needed
        } else {
            // Validate against sum assured
            if (reserva.getSumaAsegurada().compareTo(BigDecimal.ZERO) != 0) {
                // Check if coverage requires validation against sum assured
                boolean requiresValidation = coberturaRepository.checkIfCoverageRequiresValidation(
                    ramoContable, cobertura);
                
                if (requiresValidation && ajusteReserva.compareTo(reserva.getSumaAsegurada()) > 0) {
                    // Get maximum indemnification factor
                    int maxIndemnizationFactor = getMaxIndemnizationFactor(
                        siniestro.getCertificadoSiniestro().getRamo());
                    
                    if (ajusteReserva.compareTo(
                        reserva.getSumaAsegurada().multiply(BigDecimal.valueOf(maxIndemnizationFactor))) > 0) {
                        throw new BusinessException("Monto de la reserva debe ser menor o igual que la suma asegurada que es de " 
                            + reserva.getSumaAsegurada().multiply(BigDecimal.valueOf(maxIndemnizationFactor)));
                    }
                }
            } else {
                throw new BusinessException("Advertencia: Monto de suma asegurada es igual a cero");
            }
            
            // Special validation for ACP coverage 003
            if (ramoContable == 3 && "003".equals(cobertura)) {
                validateACPCoverage003(siniestro, reserva, ajusteReserva);
            }
            
            // Validate for LUC insurance lines
            if (isLUCInsuranceLine(siniestro.getCertificadoSiniestro().getRamo())) {
                validateLUCAdjustment(
                    siniestro.getSucursal(),
                    siniestro.getCertificadoSiniestro().getRamo(),
                    siniestro.getCertificadoSiniestro().getPoliza(),
                    siniestro.getCertificadoSiniestro().getCertificado(),
                    siniestro.getFechaOcurrencia(),
                    ramoContable,
                    cobertura,
                    ajusteReserva
                );
            }
            
            // Validate death cause for life insurance (ramo 13)
            if (siniestro.getCertificadoSiniestro().getRamo() == 13) {
                validateDeathCause(
                    siniestro.getSucursal(),
                    siniestro.getCertificadoSiniestro().getRamo(),
                    siniestro.getCertificadoSiniestro().getPoliza(),
                    siniestro.getCertificadoSiniestro().getCertificado(),
                    siniestro.getFechaOcurrencia(),
                    ramoContable,
                    cobertura,
                    ajusteReserva
                );
            }
        }
    }

    /**
     * Registers a reserve adjustment in the system.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramo Insurance line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param polizaSucursal Policy branch code
     * @param polizaRamo Policy insurance line code
     * @param polizaNumero Policy number
     * @param polizaCertificado Policy certificate number
     * @param ajusteReserva Adjustment amount
     * @param movimientoNumero Movement number
     */
    private void registerReserveAdjustment(Long sucursal, Long siniestro, Long ramo, Long ramoContable, 
                                          String cobertura, Long polizaSucursal, Long polizaRamo, 
                                          Long polizaNumero, Long polizaCertificado, 
                                          BigDecimal ajusteReserva, Long movimientoNumero) {
        // Get claim information
        Siniestro siniestroEntity = siniestroRepository.findBySucursalAndRamoAndNumero(sucursal, ramo, siniestro)
                .orElseThrow(() -> new BusinessException("Siniestro no encontrado"));
        
        // Get currency
        String moneda = getClaimCurrency(sucursal, siniestro);
        
        // Get movement type
        String tipoMovimiento = determineMovementType(siniestroEntity.getEstado());
        
        // Calculate adjustment amount
        BigDecimal saldo = calculateBalance(sucursal, siniestro, ramoContable, cobertura);
        BigDecimal montoAjuste = ajusteReserva.subtract(saldo);
        
        // Create movement record
        MovimientoSiniestro movimiento = new MovimientoSiniestro();
        movimiento.setSucursal(sucursal);
        movimiento.setSiniestro(siniestro);
        movimiento.setNumeroMovimiento(movimientoNumero);
        movimiento.setFechaMovimiento(LocalDate.now());
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setAnalista(userContextService.getCurrentUser());
        movimiento.setMonto(montoAjuste);
        movimiento.setMoneda(moneda);
        movimiento.setRamoContable(ramoContable);
        movimiento.setCobertura(cobertura);
        movimiento.setAvisoAceptado(siniestroEntity.getEstado() == 26 ? "CO" : null);
        
        movimientoSiniestroRepository.save(movimiento);
        
        // Create coverage movement record
        MovimientoCobertura movimientoCobertura = new MovimientoCobertura();
        movimientoCobertura.setSucursal(sucursal);
        movimientoCobertura.setSiniestro(siniestro);
        movimientoCobertura.setNumeroMovimiento(movimientoNumero);
        movimientoCobertura.setRamoContable(ramoContable);
        movimientoCobertura.setCobertura(cobertura);
        movimientoCobertura.setSucursalPoliza(polizaSucursal);
        movimientoCobertura.setRamoPoliza(polizaRamo);
        movimientoCobertura.setPoliza(polizaNumero);
        movimientoCobertura.setCertificado(polizaCertificado);
        movimientoCobertura.setTipoMovimiento(tipoMovimiento);
        movimientoCobertura.setFechaMovimiento(LocalDate.now());
        movimientoCobertura.setMonto(montoAjuste);
        movimientoCobertura.setMoneda(siniestroEntity.getEstado() == 26 ? "CO" : "01");
        
        movimientoCoberturaRepository.save(movimientoCobertura);
        
        // Create accounting entries
        createAccountingEntries(
            sucursal, 
            siniestro, 
            movimientoNumero, 
            tipoMovimiento, 
            ramoContable, 
            cobertura, 
            polizaNumero, 
            polizaCertificado, 
            montoAjuste, 
            polizaRamo
        );
    }

    /**
     * Updates coverage amounts after a reserve adjustment.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param polizaSucursal Policy branch code
     * @param polizaRamo Policy insurance line code
     * @param polizaNumero Policy number
     * @param polizaCertificado Policy certificate number
     * @param ajusteReserva New reserve amount
     */
    private void updateCoverageAmounts(Long sucursal, Long siniestro, Long ramoContable, 
                                      String cobertura, Long polizaSucursal, Long polizaRamo, 
                                      Long polizaNumero, Long polizaCertificado, BigDecimal ajusteReserva) {
        ReservaCoberturaCertificado reserva = reservaCoberturaCertificadoRepository
            .findBySucursalAndSiniestroAndRamoContableAndCoberturaAndPoliza(
                sucursal, siniestro, ramoContable, cobertura, 
                polizaSucursal, polizaRamo, polizaNumero, polizaCertificado)
            .orElseThrow(() -> new BusinessException("Reserva no encontrada"));
        
        // Calculate current balance
        BigDecimal saldo = calculateBalance(sucursal, siniestro, ramoContable, cobertura);
        
        // Calculate adjustment amount
        BigDecimal montoAjuste = ajusteReserva.subtract(saldo);
        
        // Update reserve
        reserva.setFechaEfectiva(getEffectiveDate(
            polizaSucursal, polizaRamo, polizaNumero, polizaCertificado, 
            ramoContable, cobertura, reserva.getSiniestro().getFechaOcurrencia()));
        
        reserva.setAjustado(reserva.getAjustado().add(montoAjuste));
        
        reservaCoberturaCertificadoRepository.save(reserva);
    }

    /**
     * Creates accounting entries for a reserve adjustment.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param movimientoNumero Movement number
     * @param tipoMovimiento Movement type
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param monto Amount
     * @param ramoPoliza Policy insurance line code
     */
    private void createAccountingEntries(Long sucursal, Long siniestro, Long movimientoNumero, 
                                        String tipoMovimiento, Long ramoContable, String cobertura, 
                                        Long poliza, Long certificado, BigDecimal monto, Long ramoPoliza) {
        // Get claim type
        String tipoSiniestro = determineClaimType(ramoPoliza, ramoContable, cobertura, "");
        
        // Get accounting entry number
        Long numeroAsiento = getNextAccountingEntryNumber(sucursal, siniestro);
        
        // Get accounting components
        List<RamoComponente> componentes = ramoComponenteRepository.findByCompaniaAndSucursalAndRamoPolizaAndRamoContableAndTipoMovimientoAndCobertura(
            1L, 1L, ramoPoliza, ramoContable, tipoMovimiento, cobertura, tipoSiniestro);
        
        int movimientoContable = 0;
        
        for (RamoComponente componente : componentes) {
            movimientoContable++;
            
            // Determine if this is a debit or credit entry
            boolean isDebit = (monto.compareTo(BigDecimal.ZERO) > 0 && componente.getPosNeg().equals("P")) ||
                             (monto.compareTo(BigDecimal.ZERO) < 0 && componente.getPosNeg().equals("N"));
            
            // Create accounting entry
            MovimientoContable movimiento = new MovimientoContable();
            movimiento.setSucursal(sucursal);
            movimiento.setSiniestro(siniestro);
            movimiento.setNumeroMovimiento(movimientoNumero);
            movimiento.setTipoMovimiento(tipoMovimiento);
            movimiento.setRamoContable(ramoContable);
            movimiento.setCobertura(cobertura);
            movimiento.setSucursalPoliza(sucursal);
            movimiento.setRamoPoliza(ramoPoliza);
            movimiento.setPoliza(poliza);
            movimiento.setCertificado(certificado);
            movimiento.setNumeroMovimientoContable(Long.valueOf(movimientoContable));
            movimiento.setCompania(1L);
            movimiento.setNumeroMayor(componente.getNumeroMayor());
            movimiento.setHaber(isDebit ? BigDecimal.ZERO : monto.abs());
            movimiento.setDebe(isDebit ? monto.abs() : BigDecimal.ZERO);
            movimiento.setFechaMovimiento(LocalDate.now());
            movimiento.setFechaContable(null);
            movimiento.setEstadoContable("N");
            movimiento.setNumeroAsiento(numeroAsiento);
            
            movimientoContableRepository.save(movimiento);
        }
    }

    /**
     * Calculates the balance for a coverage.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @return Current balance
     */
    private BigDecimal calculateBalance(Long sucursal, Long siniestro, Long ramoContable, String cobertura) {
        // Get total movements
        BigDecimal totalMovements = movimientoCoberturaRepository.sumMovementsByCoverageExcludingTypes(
            sucursal, siniestro, ramoContable, cobertura, 
            Arrays.asList("IC", "CC", "ID"), LocalDate.now());
        
        // Get total payments
        BigDecimal totalPayments = movimientoCoberturaRepository.sumPaymentsByCoverage(
            sucursal, siniestro, ramoContable, cobertura, 
            700L, 750L, LocalDate.now(), 3L);
        
        // Calculate balance
        return totalMovements.subtract(totalPayments.abs());
    }

    /**
     * Calculates the pending sum assured for a policy.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @return Pending sum assured
     */
    private BigDecimal calculatePendingSumAssured(Long sucursal, Long ramo, Long poliza, 
                                                 Long certificado, LocalDate fechaOcurrencia, 
                                                 Long ramoContable) {
        // TODO-BUSINESS-LOGIC: Implement calculation of pending sum assured
        // This should call the equivalent of PKG_SINT_LUC.fCalcula_Rva_Pendiente
        // The calculation should consider:
        // 1. The sum assured for the policy
        // 2. Any existing reserves
        // 3. Any payments made
        // 4. The specific rules for the insurance line
        
        return BigDecimal.ZERO; // Placeholder
    }

    /**
     * Generates a new movement number for a claim.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return New movement number
     */
    private Long generateMovementNumber(Long sucursal, Long siniestro) {
        // Get maximum movement number from both tables
        Long maxMovimientoSiniestro = movimientoSiniestroRepository.findMaxMovementNumber(sucursal, siniestro);
        Long maxMovimientoCobertura = movimientoCoberturaRepository.findMaxMovementNumber(sucursal, siniestro);
        
        // Return the maximum + 1, or 1 if no movements exist
        Long maxMovimiento = Math.max(
            maxMovimientoSiniestro != null ? maxMovimientoSiniestro : 0,
            maxMovimientoCobertura != null ? maxMovimientoCobertura : 0
        );
        
        return maxMovimiento + 1;
    }

    /**
     * Gets the next accounting entry number for a claim.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Next accounting entry number
     */
    private Long getNextAccountingEntryNumber(Long sucursal, Long siniestro) {
        Long maxAsiento = movimientoContableRepository.findMaxAccountingEntryNumber(sucursal, siniestro);
        return (maxAsiento != null ? maxAsiento : 0) + 1;
    }

    /**
     * Determines the movement type based on claim status.
     * 
     * @param estadoSiniestro Claim status
     * @return Movement type code
     */
    private String determineMovementType(Integer estadoSiniestro) {
        if (estadoSiniestro == 24) {
            return "RL"; // Liquidación de Reserva
        } else if (estadoSiniestro == 25) {
            return "RX"; // Rechazo de Reserva
        } else {
            return "RA"; // Ajuste de Reserva
        }
    }

    /**
     * Gets the currency for a claim.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Currency code
     */
    private String getClaimCurrency(Long sucursal, Long siniestro) {
        // TODO-BUSINESS-LOGIC: Implement currency retrieval
        // This should get the currency from the policy associated with the claim
        // The original code calls PKG_UTILERIAS.fMoneda
        
        return "01"; // Default currency
    }

    /**
     * Determines the claim type based on insurance line and coverage.
     * 
     * @param ramoPoliza Policy insurance line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param marca Mark
     * @return Claim type code
     */
    private String determineClaimType(Long ramoPoliza, Long ramoContable, String cobertura, String marca) {
        // TODO-BUSINESS-LOGIC: Implement claim type determination
        // This should determine the claim type based on the insurance line and coverage
        // The original code calls PKG_UTILERIAS.fTipoSiniestro
        
        return null; // Placeholder
    }

    /**
     * Gets the effective date for a coverage.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param fechaOcurrencia Occurrence date
     * @return Effective date
     */
    private LocalDate getEffectiveDate(Long sucursal, Long ramo, Long poliza, Long certificado, 
                                      Long ramoContable, String cobertura, LocalDate fechaOcurrencia) {
        // TODO-BUSINESS-LOGIC: Implement effective date retrieval
        // This should get the effective date for the coverage
        // The original code queries CARH_RIESGOS_CUBIERTOS and CART_RIESGOS_CUBIERTOS
        
        return fechaOcurrencia; // Default to occurrence date
    }

    /**
     * Gets the maximum indemnification factor for an insurance line.
     * 
     * @param ramo Insurance line code
     * @return Maximum indemnification factor
     */
    private int getMaxIndemnizationFactor(Long ramo) {
        // TODO-BUSINESS-LOGIC: Implement maximum indemnification factor retrieval
        // This should get the maximum indemnification factor from CG_REF_CODES
        // where rv_domain = 'MAXIMAINDEMNIZACION' and rv_low_value = ramo
        
        return 1; // Default factor
    }

    /**
     * Checks if an insurance line is LUC.
     * 
     * @param ramo Insurance line code
     * @return true if LUC, false otherwise
     */
    private boolean isLUCInsuranceLine(Long ramo) {
        // TODO-BUSINESS-LOGIC: Implement LUC insurance line check
        // This should check if the insurance line is LUC
        // The original code calls Pack_Valida_LUC.F_Valida_Ramo_LUC
        
        return false; // Placeholder
    }

    /**
     * Validates a reserve adjustment for LUC insurance lines.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param ajusteReserva Adjustment amount
     * @throws BusinessException If validation fails
     */
    private void validateLUCAdjustment(Long sucursal, Long ramo, Long poliza, Long certificado, 
                                      LocalDate fechaOcurrencia, Long ramoContable, 
                                      String cobertura, BigDecimal ajusteReserva) {
        // TODO-BUSINESS-LOGIC: Implement LUC adjustment validation
        // This should validate the adjustment for LUC insurance lines
        // The original code calls Pkg_Sint_Luc.fValidaAjuste and Pkg_Sint_Luc.fValidaPorcentajeAjte
        
        // If validation fails, throw BusinessException
    }

    /**
     * Validates a reserve adjustment for ACP coverage 003.
     * 
     * @param siniestro Claim entity
     * @param reserva Reserve entity
     * @param ajusteReserva Adjustment amount
     * @throws BusinessException If validation fails
     */
    private void validateACPCoverage003(Siniestro siniestro, ReservaCoberturaCertificado reserva, 
                                       BigDecimal ajusteReserva) {
        // TODO-BUSINESS-LOGIC: Implement ACP coverage 003 validation
        // This should validate the adjustment for ACP coverage 003
        // The original code checks ACPT_SINIESTROS.ACSI_NU_DIAS_PARCIAL
        
        // If validation fails, throw BusinessException
    }

    /**
     * Validates the death cause for life insurance.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param ajusteReserva Adjustment amount
     * @throws BusinessException If validation fails
     */
    private void validateDeathCause(Long sucursal, Long ramo, Long poliza, Long certificado, 
                                   LocalDate fechaOcurrencia, Long ramoContable, 
                                   String cobertura, BigDecimal ajusteReserva) {
        // TODO-BUSINESS-LOGIC: Implement death cause validation
        // This should validate the death cause for life insurance
        // The original code calls Gioseg.PKG_SINT_LUC.fValida_CausaFallece
        
        // If validation fails, throw BusinessException
    }

    /**
     * Validates and processes priority-based reserve adjustments for life insurance (ramo 13).
     * 
     * @param request The reserve adjustment request
     * @return Result of the priority adjustment operation
     */
    @Transactional
    public ReserveAdjustmentResultDTO processPriorityReserveAdjustments(ReserveAdjustmentRequestDTO request) {
        // Validate that this is a life insurance claim (ramo 13)
        Siniestro siniestro = siniestroRepository.findBySucursalAndRamoAndNumero(
                request.getSucursal(), request.getRamo(), request.getSiniestro())
                .orElseThrow(() -> new BusinessException("Siniestro no encontrado"));
        
        if (siniestro.getCertificadoSiniestro().getRamo() != 13) {
            throw new BusinessException("Esta funcionalidad solo está disponible para seguros de vida (ramo 13)");
        }
        
        ReserveAdjustmentResultDTO result = new ReserveAdjustmentResultDTO();
        
        try {
            // Calculate pending sum assured
            BigDecimal sumaPendiente = calculatePendingSumAssured(
                    siniestro.getCertificadoSiniestro().getSucursal(),
                    siniestro.getCertificadoSiniestro().getRamo(),
                    siniestro.getCertificadoSiniestro().getPoliza(),
                    siniestro.getCertificadoSiniestro().getCertificado(),
                    siniestro.getFechaOcurrencia(),
                    10L); // Default ramoContable = 10
            
            // Get total payments
            BigDecimal totalPayments = calculateTotalPayments(
                    siniestro.getSucursal(),
                    siniestro.getNumero(),
                    siniestro.getCertificadoSiniestro().getRamo(),
                    siniestro.getCertificadoSiniestro().getPoliza(),
                    siniestro.getCertificadoSiniestro().getCertificado(),
                    siniestro.getFechaOcurrencia(),
                    10L); // Default ramoContable = 10
            
            // Get sum assured
            BigDecimal sumaAsegurada = getSumAssured(
                    siniestro.getCertificadoSiniestro().getSucursal(),
                    siniestro.getCertificadoSiniestro().getRamo(),
                    siniestro.getCertificadoSiniestro().getPoliza(),
                    siniestro.getCertificadoSiniestro().getCertificado(),
                    siniestro.getFechaOcurrencia());
            
            // Calculate available balance
            BigDecimal saldoDisponible = sumaAsegurada.subtract(totalPayments.add(sumaPendiente.abs()));
            
            // Process adjustments by priority
            processPriorityAdjustments(request, siniestro, saldoDisponible);
            
            result.setSuccess(true);
            result.setMessages(Collections.singletonList("Ajustes por prioridad procesados correctamente"));
        } catch (Exception e) {
            log.error("Error processing priority adjustments: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessages(Collections.singletonList("Error: " + e.getMessage()));
        }
        
        return result;
    }

    /**
     * Processes reserve adjustments based on priority.
     * 
     * @param request The reserve adjustment request
     * @param siniestro Claim entity
     * @param saldoDisponible Available balance
     */
    private void processPriorityAdjustments(ReserveAdjustmentRequestDTO request, Siniestro siniestro, 
                                           BigDecimal saldoDisponible) {
        // TODO-BUSINESS-LOGIC: Implement priority-based reserve adjustments
        // This should process adjustments based on coverage priority
        // The original code is in PKG_AJUSTE_RESERVA.P_Ajuste_Prioridad_R
        
        // 1. Get all coverages with priority
        // 2. Sort them by priority
        // 3. Process adjustments in priority order
        // 4. If an adjustment exceeds available balance, adjust it down
        // 5. Update remaining balance for next coverages
        
        // Create a temporary record in SINT_TMP_MASIVO for each adjustment
        Long remesaId = generateRemesaId();
        
        // For each coverage with priority
        List<ReservaCoberturaCertificado> reservasConPrioridad = reservaCoberturaCertificadoRepository
            .findBySiniestroWithPriority(siniestro.getId());
        
        // Sort by priority
        reservasConPrioridad.sort(Comparator.comparing(ReservaCoberturaCertificado::getPrioridad));
        
        BigDecimal saldoRestante = saldoDisponible;
        
        for (ReservaCoberturaCertificado reserva : reservasConPrioridad) {
            // Get adjustment amount from request
            Optional<ReserveAdjustmentDTO> adjustment = request.getAdjustments().stream()
                .filter(a -> a.getRamoContable().equals(reserva.getCobertura().getRamoContable()) && 
                       a.getCobertura().equals(reserva.getCobertura().getCodigo()))
                .findFirst();
            
            if (adjustment.isPresent()) {
                BigDecimal montoAjuste = adjustment.get().getAjusteReserva();
                
                // Check if adjustment exceeds available balance
                if (montoAjuste.compareTo(saldoRestante) > 0) {
                    // Adjust down to available balance
                    montoAjuste = saldoRestante;
                }
                
                // Create temporary record
                SintTmpMasivo tmpMasivo = new SintTmpMasivo();
                tmpMasivo.setSucursal(siniestro.getSucursal());
                tmpMasivo.setRamo(siniestro.getCertificadoSiniestro().getRamo());
                tmpMasivo.setPoliza(siniestro.getCertificadoSiniestro().getPoliza());
                tmpMasivo.setCertificado(siniestro.getCertificadoSiniestro().getCertificado());
                tmpMasivo.setFechaOcurrencia(siniestro.getFechaOcurrencia());
                tmpMasivo.setIdRemesa(remesaId);
                tmpMasivo.setPrioridad(reserva.getPrioridad());
                tmpMasivo.setMsjVal("ST1");
                tmpMasivo.setObservaMsj("OK");
                tmpMasivo.setRegistro(1L);
                tmpMasivo.setMonto(montoAjuste);
                tmpMasivo.setRamoContable(reserva.getCobertura().getRamoContable());
                tmpMasivo.setCobertura(reserva.getCobertura().getCodigo());
                
                sintTmpMasivoRepository.save(tmpMasivo);
                
                // Update remaining balance
                saldoRestante = saldoRestante.subtract(montoAjuste);
            }
        }
    }

    /**
     * Generates a unique ID for a remesa.
     * 
     * @return Remesa ID
     */
    private Long generateRemesaId() {
        // TODO-BUSINESS-LOGIC: Implement remesa ID generation
        // This should generate a unique ID for a remesa
        // The original code uses a sequence: Gioseg.Seq_SINT_TMP_MASIVO.NextVal
        
        return System.currentTimeMillis(); // Placeholder
    }

    /**
     * Calculates total payments for a claim.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @return Total payments
     */
    private BigDecimal calculateTotalPayments(Long sucursal, Long siniestro, Long ramo, 
                                             Long poliza, Long certificado, 
                                             LocalDate fechaOcurrencia, Long ramoContable) {
        // TODO-BUSINESS-LOGIC: Implement total payments calculation
        // This should calculate total payments for a claim
        // The original code calls PKG_SINT_LUC.fCalcula_Sint_Pgos
        
        return BigDecimal.ZERO; // Placeholder
    }

    /**
     * Gets the sum assured for a policy.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @return Sum assured
     */
    private BigDecimal getSumAssured(Long sucursal, Long ramo, Long poliza, 
                                    Long certificado, LocalDate fechaOcurrencia) {
        // TODO-BUSINESS-LOGIC: Implement sum assured retrieval
        // This should get the sum assured for a policy
        // The original code calls PKG_SINT_LUC.fGet_SA_Vigente
        
        return BigDecimal.ZERO; // Placeholder
    }

    /**
     * Deletes a temporary remesa record.
     * 
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     */
    public void deleteRemesa(Long sucursal, Long ramo, Long poliza, Long certificado, 
                            Long ramoContable, String cobertura) {
        // Delete old records
        sintTmpMasivoRepository.deleteOldRecords(LocalDate.now().minusDays(2));
        
        // Delete specific record
        sintTmpMasivoRepository.deleteByPolicyAndCoverage(
            sucursal, ramo, poliza, certificado, ramoContable, cobertura);
    }
}