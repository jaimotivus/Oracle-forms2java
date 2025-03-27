package com.example.application.service;

import com.example.application.dto.*;
import com.example.application.entity.*;
import com.example.application.exception.BusinessException;
import com.example.application.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing insurance claim reserves and adjustments.
 * This service handles the business logic for adjusting claim reserves,
 * validating adjustments against policy limits, and recording reserve movements.
 */
@Service
public class SinfService {
    private static final Logger logger = LoggerFactory.getLogger(SinfService.class);

    @Autowired
    private SiniestroRepository siniestroRepository;

    @Autowired
    private CertificadoSiniestroRepository certificadoSiniestroRepository;

    @Autowired
    private ReservaCoberturaRepository reservaCoberturaRepository;

    @Autowired
    private MovimientoSiniestroRepository movimientoSiniestroRepository;

    @Autowired
    private MovimientoCoberturaRepository movimientoCoberturaRepository;

    @Autowired
    private MovimientoContableRepository movimientoContableRepository;

    @Autowired
    private RamoComponenteRepository ramoComponenteRepository;

    @Autowired
    private PolizaRepository polizaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TmpMasivoRepository tmpMasivoRepository;

    /**
     * Retrieves claim information including policy details and coverage information.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param siniestro Claim number
     * @return ClaimInfoDTO with complete claim information
     */
    public ClaimInfoDTO getClaimInfo(Long sucursal, Long ramo, Long siniestro) {
        logger.info("Getting claim info for siniestro: {}", siniestro);
        
        ClaimInfoDTO claimInfo = new ClaimInfoDTO();
        
        // Get siniestro information
        Siniestro siniestroEntity = siniestroRepository.findBySucursalAndRamoAndNumero(sucursal, ramo, siniestro)
            .orElseThrow(() -> new BusinessException("Siniestro not found"));
        
        // Map siniestro data to DTO
        claimInfo.setSiniestro(mapSiniestroToDTO(siniestroEntity));
        
        // Get policy information
        CertificadoSiniestro certificado = certificadoSiniestroRepository.findBySiniestro(siniestro)
            .orElseThrow(() -> new BusinessException("Certificate not found for claim"));
        
        // Map policy data to DTO
        claimInfo.setPoliza(mapPolizaToDTO(certificado));
        
        // Get coverage information
        List<ReservaCobertura> coberturas = reservaCoberturaRepository.findBySiniestro(siniestro);
        
        // Map coverage data to DTOs
        claimInfo.setCoberturas(coberturas.stream()
            .map(this::mapCoberturaToDTO)
            .collect(Collectors.toList()));
        
        // Calculate pending reserve amount for LUC
        BigDecimal pendingReserve = calculatePendingReserve(
            certificado.getSucursal(),
            certificado.getRamo(),
            certificado.getPoliza(),
            certificado.getCertificado(),
            siniestroEntity.getFechaOcurrencia()
        );
        
        claimInfo.setPendingReserve(pendingReserve);
        
        return claimInfo;
    }

    /**
     * Validates and processes reserve adjustments for claim coverages.
     *
     * @param adjustmentRequest The request containing adjustment details
     * @return Result of the adjustment operation
     */
    @Transactional
    public AdjustmentResultDTO processReserveAdjustments(AdjustmentRequestDTO adjustmentRequest) {
        logger.info("Processing reserve adjustments for siniestro: {}", adjustmentRequest.getSiniestroId());
        
        AdjustmentResultDTO result = new AdjustmentResultDTO();
        result.setSuccess(true);
        
        // Validate that there are adjustments to process
        if (adjustmentRequest.getAdjustments() == null || adjustmentRequest.getAdjustments().isEmpty()) {
            throw new BusinessException("No adjustments to process");
        }
        
        // Get claim information
        Siniestro siniestro = siniestroRepository.findById(adjustmentRequest.getSiniestroId())
            .orElseThrow(() -> new BusinessException("Siniestro not found"));
        
        CertificadoSiniestro certificado = certificadoSiniestroRepository.findBySiniestro(siniestro.getNumero())
            .orElseThrow(() -> new BusinessException("Certificate not found for claim"));
        
        // Process each adjustment
        List<String> messages = new ArrayList<>();
        
        for (CoberturaAdjustmentDTO adjustment : adjustmentRequest.getAdjustments()) {
            try {
                // Validate the adjustment
                validateAdjustment(siniestro, certificado, adjustment);
                
                // Process the adjustment
                processAdjustment(siniestro, certificado, adjustment);
                
                messages.add("Adjustment processed successfully for coverage: " + adjustment.getCoberturaId());
            } catch (BusinessException e) {
                // Collect error messages but continue processing other adjustments
                messages.add("Error processing adjustment for coverage " + adjustment.getCoberturaId() + ": " + e.getMessage());
                result.setSuccess(false);
            }
        }
        
        result.setMessages(messages);
        
        // If we're processing adjustments for LUC ramo (13), handle priority adjustments
        if (certificado.getRamo() == 13) {
            try {
                processPriorityAdjustments(siniestro, certificado);
            } catch (BusinessException e) {
                messages.add("Error processing priority adjustments: " + e.getMessage());
                result.setSuccess(false);
            }
        }
        
        return result;
    }

