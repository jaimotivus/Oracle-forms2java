package com.yourcompany.yourapp.repository;

import com.yourcompany.yourapp.domain.*;
import com.yourcompany.yourapp.dto.*;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.service.ContabilidadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for managing siniestro (claim) operations, specifically for reserve adjustments.
 * This class handles the database operations related to claims and their reserves.
 * 
 * Converted from Oracle Forms: SINF50104 - SISTEMA DE ACTUALIZACION RESERVAS
 */
@Repository
public class SiniestroRepository {

    private static final Logger log = LoggerFactory.getLogger(SiniestroRepository.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private SiniestroJpaRepository siniestroJpaRepository;
    
    @Autowired
    private ReservaCoberturaCertificadoRepository reservaCoberturaCertificadoRepository;
    
    @Autowired
    private MovimientoSiniestroRepository movimientoSiniestroRepository;
    
    @Autowired
    private MovimientoCoberturaRepository movimientoCoberturaRepository;
    
    @Autowired
    private ContabilidadService contabilidadService;
    
    @Autowired
    private UtileriasService utileriasService;
    
    @Autowired
    private AjusteReservaService ajusteReservaService;

    /**
     * Retrieves information about a claim and its associated policy.
     * 
     * @param sucursal Branch code
     * @param ramo Branch code
     * @param siniestro Claim number
     * @return SiniestroInfoDTO containing claim and policy information
     */
    public SiniestroInfoDTO obtenerInfoSiniestro(Long sucursal, Long ramo, Long siniestro) {
        // Equivalent to PKG_AJUSTE_RESERVA.pInfoSiniestro
        SiniestroInfoDTO result = new SiniestroInfoDTO();
        
        // Get claim data
        Optional<Siniestro> siniestroOpt = siniestroJpaRepository.findBySucursalAndRamoAndNumero(
                sucursal, ramo, siniestro);
        
        if (siniestroOpt.isEmpty()) {
            throw new BusinessException("No se encontró el siniestro especificado");
        }
        
        Siniestro siniestroEntity = siniestroOpt.get();
        result.setSiniestro(siniestroEntity);
        
        // Get policy data
        CertificadoSiniestro certificadoSiniestro = entityManager.createQuery(
                "SELECT cs FROM CertificadoSiniestro cs " +
                "WHERE cs.sucursal = :sucursal " +
                "AND cs.siniestro = :siniestro", CertificadoSiniestro.class)
                .setParameter("sucursal", sucursal)
                .setParameter("siniestro", siniestro)
                .getSingleResult();
        
        result.setCertificadoSiniestro(certificadoSiniestro);
        
        // Calculate pending reserve amount for priority
        BigDecimal sumaAseguradaPendiente = utileriasService.calcularReservaPendiente(
                certificadoSiniestro.getSucursal(),
                certificadoSiniestro.getRamo(),
                certificadoSiniestro.getPoliza(),
                certificadoSiniestro.getCertificado(),
                siniestroEntity.getFechaOcurrencia(),
                10L); // Default ramoContable = 10
        
        result.setSumaAseguradaPendiente(sumaAseguradaPendiente);
        
        // Get status description
        String descripcionEstatus = utileriasService.obtenerDescripcion("0STATSIN", 
                siniestroEntity.getEstatus().toString());
        
        result.setDescripcionEstatus(descripcionEstatus);
        
        // Load coverages with their reserves
        List<CoberturaSiniestroDTO> coberturas = obtenerCoberturasSiniestro(sucursal, ramo, siniestro);
        result.setCoberturas(coberturas);
        
        return result;
    }
    
    /**
     * Retrieves the list of coverages for a claim with their reserve information.
     * 
     * @param sucursal Branch code
     * @param ramo Branch code
     * @param siniestro Claim number
     * @return List of coverages with reserve information
     */
    public List<CoberturaSiniestroDTO> obtenerCoberturasSiniestro(Long sucursal, Long ramo, Long siniestro) {
        // Equivalent to the COBERTURA record group query
        String jpql = "SELECT new com.yourcompany.yourapp.dto.CoberturaSiniestroDTO(" +
                "r.ramoContable.codigo, " +
                "r.ramoContable.descripcion, " +
                "r.cobertura.codigo, " +
                "r.cobertura.descripcion, " +
                "r.sumaAsegurada, " +
                "r.reserva, " +
                "r.ajustado, " +
                "r.liquidacion, " +
                "r.prioridad) " +
                "FROM ReservaCoberturaCertificado r " +
                "JOIN r.ramoContable c " +
                "JOIN r.cobertura o " +
                "WHERE r.sucursal = :sucursal " +
                "AND r.ramo = :ramo " +
                "AND r.siniestro = :siniestro";
        
        List<CoberturaSiniestroDTO> coberturas = entityManager.createQuery(jpql, CoberturaSiniestroDTO.class)
                .setParameter("sucursal", sucursal)
                .setParameter("ramo", ramo)
                .setParameter("siniestro", siniestro)
                .getResultList();
        
        // Calculate saldo for each coverage
        for (CoberturaSiniestroDTO cobertura : coberturas) {
            BigDecimal saldo = utileriasService.obtenerSaldo(
                    siniestro, 
                    sucursal, 
                    cobertura.getCodigoCobertura(), 
                    cobertura.getCodigoRamoContable());
            
            cobertura.setSaldo(saldo);
            
            // Get rechazo amount
            BigDecimal rechazo = movimientoCoberturaRepository.findRechazoAmount(
                    sucursal, 
                    siniestro, 
                    cobertura.getCodigoCobertura(), 
                    cobertura.getCodigoRamoContable());
            
            cobertura.setRechazo(rechazo);
        }
        
        return coberturas;
    }
    
    /**
     * Validates and processes reserve adjustments for a claim.
     * 
     * @param sucursal Branch code
     * @param ramo Branch code
     * @param siniestro Claim number
     * @param ajustes List of reserve adjustments to process
     * @return Result message
     */
    @Transactional
    public String procesarAjustesReserva(Long sucursal, Long ramo, Long siniestro, 
            List<AjusteReservaDTO> ajustes) {
        // Equivalent to KEY-COMMIT trigger
        if (!validarAjustes(ajustes)) {
            throw new BusinessException("Se debe validar los Ajustes, previamente");
        }
        
        for (AjusteReservaDTO ajuste : ajustes) {
            if (!generarAjusteReserva(sucursal, siniestro, ajuste)) {
                throw new BusinessException("Error al procesar el ajuste para la cobertura: " + 
                        ajuste.getCodigoCobertura());
            }
        }
        
        String tipoMovimiento = utileriasService.obtenerTipoMovimiento();
        String descTipoMovimiento = utileriasService.obtenerDescripcionTipoMovimiento(tipoMovimiento);
        
        return "Se realizó el movimiento de " + descTipoMovimiento + " correctamente.";
    }
    
    /**
     * Validates if there are valid adjustments to process.
     * 
     * @param ajustes List of adjustments to validate
     * @return true if there are valid adjustments, false otherwise
     */
    private boolean validarAjustes(List<AjusteReservaDTO> ajustes) {
        // Equivalent to PKG_UTILERIAS.fValidaAjuste
        return ajustes != null && !ajustes.isEmpty();
    }
    
    /**
     * Generates a reserve adjustment for a specific coverage.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ajuste Adjustment data
     * @return true if successful, false otherwise
     */
    private boolean generarAjusteReserva(Long sucursal, Long siniestro, AjusteReservaDTO ajuste) {
        // Equivalent to PKG_DML.fGeneraAjusteR
        try {
            // Register reserve adjustment
            if (!registrarReserva(sucursal, siniestro, ajuste.getRamo(), 
                    ajuste.getRamoContable(), ajuste.getCodigoCobertura())) {
                return false;
            }
            
            // Update coverage amounts
            if (!actualizarCobertura(sucursal, siniestro, ajuste.getRamoContable(), 
                    ajuste.getCodigoCobertura(), ajuste.getSucursalPoliza(), 
                    ajuste.getRamoPoliza(), ajuste.getPoliza(), ajuste.getCertificado())) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error al generar ajuste de reserva", e);
            return false;
        }
    }
    
    /**
     * Registers a reserve adjustment in the system.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramo Branch code
     * @param ramoCon Accounting branch code
     * @param cobertura Coverage code
     * @return true if successful, false otherwise
     */
    private boolean registrarReserva(Long sucursal, Long siniestro, Long ramo, 
            Long ramoCon, String cobertura) {
        // Equivalent to PKG_DML.fRegistraRva
        try {
            // Get next movement number
            Long numMovimiento = movimientoSiniestroRepository.obtenerSiguienteNumeroMovimiento(
                    sucursal, siniestro);
            
            // Get movement type
            String tipoMovimiento = utileriasService.obtenerTipoMovimiento();
            
            // Insert movement
            ReservaCoberturaCertificado reserva = reservaCoberturaCertificadoRepository
                    .findBySucursalAndSiniestroAndRamoContableAndCobertura(
                            sucursal, siniestro, ramoCon, cobertura);
            
            if (reserva == null) {
                throw new BusinessException("No se encontró la reserva para la cobertura especificada");
            }
            
            BigDecimal montoAjuste = reserva.getMontoAjusteMovimiento();
            
            // Insert movement record
            insertarMovimientoAjuste(sucursal, siniestro, ramoCon, cobertura, 
                    numMovimiento, montoAjuste, tipoMovimiento);
            
            // Validate for LUC branch
            if (!utileriasService.validarRamoLUC(sucursal, siniestro, ramoCon, cobertura, 
                    ramo, reserva.getSiniestro().getEstatus(), tipoMovimiento, 
                    reserva.getAjusteReserva())) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error al registrar reserva", e);
            return false;
        }
    }
    
    /**
     * Inserts a movement adjustment record.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramoCont Accounting branch code
     * @param cobertura Coverage code
     * @param numMovimiento Movement number
     * @param importe Amount
     * @param tipoMovto Movement type
     * @return true if successful, false otherwise
     */
    private boolean insertarMovimientoAjuste(Long sucursal, Long siniestro, Long ramoCont, 
            String cobertura, Long numMovimiento, BigDecimal importe, String tipoMovto) {
        // Equivalent to PKG_DML.fInsertaMovtoAjuste
        try {
            // Get currency
            String moneda = utileriasService.obtenerMoneda(sucursal, siniestro);
            
            // Create movement record
            MovimientoSiniestro movimiento = new MovimientoSiniestro();
            movimiento.setSucursal(sucursal);
            movimiento.setSiniestro(siniestro);
            movimiento.setNumeroMovimiento(numMovimiento);
            movimiento.setFechaMovimiento(LocalDate.now());
            movimiento.setTipoMovimiento(tipoMovto);
            movimiento.setAnalista(utileriasService.getCurrentUser());
            movimiento.setMonto(importe);
            movimiento.setMoneda(moneda);
            movimiento.setRamoContable(ramoCont);
            movimiento.setCobertura(cobertura);
            
            // Set aviso aceptado if status is 26
            Optional<Siniestro> siniestroOpt = siniestroJpaRepository.findById(siniestro);
            if (siniestroOpt.isPresent() && siniestroOpt.get().getEstatus() == 26) {
                movimiento.setAvisoAceptado("CO");
            }
            
            movimientoSiniestroRepository.save(movimiento);
            
            // Create coverage movement record
            MovimientoCoberturaCertificado movimientoCobertura = new MovimientoCoberturaCertificado();
            movimientoCobertura.setSucursal(sucursal);
            movimientoCobertura.setSiniestro(siniestro);
            movimientoCobertura.setNumeroMovimiento(numMovimiento);
            movimientoCobertura.setRamoContable(ramoCont);
            movimientoCobertura.setCobertura(cobertura);
            
            // Get policy data
            CertificadoSiniestro certificado = entityManager.createQuery(
                    "SELECT cs FROM CertificadoSiniestro cs " +
                    "WHERE cs.sucursal = :sucursal " +
                    "AND cs.siniestro = :siniestro", CertificadoSiniestro.class)
                    .setParameter("sucursal", sucursal)
                    .setParameter("siniestro", siniestro)
                    .getSingleResult();
            
            movimientoCobertura.setSucursalPoliza(certificado.getSucursal());
            movimientoCobertura.setRamoPoliza(certificado.getRamo());
            movimientoCobertura.setPoliza(certificado.getPoliza());
            movimientoCobertura.setCertificado(certificado.getCertificado());
            movimientoCobertura.setTipoMovimiento(tipoMovto);
            movimientoCobertura.setFechaMovimiento(LocalDate.now());
            movimientoCobertura.setMonto(importe);
            
            // Set moneda if status is 26
            if (siniestroOpt.isPresent() && siniestroOpt.get().getEstatus() == 26) {
                movimientoCobertura.setMoneda("CO");
            } else {
                movimientoCobertura.setMoneda("01");
            }
            
            movimientoCoberturaRepository.save(movimientoCobertura);
            
            // Register accounting entries
            String tipoSiniestro = utileriasService.obtenerTipoSiniestro(
                    certificado.getRamo(), ramoCont, cobertura, null);
            
            Long numAsiento = utileriasService.obtenerNumeroAsiento(sucursal, siniestro);
            
            if (!contabilidadService.contabilizarReserva(
                    sucursal, siniestro, numMovimiento, tipoMovto, ramoCont, cobertura,
                    certificado.getPoliza(), certificado.getCertificado(), 1L, null, null,
                    importe, 0L, LocalDate.now(), null, "N", null, null, null, null, null,
                    certificado.getRamo(), tipoSiniestro, numAsiento)) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error al insertar movimiento de ajuste", e);
            return false;
        }
    }
    
    /**
     * Updates coverage information after an adjustment.
     * 
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramoCon Accounting branch code
     * @param cobertura Coverage code
     * @param sucursalPoliza Policy branch code
     * @param ramo Branch code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @return true if successful, false otherwise
     */
    private boolean actualizarCobertura(Long sucursal, Long siniestro, Long ramoCon, 
            String cobertura, Long sucursalPoliza, Long ramo, Long poliza, Long certificado) {
        // Equivalent to PKG_DML.fActualizaCobertura
        try {
            ReservaCoberturaCertificado reserva = reservaCoberturaCertificadoRepository
                    .findBySucursalAndSiniestroAndRamoContableAndCoberturaAndPoliza(
                            sucursal, siniestro, ramoCon, cobertura, 
                            sucursalPoliza, ramo, poliza, certificado);
            
            if (reserva == null) {
                throw new BusinessException("No se encontró la reserva para la cobertura especificada");
            }
            
            // Update fecha efectiva and ajustado amount
            reserva.setFechaEfectiva(utileriasService.obtenerFechaEfectiva());
            reserva.setAjustado(reserva.getAjustado().add(reserva.getMontoAjusteMovimiento()));
            
            reservaCoberturaCertificadoRepository.save(reserva);
            
            return true;
        } catch (Exception e) {
            log.error("Error al actualizar cobertura", e);
            return false;
        }
    }
    
    /**
     * Validates reserve adjustments for a claim with priority rules.
     * 
     * @param sucursal Branch code
     * @param ramo Branch code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param errorMessage Error message output
     * @return Result message
     */
    @Transactional
    public String validarAjustesPrioridad(Long sucursal, Long ramo, Long poliza, 
            Long certificado, LocalDate fechaOcurrencia, StringBuilder errorMessage) {
        // Equivalent to PKG_AJUSTE_RESERVA.P_Ajuste_Prioridad_R
        try {
            // Get pending reserve amount
            BigDecimal reservaPendiente = utileriasService.calcularReservaPendiente(
                    sucursal, ramo, poliza, certificado, fechaOcurrencia, 10L);
            
            // Get payments amount
            BigDecimal pagos = utileriasService.calcularPagosSiniestro(
                    sucursal, ramo, poliza, certificado, fechaOcurrencia, 10L);
            
            // Get valid sum insured
            BigDecimal sumaAseguradaVigente = utileriasService.obtenerSumaAseguradaVigente(
                    sucursal, ramo, poliza, certificado, fechaOcurrencia);
            
            // Calculate global balance
            BigDecimal saldoGlobal = sumaAseguradaVigente.subtract(
                    pagos.add(reservaPendiente.subtract(pagos).abs()));
            
            // Process each coverage by priority
            List<ReservaCoberturaCertificado> coberturas = reservaCoberturaCertificadoRepository
                    .findBySucursalPolizaAndRamoAndPolizaAndCertificado(
                            sucursal, ramo, poliza, certificado);
            
            BigDecimal saldoAcumulado = BigDecimal.ZERO;
            
            // First pass: process coverages with priority
            for (ReservaCoberturaCertificado cobertura : coberturas) {
                if (cobertura.getPrioridad() != null && cobertura.getMontoAjusteMovimiento() != null) {
                    cobertura.setMarcaValidacion("P");
                    
                    // If adjustment exceeds available balance
                    if (cobertura.getMontoAjusteMovimiento().compareTo(BigDecimal.ZERO) > 0 && 
                            cobertura.getMontoAjusteMovimiento().compareTo(saldoGlobal) > 0) {
                        
                        BigDecimal montoAjuste = cobertura.getAjusteReserva();
                        BigDecimal saldoOcupado = BigDecimal.ZERO;
                        
                        // Find coverages with lower priority
                        buscarPrimerosConPrioridad(cobertura.getPrioridad(), montoAjuste, saldoOcupado, coberturas);
                        
                        BigDecimal saldoFaltante = cobertura.getMontoAjusteMovimiento().subtract(saldoGlobal);
                        
                        cobertura.setMarcaValidacion("N");
                        saldoAcumulado = saldoAcumulado.add(saldoOcupado);
                        
                        // Find coverages with higher priority
                        buscarUltimosConPrioridad(saldoFaltante, cobertura.getPrioridad(), 
                                saldoAcumulado, coberturas);
                        
                        saldoGlobal = saldoGlobal.subtract(cobertura.getMontoAjusteMovimiento());
                        saldoAcumulado = saldoAcumulado.add(
                                cobertura.getSaldoNuevo() != null ? cobertura.getSaldoNuevo() : BigDecimal.ZERO);
                    } 
                    // If adjustment is within available balance
                    else if (cobertura.getMontoAjusteMovimiento().compareTo(BigDecimal.ZERO) > 0 && 
                            cobertura.getMontoAjusteMovimiento().compareTo(saldoGlobal) <= 0) {
                        
                        cobertura.setMarcaValidacion("N");
                        saldoGlobal = saldoGlobal.subtract(cobertura.getMontoAjusteMovimiento());
                        saldoAcumulado = saldoAcumulado.add(
                                cobertura.getSaldoNuevo() != null ? cobertura.getSaldoNuevo() : BigDecimal.ZERO);
                    }
                    // If adjustment is negative
                    else if (cobertura.getMontoAjusteMovimiento() != null && 
                            cobertura.getMontoAjusteMovimiento().compareTo(BigDecimal.ZERO) < 0) {
                        
                        cobertura.setMarcaValidacion("N");
                    }
                    // For other cases, validate sum insured
                    else if (cobertura.getMarcaValidacion().equals("P")) {
                        try {
                            BigDecimal montoSA = ajusteReservaService.validarSumaPrioridad(
                                    sucursal, ramo, poliza, certificado, fechaOcurrencia,
                                    cobertura.getRamoContable(), cobertura.getCobertura(),
                                    cobertura.getMontoAjusteMovimiento());
                            
                            if (montoSA.compareTo(BigDecimal.ZERO) >= 0) {
                                cobertura.setMarcaValidacion("N");
                            } else {
                                cobertura.setMensajeValidacion("Error: " + errorMessage.toString());
                            }
                        } catch (Exception e) {
                            cobertura.setMensajeValidacion("Error: " + e.getMessage());
                        }
                    }
                } 
                // For coverages without priority
                else if (cobertura.getAjusteReserva() != null) {
                    cobertura.setMarcaValidacion("N");
                }
            }
            
            // Save changes
            reservaCoberturaCertificadoRepository.saveAll(coberturas);
            
            return "OK";
        } catch (Exception e) {
            log.error("Error al validar ajustes por prioridad", e);
            errorMessage.append("Error: ").append(e.getMessage());
            return "ERROR";
        }
    }
    
    /**
     * Finds coverages with lower priority for adjustment distribution.
     * 
     * @param prioridad Priority level
     * @param montoAjustar Adjustment amount
     * @param saldoOcupado Occupied balance (output)
     * @param coberturas List of coverages
     */
    private void buscarPrimerosConPrioridad(Integer prioridad, BigDecimal montoAjustar, 
            BigDecimal saldoOcupado, List<ReservaCoberturaCertificado> coberturas) {
        // Equivalent to PKG_AJUSTE_RESERVA.pBusca_PrimerosR
        
        for (ReservaCoberturaCertificado cobertura : coberturas) {
            // Find the coverage with the specified priority
            if (cobertura.getPrioridad() != null && cobertura.getPrioridad().equals(prioridad)) {
                // Calculate adjustment based on available balance
                if (cobertura.getSumaAsegurada().subtract(saldoOcupado).compareTo(montoAjustar) <= 0) {
                    cobertura.setAjusteReserva(montoAjustar.subtract(BigDecimal.ZERO));
                } else {
                    cobertura.setAjusteReserva(montoAjustar.subtract(saldoOcupado));
                }
                return;
            }
            
            // Accumulate balance from coverages with lower priority
            if (cobertura.getPrioridad() != null && 
                    cobertura.getPrioridad() < prioridad && 
                    cobertura.getAjusteReserva() == null) {
                
                saldoOcupado = saldoOcupado.add(cobertura.getSaldo());
            }
        }
    }
    
    /**
     * Finds coverages with higher priority for adjustment distribution.
     * 
     * @param saldoFaltante Missing balance
     * @param prioridad Priority level
     * @param saldoAcumulado Accumulated balance (input/output)
     * @param coberturas List of coverages
     */
    private void buscarUltimosConPrioridad(BigDecimal saldoFaltante, Integer prioridad, 
            BigDecimal saldoAcumulado, List<ReservaCoberturaCertificado> coberturas) {
        // Equivalent to PKG_AJUSTE_RESERVA.pBusca_UltimoR
        
        BigDecimal saldoRestante = saldoFaltante;
        
        // Process coverages in reverse order (from highest to lowest priority)
        for (int i = coberturas.size() - 1; i >= 0; i--) {
            ReservaCoberturaCertificado cobertura = coberturas.get(i);
            
            // Skip the coverage with the specified priority
            if (cobertura.getPrioridad() != null && cobertura.getPrioridad().equals(prioridad)) {
                continue;
            }
            
            // Process coverages with higher priority
            if (cobertura.getPrioridad() != null && 
                    cobertura.getPrioridad() > prioridad && 
                    cobertura.getPrioridad() <= 5) {
                
                // If coverage balance covers the missing amount
                if (cobertura.getSaldo().compareTo(saldoRestante) >= 0) {
                    cobertura.setAjusteReserva(cobertura.getSaldo().subtract(saldoRestante));
                    
                    if (cobertura.getMarcaValidacion().equals("P")) {
                        cobertura.setMarcaValidacion("N");
                    }
                    
                    liberarReservaAjuste(cobertura);
                    cobertura.setMarcaValidacion("K");
                    return;
                } 
                // If coverage balance partially covers the missing amount
                else if (cobertura.getSaldo().compareTo(saldoRestante) < 0 && 
                        cobertura.getSaldo().compareTo(BigDecimal.ZERO) > 0) {
                    
                    cobertura.setAjusteReserva(BigDecimal.ZERO);
                    cobertura.setMarcaValidacion("N");
                    liberarReservaAjuste(cobertura);
                    
                    saldoRestante = saldoRestante.subtract(cobertura.getSaldo());
                } 
                // If coverage balance is zero or negative
                else if (saldoRestante.compareTo(BigDecimal.ZERO) <= 0 || 
                        cobertura.getSaldo().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
            }
        }
    }
    
    /**
     * Releases reserve adjustment for a coverage.
     * 
     * @param cobertura Coverage to process
     */
    private void liberarReservaAjuste(ReservaCoberturaCertificado cobertura) {
        // Equivalent to PKG_AJUSTE_RESERVA.pLiberaReserva_Ajus
        
        // Calculate adjustment amounts
        if (cobertura.getMarcaValidacion().equals("N")) {
            cobertura.setAjustadoNuevo(cobertura.getAjustado().add(
                    cobertura.getAjusteReserva().subtract(cobertura.getSaldo())));
            
            cobertura.setMontoAjusteMovimiento(
                    cobertura.getAjusteReserva().subtract(cobertura.getSaldo()));
        } 
        else if (cobertura.getMarcaValidacion().equals("K")) {
            cobertura.setAjustadoNuevo(
                    cobertura.getAjustadoNuevo().add(cobertura.getAjustado()).add(
                            cobertura.getAjusteReserva().subtract(cobertura.getSaldo())));
            
            cobertura.setMontoAjusteMovimiento(
                    cobertura.getMontoAjusteMovimiento().add(cobertura.getAjusteReserva())
                            .subtract(cobertura.getSaldo()));
        }
        
        // Handle negative balance
        if (cobertura.getSaldoNuevo() != null && cobertura.getSaldoNuevo().compareTo(BigDecimal.ZERO) < 0) {
            cobertura.setAjustadoNuevo(cobertura.getAjustado());
            cobertura.setSaldoNuevo(cobertura.getSaldo());
        }
        
        // Set new balance
        if (cobertura.getMarcaValidacion().equals("N")) {
            cobertura.setSaldoNuevo(cobertura.getAjusteReserva() != null ? 
                    cobertura.getAjusteReserva() : BigDecimal.ZERO);
        } 
        else if (cobertura.getMarcaValidacion().equals("K")) {
            cobertura.setSaldoNuevo(cobertura.getSaldoNuevo().add(
                    cobertura.getAjusteReserva() != null ? 
                            cobertura.getAjusteReserva() : BigDecimal.ZERO));
            
            cobertura.setSaldo(cobertura.getSaldoNuevo());
        }
    }
    
    /**
     * Assigns validation marks to coverages based on adjustment status.
     */
    public void asignarMarcas() {
        // Equivalent to PKG_AJUSTE_RESERVA.pAsignaMarca
        List<ReservaCoberturaCertificado> coberturas = reservaCoberturaCertificadoRepository.findAll();
        
        for (ReservaCoberturaCertificado cobertura : coberturas) {
            if (cobertura.getMontoAjusteMovimiento() != null && 
                    cobertura.getPrioridad() != null) {
                cobertura.setMarcaValidacion("P");
            } 
            else if (cobertura.getMontoAjusteMovimiento() != null && 
                    cobertura.getPrioridad() == null) {
                cobertura.setMarcaValidacion("N");
            } 
            else {
                cobertura.setMarcaValidacion("S");
            }
        }
        
        reservaCoberturaCertificadoRepository.saveAll(coberturas);
    }
    
    /**
     * Assigns default amounts to coverages.
     */
    public void asignarMontos() {
        // Equivalent to PKG_AJUSTE_RESERVA.pAsignaMontos
        List<ReservaCoberturaCertificado> coberturas = reservaCoberturaCertificadoRepository.findAll();
        
        for (ReservaCoberturaCertificado cobertura : coberturas) {
            cobertura.setReservaNueva(cobertura.getReserva());
            cobertura.setSumaAseguradaNueva(cobertura.getSumaAsegurada());
            cobertura.setAjustadoNuevo(cobertura.getAjustado());
            cobertura.setSaldoNuevo(cobertura.getSaldo());
            cobertura.setLiquidacionNueva(cobertura.getLiquidacion());
            cobertura.setMarcaValidacion("S");
        }
        
        reservaCoberturaCertificadoRepository.saveAll(coberturas);
    }
    
    /**
     * Clears amounts for coverages.
     */
    public void limpiarMontos() {
        // Equivalent to PKG_AJUSTE_RESERVA.pLimpiaMontos
        List<ReservaCoberturaCertificado> coberturas = reservaCoberturaCertificadoRepository.findAll();
        
        for (ReservaCoberturaCertificado cobertura : coberturas) {
            cobertura.setAjusteReserva(null);
            cobertura.setAjustadoNuevo(null);
            cobertura.setSaldoNuevo(null);
            cobertura.setMensajeValidacion(null);
            cobertura.setMontoAjusteMovimiento(null);
            cobertura.setMarcaValidacion(null);
        }
        
        reservaCoberturaCertificadoRepository.saveAll(coberturas);
    }
    
    /**
     * JPA repository interface for Siniestro entity.
     */
    @Repository
    public interface SiniestroJpaRepository extends JpaRepository<Siniestro, Long> {
        
        /**
         * Finds a claim by branch, branch code and claim number.
         * 
         * @param sucursal Branch code
         * @param ramo Branch code
         * @param numero Claim number
         * @return Optional containing the claim if found
         */
        @Query("SELECT s FROM Siniestro s WHERE s.sucursal = :sucursal AND s.ramo = :ramo AND s.numero = :numero")
        Optional<Siniestro> findBySucursalAndRamoAndNumero(
                @Param("sucursal") Long sucursal, 
                @Param("ramo") Long ramo, 
                @Param("numero") Long numero);
    }
}