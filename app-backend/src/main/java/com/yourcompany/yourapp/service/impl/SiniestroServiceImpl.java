package com.yourcompany.yourapp.service.impl;

import com.yourcompany.yourapp.domain.*;
import com.yourcompany.yourapp.dto.*;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.repository.*;
import com.yourcompany.yourapp.service.SiniestroService;
import com.yourcompany.yourapp.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing Siniestro (Claims) operations.
 * This class handles the business logic for insurance claims processing,
 * particularly focusing on reserve adjustments for claim coverages.
 * 
 * Converted from Oracle Forms module SINF50104.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiniestroServiceImpl implements SiniestroService {

    private final SiniestroRepository siniestroRepository;
    private final SiniestroMovimientoRepository siniestroMovimientoRepository;
    private final SiniestroReservaCoberturaRepository siniestroReservaCoberturaRepository;
    private final SiniestroMovimientoCoberturaRepository siniestroMovimientoCoberturaRepository;
    private final SiniestroMovimientoContableRepository siniestroMovimientoContableRepository;
    private final SiniestroLiquidacionRepository siniestroLiquidacionRepository;
    private final SiniestroLiquidacionCoberturaRepository siniestroLiquidacionCoberturaRepository;
    private final SiniestroDeducibleCoaseguroRepository siniestroDeducibleCoaseguroRepository;
    private final PolizaRepository polizaRepository;
    private final CertificadoRepository certificadoRepository;
    private final CertificadoSiniestroRepository certificadoSiniestroRepository;
    private final RamoContableRepository ramoContableRepository;
    private final CoberturaRepository coberturaRepository;
    private final ClienteRepository clienteRepository;
    private final SintCobLucRepository sintCobLucRepository;
    private final SintTmpMasivoRepository sintTmpMasivoRepository;
    private final MessageUtils messageUtils;

    /**
     * Retrieves claim information including policy details and coverages.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param siniestro Claim number
     * @return SiniestroInfoDTO with claim details
     */
    @Override
    @Transactional(readOnly = true)
    public SiniestroInfoDTO getSiniestroInfo(Long sucursal, Long ramo, Long siniestro) {
        log.debug("Request to get Siniestro info: {}/{}/{}", sucursal, ramo, siniestro);
        
        // Retrieve basic siniestro information
        Siniestro siniestroEntity = siniestroRepository.findBySucursalAndRamoAndNumero(sucursal, ramo, siniestro)
            .orElseThrow(() -> new BusinessException("Siniestro no encontrado"));
        
        // Get cliente information
        Cliente cliente = clienteRepository.findByNacionalidadAndCedulaRif(
            siniestroEntity.getCdNacionalidad(), siniestroEntity.getNuCedulaRif())
            .orElseThrow(() -> new BusinessException("Cliente no encontrado"));
        
        // Get certificate information
        CertificadoSiniestro certSiniestro = certificadoSiniestroRepository
            .findBySucursalAndSiniestro(sucursal, siniestro)
            .orElseThrow(() -> new BusinessException("Certificado de siniestro no encontrado"));
        
        // Build the DTO with basic information
        SiniestroInfoDTO result = new SiniestroInfoDTO();
        result.setSucursal(sucursal);
        result.setRamo(ramo);
        result.setNumeroSiniestro(siniestro);
        result.setFechaOcurrencia(siniestroEntity.getFeOcurrencia());
        result.setEstadoSiniestro(siniestroEntity.getStSiniestro());
        result.setDescripcionEstado(getDescripcionEstado(siniestroEntity.getStSiniestro()));
        result.setNombreAsegurado(cliente.getNmApellidoRazon());
        
        // Set policy information
        result.setSucursalPoliza(certSiniestro.getCaceCasuCdSucursal());
        result.setRamoPoliza(certSiniestro.getCaceCarpCdRamo());
        result.setNumeroPoliza(certSiniestro.getCaceCapoNuPoliza());
        result.setNumeroCertificado(certSiniestro.getCaceNuCertificado());
        result.setNumeroBeneficiario(certSiniestro.getNuBeneficiario());
        
        // Calculate pending reserve amount for LUC (Límite Único Combinado)
        BigDecimal sumaAseguradaPendiente = calcularSumaAseguradaPendiente(
            certSiniestro.getCaceCasuCdSucursal(),
            certSiniestro.getCaceCarpCdRamo(),
            certSiniestro.getCaceCapoNuPoliza(),
            certSiniestro.getCaceNuCertificado(),
            siniestroEntity.getFeOcurrencia(),
            10L // Default ramo contable for LUC
        );
        result.setSumaAseguradaPendiente(sumaAseguradaPendiente);
        
        // Get coverages with reserves
        List<CoberturaSiniestroDTO> coberturas = getSiniestroCoberturasWithReserves(
            sucursal, siniestro, certSiniestro);
        result.setCoberturas(coberturas);
        
        return result;
    }

    /**
     * Retrieves coverages with reserve information for a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param certSiniestro Certificate claim entity
     * @return List of coverages with reserve information
     */
    private List<CoberturaSiniestroDTO> getSiniestroCoberturasWithReserves(
            Long sucursal, Long siniestro, CertificadoSiniestro certSiniestro) {
        
        List<SiniestroReservaCobertura> reservas = siniestroReservaCoberturaRepository
            .findBySucursalAndSiniestro(sucursal, siniestro);
        
        return reservas.stream().map(reserva -> {
            CoberturaSiniestroDTO dto = new CoberturaSiniestroDTO();
            
            // Get ramo contable and cobertura information
            RamoContable ramoContable = ramoContableRepository.findById(reserva.getSicoCacbCarbCdRamo())
                .orElseThrow(() -> new BusinessException("Ramo contable no encontrado"));
            
            Cobertura cobertura = coberturaRepository
                .findByRamoAndCobertura(reserva.getSicoCacbCarbCdRamo(), reserva.getSicoCacbCdCobertura())
                .orElseThrow(() -> new BusinessException("Cobertura no encontrada"));
            
            // Set basic coverage information
            dto.setRamoContableCodigo(reserva.getSicoCacbCarbCdRamo());
            dto.setRamoContableDescripcion(ramoContable.getDeRamo());
            dto.setCoberturaCodigo(reserva.getSicoCacbCdCobertura());
            dto.setCoberturaDescripcion(cobertura.getDeCobertura());
            
            // Set financial information
            dto.setSumaAsegurada(reserva.getMtSumaSeg());
            dto.setReservaInicial(reserva.getMtReserva());
            dto.setAjustado(reserva.getMtAjustado());
            dto.setLiquidacion(reserva.getMtLiquidacion());
            dto.setPago(reserva.getMtPago());
            dto.setRechazo(reserva.getMtRechazo());
            
            // Calculate current balance
            BigDecimal saldo = calcularSaldo(sucursal, siniestro, 
                reserva.getSicoCacbCarbCdRamo(), reserva.getSicoCacbCdCobertura());
            dto.setSaldo(saldo);
            
            // Get priority for LUC
            Optional<SintCobLuc> cobLuc = sintCobLucRepository.findByRamoAndRamoContableAndCobertura(
                certSiniestro.getCaceCarpCdRamo(), 
                reserva.getSicoCacbCarbCdRamo(), 
                reserva.getSicoCacbCdCobertura());
            
            cobLuc.ifPresent(luc -> dto.setPrioridad(luc.getPrioridad()));
            
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Calculates the current balance for a coverage in a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @return Current balance
     */
    private BigDecimal calcularSaldo(Long sucursal, Long siniestro, Long ramoContable, String cobertura) {
        // Get total movement amount
        BigDecimal totalMovimientos = siniestroMovimientoCoberturaRepository
            .sumMovimientosBySucursalAndSiniestroAndRamoContableAndCobertura(
                sucursal, siniestro, ramoContable, cobertura, 
                List.of("IC", "CC", "ID") // Exclude these movement types
            );
        
        // Get total payments
        BigDecimal totalPagos = siniestroLiquidacionCoberturaRepository
            .sumLiquidacionesBySucursalAndSiniestroAndRamoContableAndCobertura(
                sucursal, siniestro, ramoContable, cobertura,
                700L, 750L, // Egress codes range
                List.of("Y", "Z"), // Exclude these payment types
                List.of(6L, 7L) // Exclude these statuses
            );
        
        // Calculate balance
        return totalMovimientos.subtract(totalPagos.abs());
    }

    /**
     * Processes reserve adjustments for claim coverages.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param siniestro Claim number
     * @param ajustes List of coverage adjustments
     * @return Result of the operation
     */
    @Override
    @Transactional
    public AjusteReservaResultDTO procesarAjustesReserva(Long sucursal, Long ramo, Long siniestro, 
            List<AjusteReservaDTO> ajustes) {
        log.debug("Request to process reserve adjustments for Siniestro: {}/{}/{}", sucursal, ramo, siniestro);
        
        AjusteReservaResultDTO result = new AjusteReservaResultDTO();
        result.setExito(true);
        List<String> mensajes = new ArrayList<>();
        
        // Get siniestro information
        Siniestro siniestroEntity = siniestroRepository.findBySucursalAndRamoAndNumero(sucursal, ramo, siniestro)
            .orElseThrow(() -> new BusinessException("Siniestro no encontrado"));
        
        // Get certificate information
        CertificadoSiniestro certSiniestro = certificadoSiniestroRepository
            .findBySucursalAndSiniestro(sucursal, siniestro)
            .orElseThrow(() -> new BusinessException("Certificado de siniestro no encontrado"));
        
        // Create a unique remesa ID for batch processing
        Long remesaId = sintTmpMasivoRepository.getNextRemesaId();
        
        // If ramo is 13 (Life insurance), validate priority adjustments
        if (certSiniestro.getCaceCarpCdRamo().equals(13L)) {
            validarAjustesPrioridad(certSiniestro, siniestroEntity.getFeOcurrencia(), ajustes, remesaId);
        }
        
        // Process each adjustment
        for (AjusteReservaDTO ajuste : ajustes) {
            try {
                // Validate adjustment
                validarAjusteReserva(siniestroEntity, certSiniestro, ajuste);
                
                // Generate movement number
                Long numeroMovimiento = obtenerNumeroMovimiento(sucursal, siniestro);
                
                // Register reserve adjustment
                registrarAjusteReserva(sucursal, siniestro, certSiniestro.getCaceCarpCdRamo(),
                    ajuste.getRamoContableCodigo(), ajuste.getCoberturaCodigo(), 
                    certSiniestro.getCaceCasuCdSucursal(), certSiniestro.getCaceCarpCdRamo(),
                    certSiniestro.getCaceCapoNuPoliza(), certSiniestro.getCaceNuCertificado(),
                    numeroMovimiento, ajuste.getMontoAjuste());
                
                // Update coverage reserve
                actualizarReservaCobertura(sucursal, siniestro, 
                    ajuste.getRamoContableCodigo(), ajuste.getCoberturaCodigo(),
                    certSiniestro.getCaceCasuCdSucursal(), certSiniestro.getCaceCarpCdRamo(),
                    certSiniestro.getCaceCapoNuPoliza(), certSiniestro.getCaceNuCertificado(),
                    ajuste.getMontoAjuste());
                
            } catch (BusinessException e) {
                mensajes.add(String.format("Error en cobertura %s: %s", 
                    ajuste.getCoberturaCodigo(), e.getMessage()));
                result.setExito(false);
            }
        }
        
        result.setMensajes(mensajes);
        
        // If successful, return success message
        if (result.isExito()) {
            String tipoMovimiento = obtenerTipoMovimiento(siniestroEntity.getStSiniestro());
            String descripcionMovimiento = obtenerDescripcionTipoMovimiento(tipoMovimiento);
            mensajes.add("Se realizó el movimiento de " + descripcionMovimiento + " correctamente.");
        }
        
        return result;
    }

    /**
     * Validates reserve adjustments for coverages with priority (LUC).
     *
     * @param certSiniestro Certificate claim entity
     * @param fechaOcurrencia Occurrence date
     * @param ajustes List of coverage adjustments
     * @param remesaId Batch ID for processing
     */
    private void validarAjustesPrioridad(CertificadoSiniestro certSiniestro, 
            LocalDate fechaOcurrencia, List<AjusteReservaDTO> ajustes, Long remesaId) {
        
        // Calculate total sum assured
        BigDecimal sumaAseguradaTotal = calcularSumaAseguradaVigente(
            certSiniestro.getCaceCasuCdSucursal(),
            certSiniestro.getCaceCarpCdRamo(),
            certSiniestro.getCaceCapoNuPoliza(),
            certSiniestro.getCaceNuCertificado(),
            fechaOcurrencia
        );
        
        // Calculate total payments
        BigDecimal totalPagos = calcularTotalPagos(
            certSiniestro.getCaceCasuCdSucursal(),
            certSiniestro.getCaceCarpCdRamo(),
            certSiniestro.getCaceCapoNuPoliza(),
            certSiniestro.getCaceNuCertificado(),
            fechaOcurrencia,
            10L // Default ramo contable for LUC
        );
        
        // Calculate pending reserve
        BigDecimal reservaPendiente = calcularReservaPendiente(
            certSiniestro.getCaceCasuCdSucursal(),
            certSiniestro.getCaceCarpCdRamo(),
            certSiniestro.getCaceCapoNuPoliza(),
            certSiniestro.getCaceNuCertificado(),
            fechaOcurrencia,
            10L, // Default ramo contable for LUC
            remesaId
        );
        
        // Calculate available balance
        BigDecimal saldoDisponible = sumaAseguradaTotal.subtract(totalPagos.add(reservaPendiente.abs()));
        
        // Validate and adjust reserves based on priority
        BigDecimal saldoGlobalRestante = saldoDisponible;
        
        // First pass: validate high priority adjustments
        for (AjusteReservaDTO ajuste : ajustes) {
            // Skip if no priority or not marked for processing
            if (ajuste.getPrioridad() == null || !ajuste.isMarcarParaProcesar()) {
                continue;
            }
            
            // Check if this is a LUC coverage
            boolean esCoberturaPriorizada = sintCobLucRepository.existsByRamoAndRamoContableAndCobertura(
                certSiniestro.getCaceCarpCdRamo(), 
                ajuste.getRamoContableCodigo(), 
                ajuste.getCoberturaCodigo());
            
            if (esCoberturaPriorizada) {
                BigDecimal montoAjuste = ajuste.getMontoAjuste().subtract(ajuste.getSaldoActual());
                
                // If adjustment exceeds available balance
                if (montoAjuste.compareTo(BigDecimal.ZERO) > 0 && montoAjuste.compareTo(saldoGlobalRestante) > 0) {
                    if (saldoGlobalRestante.compareTo(BigDecimal.ZERO) > 0) {
                        // Adjust to maximum available
                        ajuste.setMontoAjuste(ajuste.getSaldoActual().add(saldoGlobalRestante));
                        ajuste.setMensajeValidacion("El monto de Ajuste ha sobrepasado el monto de Suma Asegurada de la póliza");
                        saldoGlobalRestante = BigDecimal.ZERO;
                    } else {
                        // Reject adjustment
                        ajuste.setMontoAjuste(ajuste.getSaldoActual());
                        ajuste.setMensajeValidacion("Se rechaza por Agotamiento en Suma Asegurada");
                        ajuste.setMarcarParaProcesar(false);
                    }
                } else if (montoAjuste.compareTo(BigDecimal.ZERO) > 0) {
                    // Reduce available balance
                    saldoGlobalRestante = saldoGlobalRestante.subtract(montoAjuste);
                }
            }
        }
        
        // Second pass: adjust lower priority reserves if needed
        if (saldoGlobalRestante.compareTo(BigDecimal.ZERO) <= 0) {
            ajustarReservasPorPrioridad(ajustes, certSiniestro.getCaceCarpCdRamo());
        }
        
        // Store temporary data for batch processing
        for (AjusteReservaDTO ajuste : ajustes) {
            if (ajuste.isMarcarParaProcesar() && ajuste.getPrioridad() != null) {
                guardarAjusteEnRemesa(
                    certSiniestro.getCaceCasuCdSucursal(),
                    certSiniestro.getCaceCarpCdRamo(),
                    certSiniestro.getCaceCapoNuPoliza(),
                    certSiniestro.getCaceNuCertificado(),
                    fechaOcurrencia,
                    ajuste.getPrioridad(),
                    ajuste.getRamoContableCodigo(),
                    ajuste.getCoberturaCodigo(),
                    ajuste.getMontoAjuste(),
                    remesaId
                );
            }
        }
    }

    /**
     * Adjusts reserves based on priority when total exceeds available sum assured.
     *
     * @param ajustes List of coverage adjustments
     * @param ramoPoliza Insurance line code
     */
    private void ajustarReservasPorPrioridad(List<AjusteReservaDTO> ajustes, Long ramoPoliza) {
        // Sort adjustments by priority (higher priority first)
        List<AjusteReservaDTO> ajustesOrdenados = ajustes.stream()
            .filter(a -> a.getPrioridad() != null && a.isMarcarParaProcesar())
            .sorted((a1, a2) -> a1.getPrioridad().compareTo(a2.getPrioridad()))
            .collect(Collectors.toList());
        
        // TODO-BUSINESS-LOGIC: Implement complex priority-based adjustment logic
        // This requires detailed business rules about how to redistribute reserves
        // when the total exceeds the available sum assured
        // 
        // 1. The original code uses a complex algorithm to reduce reserves of lower priority coverages
        // 2. Test by creating a scenario where total adjustments exceed available sum assured
        // 3. Verify that higher priority coverages maintain their reserves while lower ones are reduced
        
        log.warn("Priority-based reserve adjustment not fully implemented");
    }

    /**
     * Stores adjustment information in a temporary table for batch processing.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param prioridad Priority
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param monto Amount
     * @param remesaId Batch ID
     */
    private void guardarAjusteEnRemesa(Long sucursal, Long ramo, Long poliza, Long certificado,
            LocalDate fechaOcurrencia, Integer prioridad, Long ramoContable, String cobertura,
            BigDecimal monto, Long remesaId) {
        
        SintTmpMasivo tmpMasivo = new SintTmpMasivo();
        tmpMasivo.setStmSucursal(sucursal);
        tmpMasivo.setStmCarpRamo(ramo);
        tmpMasivo.setStmPoliza(poliza);
        tmpMasivo.setStmCertificado(certificado);
        tmpMasivo.setStmFechaOcurrencia(fechaOcurrencia);
        tmpMasivo.setStmIdRemesa(remesaId);
        tmpMasivo.setStmPrioridad(prioridad);
        tmpMasivo.setStmMsjVal("ST1");
        tmpMasivo.setSmtObservaMsj("OK");
        tmpMasivo.setSmtRegistro(1);
        tmpMasivo.setStmMonto(monto);
        tmpMasivo.setStmCarbRamo(ramoContable);
        tmpMasivo.setStmCobertura(cobertura);
        
        sintTmpMasivoRepository.save(tmpMasivo);
    }

    /**
     * Validates a reserve adjustment.
     *
     * @param siniestro Claim entity
     * @param certSiniestro Certificate claim entity
     * @param ajuste Adjustment data
     * @throws BusinessException If validation fails
     */
    private void validarAjusteReserva(Siniestro siniestro, CertificadoSiniestro certSiniestro, 
            AjusteReservaDTO ajuste) {
        
        // Skip validation if not marked for processing
        if (!ajuste.isMarcarParaProcesar()) {
            return;
        }
        
        // Validate adjustment amount
        if (ajuste.getMontoAjuste() == null) {
            throw new BusinessException("Debe indicar Monto Ajustado a la Cobertura");
        }
        
        // For ARP (status 26), validate adjustment is not negative
        if (siniestro.getStSiniestro().equals(26L) && ajuste.getMontoAjuste().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("No es permitido ingresar montos negativos en el ajuste");
        }
        
        // Validate adjustment is not zero
        if (ajuste.getMontoAjuste().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("El Monto a ajustar es igual a cero");
        }
        
        // Validate adjustment is not equal to current balance
        if (ajuste.getMontoAjuste().compareTo(ajuste.getSaldoActual()) == 0) {
            throw new BusinessException("Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: " + 
                ajuste.getMontoAjuste() + " Saldo: " + ajuste.getSaldoActual());
        }
        
        // Get sum assured for coverage
        BigDecimal sumaAsegurada = obtenerSumaAsegurada(
            certSiniestro.getCaceCasuCdSucursal(),
            certSiniestro.getCaceCarpCdRamo(),
            certSiniestro.getCaceCapoNuPoliza(),
            certSiniestro.getCaceNuCertificado(),
            siniestro.getFeOcurrencia(),
            ajuste.getRamoContableCodigo(),
            ajuste.getCoberturaCodigo()
        );
        
        // Validate sum assured exists
        if (sumaAsegurada.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Monto de suma asegurada es igual a cero");
        }
        
        // Validate coverage exists and is valid
        Cobertura cobertura = coberturaRepository
            .findByRamoAndCobertura(ajuste.getRamoContableCodigo(), ajuste.getCoberturaCodigo())
            .orElseThrow(() -> new BusinessException("Cobertura no encontrada"));
        
        // Check if coverage validates against sum assured
        if ("S".equals(cobertura.getInValidaMontoSin())) {
            // Get maximum indemnification factor
            Integer factorMaximo = obtenerFactorMaximoIndemnizacion(certSiniestro.getCaceCarpCdRamo());
            
            // For non-ARP status, validate adjustment doesn't exceed sum assured
            if (!siniestro.getStSiniestro().equals(26L) && 
                    ajuste.getMontoAjuste().compareTo(sumaAsegurada.multiply(new BigDecimal(factorMaximo))) > 0) {
                throw new BusinessException("Monto de la reserva debe ser menor o igual que la suma asegurada que es de " + 
                    sumaAsegurada.multiply(new BigDecimal(factorMaximo)));
            }
        }
        
        // For ACP coverage 003, validate special rules
        if (ajuste.getRamoContableCodigo().equals(3L) && "003".equals(ajuste.getCoberturaCodigo())) {
            validarReservaAcp(
                siniestro.getSisiCasuCdSucursal(),
                siniestro.getSisiNuSiniestro(),
                sumaAsegurada,
                ajuste.getMontoAjuste()
            );
        }
        
        // Validate LUC rules for non-ARP status
        if (!siniestro.getStSiniestro().equals(26L) && 
                esRamoLuc(certSiniestro.getCaceCarpCdRamo()) && 
                ajuste.getMontoAjuste().compareTo(BigDecimal.ZERO) > 0) {
            
            validarMontoAjusteLuc(
                certSiniestro.getCaceCasuCdSucursal(),
                ajuste.getRamoContableCodigo(),
                ajuste.getCoberturaCodigo(),
                certSiniestro.getCaceCarpCdRamo(),
                certSiniestro.getCaceCapoNuPoliza(),
                certSiniestro.getCaceNuCertificado(),
                siniestro.getFeOcurrencia(),
                ajuste.getMontoAjuste()
            );
        }
        
        // Validate cause of death for life insurance
        if (certSiniestro.getCaceCarpCdRamo().equals(13L)) {
            validarCausaFallecimiento(
                certSiniestro.getCaceCasuCdSucursal(),
                certSiniestro.getCaceCarpCdRamo(),
                certSiniestro.getCaceCapoNuPoliza(),
                certSiniestro.getCaceNuCertificado(),
                siniestro.getFeOcurrencia(),
                ajuste.getRamoContableCodigo(),
                ajuste.getCoberturaCodigo(),
                "RA", // Default movement type
                ajuste.getMontoAjuste()
            );
        }
        
        // Validate blindaje plus
        if (esRamoBlindajePlus(certSiniestro.getCaceCarpCdRamo())) {
            validarAjusteBlindajePlus(
                certSiniestro.getCaceCasuCdSucursal(),
                certSiniestro.getCaceCarpCdRamo(),
                certSiniestro.getCaceCapoNuPoliza(),
                certSiniestro.getCaceNuCertificado(),
                siniestro.getFeOcurrencia(),
                ajuste.getRamoContableCodigo(),
                ajuste.getCoberturaCodigo(),
                ajuste.getMontoAjuste(),
                ajuste.getSaldoActual()
            );
        }
    }

    /**
     * Registers a reserve adjustment.
     *
     * @param sucursalSiniestro Claim branch code
     * @param numeroSiniestro Claim number
     * @param ramoPoliza Insurance line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param sucursalPoliza Policy branch code
     * @param ramoPolizaReal Policy insurance line code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param numeroMovimiento Movement number
     * @param montoAjuste Adjustment amount
     * @throws BusinessException If registration fails
     */
    @Transactional
    public void registrarAjusteReserva(Long sucursalSiniestro, Long numeroSiniestro, Long ramoPoliza,
            Long ramoContable, String cobertura, Long sucursalPoliza, Long ramoPolizaReal,
            Long numeroPoliza, Long numeroCertificado, Long numeroMovimiento, BigDecimal montoAjuste) {
        
        // Get movement type
        String tipoMovimiento = obtenerTipoMovimiento(null);
        
        // Get currency
        String moneda = obtenerMoneda(sucursalSiniestro, numeroSiniestro);
        
        // Calculate adjustment difference
        BigDecimal montoMovimiento = calcularMontoMovimiento(
            sucursalSiniestro, numeroSiniestro, ramoContable, cobertura, 
            sucursalPoliza, ramoPolizaReal, numeroPoliza, numeroCertificado, montoAjuste);
        
        try {
            // Insert movement record
            SiniestroMovimiento movimiento = new SiniestroMovimiento();
            movimiento.setSisiCasuCdSucursal(sucursalSiniestro);
            movimiento.setSisiNuSiniestro(numeroSiniestro);
            movimiento.setNuMovimiento(numeroMovimiento);
            movimiento.setFeMovimiento(LocalDate.now());
            movimiento.setTpMovimiento(tipoMovimiento);
            movimiento.setCdAnalista(obtenerUsuarioActual());
            movimiento.setMtMovimiento(montoMovimiento);
            movimiento.setCdMoneda(moneda);
            movimiento.setCarbCdRamo(ramoContable);
            movimiento.setCacbCdCobertura(cobertura);
            movimiento.setNuAvisoAceptado(obtenerAvisoAceptado(null));
            
            siniestroMovimientoRepository.save(movimiento);
            
            // Insert coverage movement record
            SiniestroMovimientoCobertura movimientoCobertura = new SiniestroMovimientoCobertura();
            movimientoCobertura.setSimsCasuCdSucursal(sucursalSiniestro);
            movimientoCobertura.setSimsSisiNuSiniestro(numeroSiniestro);
            movimientoCobertura.setSimsNuMovimiento(numeroMovimiento);
            movimientoCobertura.setSiccCarbCdRamo(ramoContable);
            movimientoCobertura.setSiccCacbCdCobertura(cobertura);
            movimientoCobertura.setSiccCasuCdSucursal(sucursalPoliza);
            movimientoCobertura.setSiccCarpCdRamo(ramoPolizaReal);
            movimientoCobertura.setSiccCaceCapoNuPoliza(numeroPoliza);
            movimientoCobertura.setSiccCaceNuCertificado(numeroCertificado);
            movimientoCobertura.setSimsTpMovimiento(tipoMovimiento);
            movimientoCobertura.setSimsFeMovimiento(LocalDate.now());
            movimientoCobertura.setMtMovimiento(montoMovimiento);
            movimientoCobertura.setCdMoneda(moneda);
            
            siniestroMovimientoCoberturaRepository.save(movimientoCobertura);
            
            // Register accounting entries
            registrarContabilidadReserva(
                sucursalSiniestro, numeroSiniestro, numeroMovimiento, tipoMovimiento,
                ramoContable, cobertura, numeroPoliza, numeroCertificado,
                ramoPolizaReal, montoMovimiento
            );
            
        } catch (Exception e) {
            log.error("Error al registrar ajuste de reserva", e);
            throw new BusinessException("Error al registrar ajuste de reserva: " + e.getMessage());
        }
    }

    /**
     * Updates coverage reserve information.
     *
     * @param sucursalSiniestro Claim branch code
     * @param numeroSiniestro Claim number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param sucursalPoliza Policy branch code
     * @param ramoPoliza Policy insurance line code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param montoAjuste Adjustment amount
     * @throws BusinessException If update fails
     */
    @Transactional
    public void actualizarReservaCobertura(Long sucursalSiniestro, Long numeroSiniestro,
            Long ramoContable, String cobertura, Long sucursalPoliza, Long ramoPoliza,
            Long numeroPoliza, Long numeroCertificado, BigDecimal montoAjuste) {
        
        try {
            SiniestroReservaCobertura reserva = siniestroReservaCoberturaRepository
                .findBySucursalAndSiniestroAndRamoContableAndCoberturaAndPoliza(
                    sucursalSiniestro, numeroSiniestro, ramoContable, cobertura,
                    sucursalPoliza, ramoPoliza, numeroPoliza, numeroCertificado)
                .orElseThrow(() -> new BusinessException("Reserva de cobertura no encontrada"));
            
            // Calculate adjustment difference
            BigDecimal montoMovimiento = montoAjuste.subtract(reserva.getMtReserva().add(
                reserva.getMtAjustado() == null ? BigDecimal.ZERO : reserva.getMtAjustado()));
            
            // Update effective date and adjustment amount
            LocalDate fechaEfectiva = obtenerFechaEfectiva(
                sucursalPoliza, ramoPoliza, numeroPoliza, numeroCertificado,
                ramoContable, cobertura, reserva.getFeOcurrencia());
            
            reserva.setCarcFeEfectiva(fechaEfectiva);
            reserva.setMtAjustado(reserva.getMtAjustado().add(montoMovimiento));
            
            siniestroReservaCoberturaRepository.save(reserva);
            
        } catch (Exception e) {
            log.error("Error al actualizar reserva de cobertura", e);
            throw new BusinessException("Error al actualizar reserva de cobertura: " + e.getMessage());
        }
    }

    /**
     * Registers accounting entries for a reserve adjustment.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param numeroMovimiento Movement number
     * @param tipoMovimiento Movement type
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param ramoPoliza Policy insurance line code
     * @param montoMovimiento Movement amount
     * @throws BusinessException If registration fails
     */
    @Transactional
    public void registrarContabilidadReserva(Long sucursal, Long siniestro, Long numeroMovimiento,
            String tipoMovimiento, Long ramoContable, String cobertura, Long numeroPoliza,
            Long numeroCertificado, Long ramoPoliza, BigDecimal montoMovimiento) {
        
        // TODO-BUSINESS-LOGIC: Implement accounting entries registration
        // This requires detailed knowledge of the accounting structure and rules
        // 
        // 1. The original code uses a complex algorithm to generate accounting entries
        // 2. Test by verifying that appropriate accounting entries are created for each adjustment
        // 3. Ensure that debit and credit entries balance correctly
        
        log.warn("Accounting entries registration not fully implemented");
        
        // Generate accounting entry number
        Long numeroAsiento = obtenerNumeroAsientoContable(sucursal, siniestro);
        
        // Get claim type for accounting
        String tipoSiniestro = obtenerTipoSiniestro(ramoPoliza, ramoContable, cobertura, null);
        
        // Create accounting entries
        // This is a simplified version - the actual implementation would be more complex
        SiniestroMovimientoContable movimientoContable = new SiniestroMovimientoContable();
        movimientoContable.setNuSucursal(sucursal);
        movimientoContable.setNuSiniestro(siniestro);
        movimientoContable.setNuMovimiento(numeroMovimiento);
        movimientoContable.setTpMovto(tipoMovimiento);
        movimientoContable.setCarbCdRamo(ramoContable);
        movimientoContable.setCdCobertura(cobertura);
        movimientoContable.setCdSucursal(sucursal);
        movimientoContable.setCarpCdRamo(ramoPoliza);
        movimientoContable.setNuPoliza(numeroPoliza);
        movimientoContable.setNuCertificado(numeroCertificado);
        movimientoContable.setNuCompania(1L); // Default company
        movimientoContable.setMtHaber(montoMovimiento.compareTo(BigDecimal.ZERO) > 0 ? montoMovimiento : BigDecimal.ZERO);
        movimientoContable.setMtDebe(montoMovimiento.compareTo(BigDecimal.ZERO) < 0 ? montoMovimiento.abs() : BigDecimal.ZERO);
        movimientoContable.setNuDocumento(0L);
        movimientoContable.setFeMovimiento(LocalDate.now());
        movimientoContable.setFeContable(null);
        movimientoContable.setStContable("N");
        movimientoContable.setNuAsconta(numeroAsiento);
        
        siniestroMovimientoContableRepository.save(movimientoContable);
    }

    // Helper methods

    /**
     * Gets the description for a claim status.
     *
     * @param status Status code
     * @return Status description
     */
    private String getDescripcionEstado(Long status) {
        // TODO-BUSINESS-LOGIC: Implement status description lookup
        // This would typically come from a database table or enum
        
        switch (status.intValue()) {
            case 26: return "ARP";
            default: return "Estado " + status;
        }
    }

    /**
     * Calculates the pending sum assured for LUC.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @return Pending sum assured
     */
    private BigDecimal calcularSumaAseguradaPendiente(Long sucursal, Long ramo, Long poliza,
            Long certificado, LocalDate fechaOcurrencia, Long ramoContable) {
        
        // TODO-BUSINESS-LOGIC: Implement pending sum assured calculation for LUC
        // This requires detailed business rules about how to calculate remaining sum assured
        
        return BigDecimal.valueOf(100000); // Placeholder value
    }

    /**
     * Calculates the total payments for a policy.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @return Total payments
     */
    private BigDecimal calcularTotalPagos(Long sucursal, Long ramo, Long poliza,
            Long certificado, LocalDate fechaOcurrencia, Long ramoContable) {
        
        // TODO-BUSINESS-LOGIC: Implement total payments calculation
        // This would typically involve querying the database for all payments
        
        return BigDecimal.valueOf(10000); // Placeholder value
    }

    /**
     * Calculates the pending reserve for a policy.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param remesaId Batch ID
     * @return Pending reserve
     */
    private BigDecimal calcularReservaPendiente(Long sucursal, Long ramo, Long poliza,
            Long certificado, LocalDate fechaOcurrencia, Long ramoContable, Long remesaId) {
        
        // Get pending reserve from database
        BigDecimal reservaPendiente = siniestroReservaCoberturaRepository
            .calcularReservaPendiente(sucursal, ramo, poliza, certificado, fechaOcurrencia, ramoContable);
        
        // Add pending reserves from current batch
        BigDecimal reservaRemesa = sintTmpMasivoRepository
            .sumMontoByRemesa(remesaId);
        
        return reservaPendiente.add(reservaRemesa);
    }

    /**
     * Calculates the total sum assured for a policy.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @return Total sum assured
     */
    private BigDecimal calcularSumaAseguradaVigente(Long sucursal, Long ramo, Long poliza,
            Long certificado, LocalDate fechaOcurrencia) {
        
        // TODO-BUSINESS-LOGIC: Implement sum assured calculation
        // This would typically involve querying the database for the policy's sum assured
        
        return BigDecimal.valueOf(500000); // Placeholder value
    }

    /**
     * Gets the sum assured for a coverage.
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @return Sum assured
     */
    private BigDecimal obtenerSumaAsegurada(Long sucursal, Long ramo, Long poliza,
            Long certificado, LocalDate fechaOcurrencia, Long ramoContable, String cobertura) {
        
        // Get effective date
        LocalDate fechaEfectiva = obtenerFechaEfectiva(
            sucursal, ramo, poliza, certificado, ramoContable, cobertura, fechaOcurrencia);
        
        // Get sum assured from database
        BigDecimal sumaAsegurada = BigDecimal.ZERO;
        
        try {
            // Try to get from current table
            sumaAsegurada = certificadoRepository
                .findSumaAsegurada(sucursal, ramo, poliza, certificado, 
                    ramoContable, cobertura, fechaEfectiva);
        } catch (Exception e) {
            // Try to get from historical table
            try {
                sumaAsegurada = certificadoRepository
                    .findSumaAseguradaHistorica(sucursal, ramo, poliza, certificado, 
                        ramoContable, cobertura, fechaEfectiva);
            } catch (Exception ex) {
                log.error("Error al obtener suma asegurada", ex);
            }
        }
        
        // Check if this is a special product (IPS/IVS)
        String tipoProducto = obtenerTipoProducto(ramo, ramoContable, cobertura);
        
        if ("IPS".equals(tipoProducto) || "IVS".equals(tipoProducto)) {
            sumaAsegurada = calcularSumaAseguradaEspecial(
                sucursal, ramo, poliza, certificado, fechaOcurrencia, 
                ramoContable, cobertura, tipoProducto);
        }
        
        return sumaAsegurada;
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
    private LocalDate obtenerFechaEfectiva(Long sucursal, Long ramo, Long poliza,
            Long certificado, Long ramoContable, String cobertura, LocalDate fechaOcurrencia) {
        
        // Get the most recent effective date before occurrence date
        LocalDate fechaEfectiva = certificadoRepository
            .findFechaEfectiva(sucursal, ramo, poliza, certificado, 
                ramoContable, cobertura, fechaOcurrencia);
        
        if (fechaEfectiva == null) {
            // Try historical table
            fechaEfectiva = certificadoRepository
                .findFechaEfectivaHistorica(sucursal, ramo, poliza, certificado, 
                    ramoContable, cobertura, fechaOcurrencia);
        }
        
        return fechaEfectiva != null ? fechaEfectiva : fechaOcurrencia;
    }

    /**
     * Calculates the sum assured for special products (IPS/IVS).
     *
     * @param sucursal Branch code
     * @param ramo Insurance line code
     * @param poliza Policy number
     * @param certificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param tipoProducto Product type
     * @return Sum assured
     */
    private BigDecimal calcularSumaAseguradaEspecial(Long sucursal, Long ramo, Long poliza,
            Long certificado, LocalDate fechaOcurrencia, Long ramoContable, String cobertura,
            String tipoProducto) {
        
        // TODO-BUSINESS-LOGIC: Implement special sum assured calculation
        // This requires detailed business rules about how to calculate sum assured for IPS/IVS products
        
        return BigDecimal.valueOf(200000); // Placeholder value
    }

    /**
     * Gets the product type for a coverage.
     *
     * @param ramo Insurance line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @return Product type
     */
    private String obtenerTipoProducto(Long ramo, Long ramoContable, String cobertura) {
        return sintCobLucRepository.findTipoProducto(ramo, ramoContable, cobertura);
    }

    /**
     * Gets the next movement number for a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Movement number
     */
    private Long obtenerNumeroMovimiento(Long sucursal, Long siniestro) {
        // Check if there are existing movements
        Long count = siniestroReservaCoberturaRepository.countBySucursalAndSiniestro(sucursal, siniestro);
        
        if (count > 0) {
            // Get the maximum movement number and increment
            Long maxMovimiento = Math.max(
                siniestroMovimientoRepository.findMaxMovimientoBySucursalAndSiniestro(sucursal, siniestro),
                siniestroMovimientoCoberturaRepository.findMaxMovimientoBySucursalAndSiniestro(sucursal, siniestro)
            );
            
            return maxMovimiento + 1;
        } else {
            return 1L;
        }
    }

    /**
     * Gets the movement type based on claim status.
     *
     * @param estadoSiniestro Claim status
     * @return Movement type
     */
    private String obtenerTipoMovimiento(Long estadoSiniestro) {
        if (estadoSiniestro != null) {
            if (estadoSiniestro.equals(24L)) {
                return "RL"; // Liquidación
            } else if (estadoSiniestro.equals(25L)) {
                return "RX"; // Rechazo
            }
        }
        
        return "RA"; // Ajuste de Reserva (default)
    }

    /**
     * Gets the description for a movement type.
     *
     * @param tipoMovimiento Movement type
     * @return Movement type description
     */
    private String obtenerDescripcionTipoMovimiento(String tipoMovimiento) {
        // TODO-BUSINESS-LOGIC: Implement movement type description lookup
        // This would typically come from a database table
        
        switch (tipoMovimiento) {
            case "RA": return "Ajuste de Reserva";
            case "RL": return "Liquidación";
            case "RX": return "Rechazo";
            default: return tipoMovimiento;
        }
    }

    /**
     * Gets the currency for a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Currency code
     */
    private String obtenerMoneda(Long sucursal, Long siniestro) {
        try {
            return polizaRepository.findMonedaBySiniestro(sucursal, siniestro);
        } catch (Exception e) {
            log.error("Error al obtener moneda", e);
            return "01"; // Default currency
        }
    }

    /**
     * Calculates the movement amount for an adjustment.
     *
     * @param sucursalSiniestro Claim branch code
     * @param numeroSiniestro Claim number
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param sucursalPoliza Policy branch code
     * @param ramoPoliza Policy insurance line code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param montoAjuste Adjustment amount
     * @return Movement amount
     */
    private BigDecimal calcularMontoMovimiento(Long sucursalSiniestro, Long numeroSiniestro,
            Long ramoContable, String cobertura, Long sucursalPoliza, Long ramoPoliza,
            Long numeroPoliza, Long numeroCertificado, BigDecimal montoAjuste) {
        
        // Get current balance
        BigDecimal saldoActual = calcularSaldo(sucursalSiniestro, numeroSiniestro, ramoContable, cobertura);
        
        // Calculate difference
        return montoAjuste.subtract(saldoActual);
    }

    /**
     * Gets the current user.
     *
     * @return User name
     */
    private String obtenerUsuarioActual() {
        // In a real application, this would come from Spring Security
        return "SYSTEM";
    }

    /**
     * Gets the accepted notice based on claim status.
     *
     * @param estadoSiniestro Claim status
     * @return Accepted notice code
     */
    private String obtenerAvisoAceptado(Long estadoSiniestro) {
        if (estadoSiniestro != null && estadoSiniestro.equals(26L)) {
            return "CO";
        }
        return null;
    }

    /**
     * Gets the next accounting entry number for a claim.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @return Accounting entry number
     */
    private Long obtenerNumeroAsientoContable(Long sucursal, Long siniestro) {
        Long maxAsiento = siniestroMovimientoContableRepository
            .findMaxAsientoBySucursalAndSiniestro(sucursal, siniestro);
        
        return maxAsiento != null ? maxAsiento + 1 : 1L;
    }

    /**
     * Gets the claim type for accounting purposes.
     *
     * @param ramoPoliza Policy insurance line code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param marca Mark
     * @return Claim type
     */
    private String obtenerTipoSiniestro(Long ramoPoliza, Long ramoContable, String cobertura, String marca) {
        // TODO-BUSINESS-LOGIC: Implement claim type lookup
        // This would typically come from a database table
        
        return "01"; // Default claim type
    }

    /**
     * Gets the maximum indemnification factor for a line.
     *
     * @param ramo Insurance line code
     * @return Maximum indemnification factor
     */
    private Integer obtenerFactorMaximoIndemnizacion(Long ramo) {
        // TODO-BUSINESS-LOGIC: Implement factor lookup
        // This would typically come from a database table
        
        return 1; // Default factor
    }

    /**
     * Validates ACP coverage 003 reserve.
     *
     * @param sucursal Branch code
     * @param siniestro Claim number
     * @param sumaAsegurada Sum assured
     * @param montoReserva Reserve amount
     * @throws BusinessException If validation fails
     */
    private void validarReservaAcp(Long sucursal, Long siniestro, 
            BigDecimal sumaAsegurada, BigDecimal montoReserva) {
        
        // TODO-BUSINESS-LOGIC: Implement ACP validation
        // This requires detailed business rules about ACP coverage
        
        // Get partial days
        Integer diasParcial = 365; // Default
        try {
            diasParcial = siniestroRepository.findDiasParcialAcp(sucursal, siniestro);
        } catch (Exception e) {
            log.warn("No se encontraron días parciales para ACP", e);
        }
        
        // Calculate maximum reserve
        BigDecimal montoMaximoReserva = sumaAsegurada.divide(BigDecimal.valueOf(7))
            .multiply(BigDecimal.valueOf(diasParcial));
        
        // Validate
        if (montoReserva.compareTo(montoMaximoReserva) > 0) {
            throw new BusinessException("El Monto de Reserva mayor a la Suma Asegurada X # dias parcial");
        }
    }

    /**
     * Checks if a line is LUC (Límite Único Combinado).
     *
     * @param ramo Insurance line code
     * @return true if LUC
     */
    private boolean esRamoLuc(Long ramo) {
        // TODO-BUSINESS-LOGIC: Implement LUC check
        // This would typically come from a database table or configuration
        
        return false; // Placeholder
    }

    /**
     * Validates LUC adjustment amount.
     *
     * @param sucursal Branch code
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param ramoPoliza Policy insurance line code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param montoAjuste Adjustment amount
     * @throws BusinessException If validation fails
     */
    private void validarMontoAjusteLuc(Long sucursal, Long ramoContable, String cobertura,
            Long ramoPoliza, Long numeroPoliza, Long numeroCertificado,
            LocalDate fechaOcurrencia, BigDecimal montoAjuste) {
        
        // TODO-BUSINESS-LOGIC: Implement LUC validation
        // This requires detailed business rules about LUC adjustments
        
        log.warn("LUC validation not fully implemented");
    }

    /**
     * Validates cause of death for life insurance.
     *
     * @param sucursal Branch code
     * @param ramoPoliza Policy insurance line code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param tipoMovimiento Movement type
     * @param montoAjuste Adjustment amount
     * @throws BusinessException If validation fails
     */
    private void validarCausaFallecimiento(Long sucursal, Long ramoPoliza, Long numeroPoliza,
            Long numeroCertificado, LocalDate fechaOcurrencia, Long ramoContable,
            String cobertura, String tipoMovimiento, BigDecimal montoAjuste) {
        
        // TODO-BUSINESS-LOGIC: Implement cause of death validation
        // This requires detailed business rules about life insurance claims
        
        log.warn("Cause of death validation not fully implemented");
    }

    /**
     * Checks if a line is Blindaje Plus.
     *
     * @param ramo Insurance line code
     * @return true if Blindaje Plus
     */
    private boolean esRamoBlindajePlus(Long ramo) {
        // TODO-BUSINESS-LOGIC: Implement Blindaje Plus check
        // This would typically come from a database table or configuration
        
        return false; // Placeholder
    }

    /**
     * Validates Blindaje Plus adjustment.
     *
     * @param sucursal Branch code
     * @param ramoPoliza Policy insurance line code
     * @param numeroPoliza Policy number
     * @param numeroCertificado Certificate number
     * @param fechaOcurrencia Occurrence date
     * @param ramoContable Accounting line code
     * @param cobertura Coverage code
     * @param montoAjuste Adjustment amount
     * @param saldoActual Current balance
     * @throws BusinessException If validation fails
     */
    private void validarAjusteBlindajePlus(Long sucursal, Long ramoPoliza, Long numeroPoliza,
            Long numeroCertificado, LocalDate fechaOcurrencia, Long ramoContable,
            String cobertura, BigDecimal montoAjuste, BigDecimal saldoActual) {
        
        // TODO-BUSINESS-LOGIC: Implement Blindaje Plus validation
        // This requires detailed business rules about Blindaje Plus adjustments
        
        log.warn("Blindaje Plus validation not fully implemented");
    }
}