    /**
     * Validates a single coverage adjustment against business rules.
     *
     * @param siniestro The claim
     * @param certificado The policy certificate
     * @param adjustment The adjustment to validate
     * @throws BusinessException if validation fails
     */
    private void validateAdjustment(Siniestro siniestro, CertificadoSiniestro certificado, CoberturaAdjustmentDTO adjustment) {
        logger.debug("Validating adjustment for coverage: {}", adjustment.getCoberturaId());
        
        // Get the coverage
        ReservaCobertura cobertura = reservaCoberturaRepository.findById(adjustment.getCoberturaId())
            .orElseThrow(() -> new BusinessException("Coverage not found"));
        
        // Validate adjustment amount
        if (adjustment.getAdjustmentAmount() == null) {
            throw new BusinessException("Adjustment amount cannot be null");
        }
        
        // For closed claims (status 26), negative adjustments are not allowed
        if (siniestro.getStatus() == 26 && adjustment.getAdjustmentAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Negative adjustments are not allowed for closed claims");
        }
        
        // Validate that adjustment is not equal to current balance
        if (adjustment.getAdjustmentAmount().compareTo(cobertura.getSaldo()) == 0) {
            throw new BusinessException("Adjustment amount must be different from current balance");
        }
        
        // Get sum insured
        BigDecimal sumInsured = getSumInsured(
            certificado.getSucursal(),
            certificado.getRamo(),
            certificado.getPoliza(),
            certificado.getCertificado(),
            cobertura.getRamoContable(),
            cobertura.getCobertura(),
            siniestro.getFechaOcurrencia()
        );
        
        // Validate sum insured
        if (sumInsured.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Sum insured cannot be zero");
        }
        
        // For LUC ramo (13), validate against payments
        if (certificado.getRamo() == 13) {
            BigDecimal payments = getPaymentsForCoverage(
                siniestro.getSucursal(),
                siniestro.getNumero(),
                cobertura.getRamoContable(),
                cobertura.getCobertura()
            );
            
            BigDecimal newBalance = adjustment.getAdjustmentAmount().subtract(cobertura.getSaldo());
            BigDecimal availableBalance = sumInsured.subtract(payments).subtract(cobertura.getSaldo());
            
            if (newBalance.compareTo(availableBalance) > 0) {
                throw new BusinessException("Adjustment exceeds available sum insured after payments");
            }
        }
        
        // Validate against sum insured
        if (adjustment.getAdjustmentAmount().compareTo(sumInsured) > 0) {
            // For some ramos, we might allow exceeding sum insured by a factor
            Integer maxDays = getMaxIndemnizationDays(certificado.getRamo());
            
            if (siniestro.getStatus() != 26 && 
                adjustment.getAdjustmentAmount().compareTo(sumInsured.multiply(new BigDecimal(maxDays))) > 0) {
                throw new BusinessException("Adjustment exceeds maximum allowed sum insured");
            }
        }
        
        // For ACP ramo (3) and coverage 003, validate against partial days
        if (cobertura.getRamoContable() == 3 && "003".equals(cobertura.getCobertura())) {
            validateACPCoverage(siniestro, sumInsured, adjustment.getAdjustmentAmount());
        }
        
        // Validate cause of death for LUC
        if (certificado.getRamo() == 13) {
            validateCauseOfDeath(
                certificado.getSucursal(),
                certificado.getRamo(),
                certificado.getPoliza(),
                certificado.getCertificado(),
                siniestro.getFechaOcurrencia(),
                cobertura.getRamoContable(),
                cobertura.getCobertura(),
                adjustment.getAdjustmentAmount()
            );
        }
    }

    /**
     * Processes a single coverage adjustment.
     *
     * @param siniestro The claim
     * @param certificado The policy certificate
     * @param adjustment The adjustment to process
     * @throws BusinessException if processing fails
     */
    private void processAdjustment(Siniestro siniestro, CertificadoSiniestro certificado, CoberturaAdjustmentDTO adjustment) {
        logger.debug("Processing adjustment for coverage: {}", adjustment.getCoberturaId());
        
        // Get the coverage
        ReservaCobertura cobertura = reservaCoberturaRepository.findById(adjustment.getCoberturaId())
            .orElseThrow(() -> new BusinessException("Coverage not found"));
        
        // Calculate adjustment difference
        BigDecimal adjustmentDifference = adjustment.getAdjustmentAmount().subtract(cobertura.getSaldo());
        
        // Create movement number
        Long movementNumber = getNextMovementNumber(siniestro.getSucursal(), siniestro.getNumero());
        
        // Get movement type
        String movementType = getMovementType(siniestro.getStatus());
        
        // Create movement record
        MovimientoSiniestro movimiento = new MovimientoSiniestro();
        movimiento.setSucursal(siniestro.getSucursal());
        movimiento.setSiniestro(siniestro.getNumero());
        movimiento.setNumeroMovimiento(movementNumber);
        movimiento.setFechaMovimiento(LocalDate.now());
        movimiento.setTipoMovimiento(movementType);
        movimiento.setAnalista(getCurrentUser());
        movimiento.setMonto(adjustmentDifference);
        movimiento.setMoneda(getClaimCurrency(siniestro.getSucursal(), siniestro.getNumero()));
        movimiento.setRamoContable(cobertura.getRamoContable());
        movimiento.setCobertura(cobertura.getCobertura());
        movimiento.setAvisoAceptado(siniestro.getStatus() == 26 ? "CO" : null);
        
        movimientoSiniestroRepository.save(movimiento);
        
        // Create coverage movement record
        MovimientoCobertura movimientoCobertura = new MovimientoCobertura();
        movimientoCobertura.setSucursal(siniestro.getSucursal());
        movimientoCobertura.setSiniestro(siniestro.getNumero());
        movimientoCobertura.setNumeroMovimiento(movementNumber);
        movimientoCobertura.setRamoContable(cobertura.getRamoContable());
        movimientoCobertura.setCobertura(cobertura.getCobertura());
        movimientoCobertura.setSucursalPoliza(certificado.getSucursal());
        movimientoCobertura.setRamoPoliza(certificado.getRamo());
        movimientoCobertura.setPoliza(certificado.getPoliza());
        movimientoCobertura.setCertificado(certificado.getCertificado());
        movimientoCobertura.setTipoMovimiento(movementType);
        movimientoCobertura.setFechaMovimiento(LocalDate.now());
        movimientoCobertura.setMonto(adjustmentDifference);
        movimientoCobertura.setMoneda(siniestro.getStatus() == 26 ? "CO" : "01");
        
        movimientoCoberturaRepository.save(movimientoCobertura);
        
        // Create accounting entries
        createAccountingEntries(
            siniestro.getSucursal(),
            siniestro.getNumero(),
            movementNumber,
            movementType,
            cobertura.getRamoContable(),
            cobertura.getCobertura(),
            certificado.getRamo(),
            certificado.getPoliza(),
            certificado.getCertificado(),
            adjustmentDifference
        );
        
        // Update coverage reserve
        cobertura.setAjustado(cobertura.getAjustado().add(adjustmentDifference));
        cobertura.setSaldo(adjustment.getAdjustmentAmount());
        cobertura.setFechaEfectiva(siniestro.getFechaOcurrencia());
        
        reservaCoberturaRepository.save(cobertura);
    }

    /**
     * Processes priority-based adjustments for LUC ramo (13).
     *
     * @param siniestro The claim
     * @param certificado The policy certificate
     * @throws BusinessException if processing fails
     */
    private void processPriorityAdjustments(Siniestro siniestro, CertificadoSiniestro certificado) {
        logger.debug("Processing priority adjustments for siniestro: {}", siniestro.getNumero());
        
        // Calculate global available balance
        BigDecimal sumInsured = getSumInsuredForPolicy(
            certificado.getSucursal(),
            certificado.getRamo(),
            certificado.getPoliza(),
            certificado.getCertificado(),
            siniestro.getFechaOcurrencia()
        );
        
        BigDecimal payments = getPaymentsForPolicy(
            certificado.getSucursal(),
            certificado.getRamo(),
            certificado.getPoliza(),
            certificado.getCertificado()
        );
        
        BigDecimal pendingReserve = getPendingReserveForPolicy(
            certificado.getSucursal(),
            certificado.getRamo(),
            certificado.getPoliza(),
            certificado.getCertificado(),
            siniestro.getFechaOcurrencia()
        );
        
        BigDecimal globalBalance = sumInsured.subtract(payments.add(pendingReserve.subtract(payments)));
        
        // Get all coverages with priority
        List<ReservaCobertura> priorityCoverages = reservaCoberturaRepository.findBySiniestroWithPriority(siniestro.getNumero());
        
        // Sort by priority
        priorityCoverages.sort((c1, c2) -> c1.getPrioridad().compareTo(c2.getPrioridad()));
        
        // Process each coverage by priority
        for (ReservaCobertura cobertura : priorityCoverages) {
            if (cobertura.getAjustadoMovimiento() != null && cobertura.getAjustadoMovimiento().compareTo(BigDecimal.ZERO) > 0) {
                // If adjustment exceeds available balance
                if (cobertura.getAjustadoMovimiento().compareTo(globalBalance) > 0) {
                    // Adjust coverages with higher priority
                    adjustHigherPriorityCoverages(
                        priorityCoverages,
                        cobertura.getPrioridad(),
                        cobertura.getAjustadoMovimiento().subtract(globalBalance)
                    );
                    
                    // Update global balance
                    globalBalance = globalBalance.subtract(cobertura.getAjustadoMovimiento());
                } else {
                    // Update global balance
                    globalBalance = globalBalance.subtract(cobertura.getAjustadoMovimiento());
                }
                
                // Mark coverage as processed
                cobertura.setMarcaValidacion("N");
                reservaCoberturaRepository.save(cobertura);
            }
        }
    }

    /**
     * Adjusts coverages with higher priority to accommodate an adjustment that exceeds available balance.
     *
     * @param coverages List of coverages sorted by priority
     * @param currentPriority The priority of the current coverage
     * @param shortfall The amount by which the adjustment exceeds available balance
     */
    private void adjustHigherPriorityCoverages(List<ReservaCobertura> coverages, Integer currentPriority, BigDecimal shortfall) {
        logger.debug("Adjusting higher priority coverages for shortfall: {}", shortfall);
        
        BigDecimal remainingShortfall = shortfall;
        
        // Process coverages with higher priority (lower priority number)
        for (ReservaCobertura cobertura : coverages) {
            if (cobertura.getPrioridad() < currentPriority && remainingShortfall.compareTo(BigDecimal.ZERO) > 0) {
                // Calculate how much we can reduce this coverage
                BigDecimal maxReduction = cobertura.getSaldo();
                BigDecimal actualReduction = maxReduction.compareTo(remainingShortfall) > 0 ? 
                    remainingShortfall : maxReduction;
                
                if (actualReduction.compareTo(BigDecimal.ZERO) > 0) {
                    // Reduce the coverage
                    cobertura.setAjustadoMovimiento(cobertura.getAjustadoMovimiento().subtract(actualReduction));
                    cobertura.setSaldoNuevo(cobertura.getSaldo().subtract(actualReduction));
                    cobertura.setMarcaValidacion("K"); // Mark as adjusted by priority
                    
                    reservaCoberturaRepository.save(cobertura);
                    
                    // Update remaining shortfall
                    remainingShortfall = remainingShortfall.subtract(actualReduction);
                }
            }
        }
        
        // If we still have shortfall, we need to reduce the current adjustment
        if (remainingShortfall.compareTo(BigDecimal.ZERO) > 0) {
            for (ReservaCobertura cobertura : coverages) {
                if (cobertura.getPrioridad().equals(currentPriority)) {
                    cobertura.setAjustadoMovimiento(cobertura.getAjustadoMovimiento().subtract(remainingShortfall));
                    cobertura.setSaldoNuevo(cobertura.getAjustadoMovimiento());
                    
                    reservaCoberturaRepository.save(cobertura);
                    break;
                }
            }
        }
    }

    /**
     * Creates accounting entries for a reserve adjustment.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param movementNumber Movement number
     * @param movementType Movement type
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param ramoPoliza Policy line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param amount Adjustment amount
     */
    private void createAccountingEntries(
            Long sucursal, 
            Long siniestro, 
            Long movementNumber, 
            String movementType,
            Integer ramoContable, 
            String cobertura, 
            Long ramoPoliza, 
            Long poliza, 
            Long certificado, 
            BigDecimal amount) {
        
        logger.debug("Creating accounting entries for siniestro: {}, movement: {}", siniestro, movementNumber);
        
        // Get claim type
        String tipoSiniestro = getClaimType(ramoPoliza, ramoContable, cobertura, "");
        
        // Get accounting entry number
        Long asientoNumber = getNextAccountingEntryNumber(sucursal, siniestro);
        
        // Get components for accounting entries
        List<RamoComponente> components = ramoComponenteRepository.findByRamoAndCoberturaAndMovimiento(
            ramoPoliza, ramoContable, cobertura, movementType, tipoSiniestro
        );
        
        // Create accounting entries
        for (RamoComponente component : components) {
            // Only process components that match the sign of the amount
            boolean isPositiveAmount = amount.compareTo(BigDecimal.ZERO) > 0;
            boolean isPositiveComponent = component.getComponente().substring(3, 4).equals("P");
            
            if ((isPositiveAmount && isPositiveComponent) || (!isPositiveAmount && !isPositiveComponent)) {
                // Create debit entry
                if ("D".equals(component.getCodificacion())) {
                    MovimientoContable movimiento = new MovimientoContable();
                    movimiento.setSucursal(sucursal);
                    movimiento.setSiniestro(siniestro);
                    movimiento.setNumeroMovimiento(movementNumber);
                    movimiento.setTipoMovimiento(movementType);
                    movimiento.setRamoContable(ramoContable);
                    movimiento.setCobertura(cobertura);
                    movimiento.setSucursalPoliza(sucursal);
                    movimiento.setRamoPoliza(ramoPoliza);
                    movimiento.setPoliza(poliza);
                    movimiento.setCertificado(certificado);
                    movimiento.setNumeroMovimientoContable(components.indexOf(component) + 1L);
                    movimiento.setCompania(1L); // Default company
                    movimiento.setNumeroMayor(component.getNumeroMayor());
                    movimiento.setDebe(amount.abs());
                    movimiento.setHaber(BigDecimal.ZERO);
                    movimiento.setNumeroDocumento(0L);
                    movimiento.setFechaMovimiento(LocalDate.now());
                    movimiento.setFechaContable(null);
                    movimiento.setEstadoContable("N");
                    movimiento.setNumeroAsiento(asientoNumber);
                    
                    movimientoContableRepository.save(movimiento);
                }
                // Create credit entry
                else if ("H".equals(component.getCodificacion())) {
                    MovimientoContable movimiento = new MovimientoContable();
                    movimiento.setSucursal(sucursal);
                    movimiento.setSiniestro(siniestro);
                    movimiento.setNumeroMovimiento(movementNumber);
                    movimiento.setTipoMovimiento(movementType);
                    movimiento.setRamoContable(ramoContable);
                    movimiento.setCobertura(cobertura);
                    movimiento.setSucursalPoliza(sucursal);
                    movimiento.setRamoPoliza(ramoPoliza);
                    movimiento.setPoliza(poliza);
                    movimiento.setCertificado(certificado);
                    movimiento.setNumeroMovimientoContable(components.indexOf(component) + 1L);
                    movimiento.setCompania(1L); // Default company
                    movimiento.setNumeroMayor(component.getNumeroMayor());
                    movimiento.setDebe(BigDecimal.ZERO);
                    movimiento.setHaber(amount.abs());
                    movimiento.setNumeroDocumento(0L);
                    movimiento.setFechaMovimiento(LocalDate.now());
                    movimiento.setFechaContable(null);
                    movimiento.setEstadoContable("N");
                    movimiento.setNumeroAsiento(asientoNumber);
                    
                    movimientoContableRepository.save(movimiento);
                }
            }
        }
    }

    /**
     * Validates ACP coverage (ramo 3, coverage 003) against partial days.
     *
     * @param siniestro The claim
     * @param sumInsured Sum insured amount
     * @param adjustmentAmount Adjustment amount
     * @throws BusinessException if validation fails
     */
    private void validateACPCoverage(Siniestro siniestro, BigDecimal sumInsured, BigDecimal adjustmentAmount) {
        logger.debug("Validating ACP coverage for siniestro: {}", siniestro.getNumero());
        
        // Get partial days
        Integer partialDays = getACPPartialDays(siniestro.getSucursal(), siniestro.getNumero());
        
        // Calculate maximum reserve
        BigDecimal maxReserve = sumInsured.divide(new BigDecimal(7)).multiply(new BigDecimal(partialDays));
        
        // Validate adjustment
        if (adjustmentAmount.compareTo(maxReserve) > 0) {
            throw new BusinessException("Adjustment exceeds maximum reserve for ACP coverage (Sum Insured × # partial days)");
        }
    }

    /**
     * Gets the number of partial days for ACP claims.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Number of partial days
     */
    private Integer getACPPartialDays(Long sucursal, Long siniestro) {
        // Try to get from ACP siniestros table
        Optional<Integer> days = siniestroRepository.getACPPartialDays(sucursal, siniestro);
        
        // Default to 365 if not found
        return days.orElse(365);
    }

    /**
     * Gets the maximum number of days for indemnization based on ramo.
     *
     * @param ramo Insurance line code
     * @return Maximum number of days
     */
    private Integer getMaxIndemnizationDays(Long ramo) {
        // Try to get from reference codes
        Optional<Integer> days = siniestroRepository.getMaxIndemnizationDays(ramo);
        
        // Default to 1 if not found
        return days.orElse(1);
    }

    /**
     * Validates cause of death for LUC claims.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param adjustmentAmount Adjustment amount
     * @throws BusinessException if validation fails
     */
    private void validateCauseOfDeath(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            LocalDate fechaOcurrencia,
            Integer ramoContable, 
            String cobertura, 
            BigDecimal adjustmentAmount) {
        
        logger.debug("Validating cause of death for LUC claim");
        
        // This would call a stored procedure or repository method to validate
        boolean isValid = siniestroRepository.validateCauseOfDeath(
            sucursal, ramo, poliza, certificado, fechaOcurrencia, ramoContable, cobertura, "RA", adjustmentAmount
        );
        
        if (!isValid) {
            throw new BusinessException("Invalid cause of death for LUC claim");
        }
    }

    /**
     * Gets the next movement number for a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Next movement number
     */
    private Long getNextMovementNumber(Long sucursal, Long siniestro) {
        // Get max movement number from both tables
        Long maxSiniestroMovement = movimientoSiniestroRepository.getMaxMovementNumber(sucursal, siniestro);
        Long maxCoberturaMovement = movimientoCoberturaRepository.getMaxMovementNumber(sucursal, siniestro);
        
        // Get the maximum of both
        Long maxMovement = Math.max(
            maxSiniestroMovement != null ? maxSiniestroMovement : 0,
            maxCoberturaMovement != null ? maxCoberturaMovement : 0
        );
        
        // Return next number
        return maxMovement + 1;
    }

    /**
     * Gets the next accounting entry number for a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Next accounting entry number
     */
    private Long getNextAccountingEntryNumber(Long sucursal, Long siniestro) {
        // Get max accounting entry number
        Long maxEntryNumber = movimientoContableRepository.getMaxEntryNumber(sucursal, siniestro);
        
        // Return next number
        return (maxEntryNumber != null ? maxEntryNumber : 0) + 1;
    }

    /**
     * Gets the movement type based on claim status.
     *
     * @param status Claim status
     * @return Movement type code
     */
    private String getMovementType(Integer status) {
        // Default is "RA" (Reserve Adjustment)
        String movementType = "RA";
        
        // If claim is closed (status 24), use "RL" (Reserve Liquidation)
        if (status == 24) {
            movementType = "RL";
        }
        // If claim is rejected (status 25), use "RX" (Reserve Rejection)
        else if (status == 25) {
            movementType = "RX";
        }
        
        return movementType;
    }

    /**
     * Gets the claim type based on policy and coverage information.
     *
     * @param ramoPoliza Policy line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param marca Mark code
     * @return Claim type code
     */
    private String getClaimType(Long ramoPoliza, Integer ramoContable, String cobertura, String marca) {
        if ("CO".equals(marca)) {
            // Get claim type for CO mark
            return siniestroRepository.getClaimTypeForMark(ramoPoliza, ramoContable, cobertura);
        } else {
            // Get claim type for sum insured
            return siniestroRepository.getClaimTypeForSumInsured(ramoPoliza, ramoContable, cobertura);
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
        // Get policy for claim
        CertificadoSiniestro certificado = certificadoSiniestroRepository.findBySiniestro(siniestro)
            .orElseThrow(() -> new BusinessException("Certificate not found for claim"));
        
        // Get policy currency
        return polizaRepository.getCurrency(
            certificado.getSucursal(),
            certificado.getRamo(),
            certificado.getPoliza()
        );
    }

    /**
     * Gets the sum insured for a specific coverage.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param fechaOcurrencia Occurrence date
     * @return Sum insured amount
     */
    private BigDecimal getSumInsured(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            Integer ramoContable, 
            String cobertura, 
            LocalDate fechaOcurrencia) {
        
        logger.debug("Getting sum insured for policy: {}, coverage: {}", poliza, cobertura);
        
        // Get effective date
        LocalDate fechaEfectiva = getEffectiveDate(
            sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaOcurrencia
        );
        
        // Get sum insured from risks covered
        BigDecimal sumInsured = reservaCoberturaRepository.getSumInsured(
            sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaEfectiva
        );
        
        // For LUC products (IPS, IVS), get special sum insured
        String productType = siniestroRepository.getProductType(ramo, ramoContable, cobertura);
        
        if ("IPS".equals(productType) || "IVS".equals(productType)) {
            sumInsured = getLUCSumInsured(
                sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaOcurrencia, productType
            );
        }
        
        return sumInsured;
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
    private LocalDate getEffectiveDate(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            Integer ramoContable, 
            String cobertura, 
            LocalDate fechaOcurrencia) {
        
        // Get max effective date that is less than or equal to occurrence date
        return reservaCoberturaRepository.getEffectiveDate(
            sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaOcurrencia
        );
    }

    /**
     * Gets the special sum insured for LUC products (IPS, IVS).
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param fechaOcurrencia Occurrence date
     * @param productType Product type (IPS or IVS)
     * @return Sum insured amount
     */
    private BigDecimal getLUCSumInsured(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            Integer ramoContable, 
            String cobertura, 
            LocalDate fechaOcurrencia,
            String productType) {
        
        logger.debug("Getting LUC sum insured for policy: {}, product type: {}", poliza, productType);
        
        if ("IVS".equals(productType)) {
            // Get IVS sum insured
            return reservaCoberturaRepository.getIVSSumInsured(
                sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaOcurrencia
            );
        } else {
            // Get IPS sum insured
            return reservaCoberturaRepository.getIPSSumInsured(
                sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaOcurrencia
            );
        }
    }

    /**
     * Gets the payments for a specific coverage.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @return Total payments amount
     */
    private BigDecimal getPaymentsForCoverage(Long sucursal, Long siniestro, Integer ramoContable, String cobertura) {
        logger.debug("Getting payments for siniestro: {}, coverage: {}", siniestro, cobertura);
        
        return movimientoCoberturaRepository.getPaymentsForCoverage(
            sucursal, siniestro, ramoContable, cobertura
        );
    }

    /**
     * Gets the payments for a policy.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @return Total payments amount
     */
    private BigDecimal getPaymentsForPolicy(Long sucursal, Long ramo, Long poliza, Long certificado) {
        logger.debug("Getting payments for policy: {}", poliza);
        
        return movimientoCoberturaRepository.getPaymentsForPolicy(
            sucursal, ramo, poliza, certificado
        );
    }

    /**
     * Gets the pending reserve for a policy.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @return Pending reserve amount
     */
    private BigDecimal getPendingReserveForPolicy(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            LocalDate fechaOcurrencia) {
        
        logger.debug("Getting pending reserve for policy: {}", poliza);
        
        return reservaCoberturaRepository.getPendingReserveForPolicy(
            sucursal, ramo, poliza, certificado, fechaOcurrencia
        );
    }

    /**
     * Gets the sum insured for a policy.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @return Sum insured amount
     */
    private BigDecimal getSumInsuredForPolicy(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            LocalDate fechaOcurrencia) {
        
        logger.debug("Getting sum insured for policy: {}", poliza);
        
        return reservaCoberturaRepository.getSumInsuredForPolicy(
            sucursal, ramo, poliza, certificado, fechaOcurrencia
        );
    }

    /**
     * Calculates the pending reserve amount for LUC.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @return Pending reserve amount
     */
    private BigDecimal calculatePendingReserve(
            Long sucursal, 
            Long ramo, 
            Long poliza, 
            Long certificado, 
            LocalDate fechaOcurrencia) {
        
        logger.debug("Calculating pending reserve for policy: {}", poliza);
        
        return reservaCoberturaRepository.calculatePendingReserve(
            sucursal, ramo, poliza, certificado, fechaOcurrencia, 10 // Default ramo contable for LUC
        );
    }

    /**
     * Gets the current user.
     *
     * @return Current user name
     */
    private String getCurrentUser() {
        // In a real application, this would use Spring Security
        return "SYSTEM";
    }

    /**
     * Maps a Siniestro entity to a SiniestroDTO.
     *
     * @param siniestro The entity to map
     * @return The mapped DTO
     */
    private SiniestroDTO mapSiniestroToDTO(Siniestro siniestro) {
        SiniestroDTO dto = new SiniestroDTO();
        dto.setId(siniestro.getId());
        dto.setSucursal(siniestro.getSucursal());
        dto.setRamo(siniestro.getRamo());
        dto.setNumero(siniestro.getNumero());
        dto.setFechaOcurrencia(siniestro.getFechaOcurrencia());
        dto.setStatus(siniestro.getStatus());
        dto.setStatusDescription(siniestro.getStatusDescription());
        
        // Get client name
        Cliente cliente = clienteRepository.findById(siniestro.getClienteId())
            .orElse(null);
        
        if (cliente != null) {
            dto.setClienteNombre(cliente.getNombre());
        }
        
        return dto;
    }

    /**
     * Maps a CertificadoSiniestro entity to a PolizaDTO.
     *
     * @param certificado The entity to map
     * @return The mapped DTO
     */
    private PolizaDTO mapPolizaToDTO(CertificadoSiniestro certificado) {
        PolizaDTO dto = new PolizaDTO();
        dto.setSucursal(certificado.getSucursal());
        dto.setRamo(certificado.getRamo());
        dto.setPoliza(certificado.getPoliza());
        dto.setCertificado(certificado.getCertificado());
        dto.setBeneficiario(certificado.getBeneficiario());
        
        return dto;
    }

    /**
     * Maps a ReservaCobertura entity to a CoberturaDTO.
     *
     * @param cobertura The entity to map
     * @return The mapped DTO
     */
    private CoberturaDTO mapCoberturaToDTO(ReservaCobertura cobertura) {
        CoberturaDTO dto = new CoberturaDTO();
        dto.setId(cobertura.getId());
        dto.setRamoContable(cobertura.getRamoContable());
        dto.setRamoContableDescripcion(cobertura.getRamoContableDescripcion());
        dto.setCobertura(cobertura.getCobertura());
        dto.setCoberturaDescripcion(cobertura.getCoberturaDescripcion());
        dto.setSumaAsegurada(cobertura.getSumaAsegurada());
        dto.setReserva(cobertura.getReserva());
        dto.setAjustado(cobertura.getAjustado());
        dto.setLiquidacion(cobertura.getLiquidacion());
        dto.setRechazo(cobertura.getRechazo());
        dto.setSaldo(cobertura.getSaldo());
        dto.setPrioridad(cobertura.getPrioridad());
        
        return dto;
    }
}