package com.yourcompany.yourapp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Siniestro entity representing insurance claims.
 * This class is converted from Oracle Forms SINF501041_fmb.xml
 * and handles insurance claim adjustments and reservations.
 */
@Entity
@Table(name = "SINT_SINIESTROS")
@Data
@Slf4j
@Component
public class Siniestro {

    @Id
    @Column(name = "SISI_NU_SINIESTRO")
    private Long numeroSiniestro;

    @Column(name = "SISI_CASU_CD_SUCURSAL")
    private Long codigoSucursal;

    @Column(name = "SISI_SICS_CARP_CD_RAMO")
    private Long codigoRamo;

    @Column(name = "SISI_FE_OCURRENCIA")
    private LocalDate fechaOcurrencia;

    @Column(name = "SISI_ST_SINIESTRO")
    private Integer estadoSiniestro;

    @Column(name = "SISI_DST_SINIESTRO")
    private String descripcionEstadoSiniestro;

    @Column(name = "DSP_CACN_NM_APELLIDO_RAZON")
    private String nombreAsegurado;

    // Relationships
    @OneToMany(mappedBy = "siniestro", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SiniestroCertificado> certificados = new ArrayList<>();

    @OneToMany(mappedBy = "siniestro", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SiniestroCobertura> coberturas = new ArrayList<>();

    @OneToMany(mappedBy = "siniestro", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SiniestroMovimiento> movimientos = new ArrayList<>();

    // Services and repositories injections
    @Autowired
    private SiniestroRepository siniestroRepository;
    
    @Autowired
    private SiniestroCoberturaRepository siniestroCoberturaRepository;
    
    @Autowired
    private SiniestroMovimientoRepository siniestroMovimientoRepository;
    
    @Autowired
    private SiniestroCertificadoRepository siniestroCertificadoRepository;
    
    @Autowired
    private UtileriasService utileriasService;
    
    @Autowired
    private AjusteReservaService ajusteReservaService;

    /**
     * Default constructor
     */
    public Siniestro() {
    }

    /**
     * Constructor with primary key
     * 
     * @param codigoSucursal the branch code
     * @param codigoRamo the line of business code
     * @param numeroSiniestro the claim number
     */
    public Siniestro(Long codigoSucursal, Long codigoRamo, Long numeroSiniestro) {
        this.codigoSucursal = codigoSucursal;
        this.codigoRamo = codigoRamo;
        this.numeroSiniestro = numeroSiniestro;
    }

    /**
     * Loads claim information including policy details and coverages.
     * Equivalent to PKG_AJUSTE_RESERVA.pInfoSiniestro in the original Forms code.
     * 
     * @param sucursal branch code
     * @param ramo line of business code
     * @param siniestro claim number
     * @return the loaded Siniestro object
     */
    @Transactional(readOnly = true)
    public Siniestro cargarInformacionSiniestro(Long sucursal, Long ramo, Long siniestro) {
        log.info("Cargando información del siniestro: {}/{}/{}", sucursal, ramo, siniestro);
        
        // Load basic claim information
        Optional<Siniestro> siniestroOpt = siniestroRepository.findBySucursalRamoAndNumero(
                sucursal, ramo, siniestro);
        
        if (!siniestroOpt.isPresent()) {
            log.error("No se encontró el siniestro: {}/{}/{}", sucursal, ramo, siniestro);
            throw new SiniestroNotFoundException("Siniestro no encontrado");
        }
        
        Siniestro siniestroEntity = siniestroOpt.get();
        
        // Load policy information
        List<SiniestroCertificado> certificados = siniestroCertificadoRepository
                .findBySiniestro(siniestroEntity);
        siniestroEntity.setCertificados(certificados);
        
        // Load coverages with their current balances
        List<SiniestroCobertura> coberturas = siniestroCoberturaRepository
                .findBySiniestro(siniestroEntity);
        
        // Calculate balances for each coverage
        for (SiniestroCobertura cobertura : coberturas) {
            BigDecimal saldo = utileriasService.calcularSaldo(
                    sucursal, 
                    siniestro, 
                    cobertura.getRamoContable(), 
                    cobertura.getCodigoCobertura(),
                    cobertura.getSucursalPoliza(),
                    cobertura.getRamoPoliza(),
                    cobertura.getNumeroPoliza(),
                    cobertura.getNumeroCertificado());
            
            cobertura.setSaldo(saldo);
            
            // Calculate sum insured
            BigDecimal sumaAsegurada = utileriasService.obtenerSumaAsegurada(
                    cobertura.getSucursalPoliza(),
                    cobertura.getRamoPoliza(),
                    cobertura.getNumeroPoliza(),
                    cobertura.getNumeroCertificado(),
                    cobertura.getRamoContable(),
                    cobertura.getCodigoCobertura(),
                    siniestroEntity.getFechaOcurrencia());
            
            cobertura.setSumaAsegurada(sumaAsegurada);
        }
        
        siniestroEntity.setCoberturas(coberturas);
        
        // Calculate pending sum insured for priority
        BigDecimal sumaAseguradaPendiente = ajusteReservaService.calcularReservaPendiente(
                sucursal,
                ramo,
                certificados.get(0).getNumeroPoliza(),
                certificados.get(0).getNumeroCertificado(),
                siniestroEntity.getFechaOcurrencia(),
                10L); // Default ramo contable 10
        
        // Store in context
        ajusteReservaService.setSumaAseguradaPendiente(sumaAseguradaPendiente);
        
        return siniestroEntity;
    }

    /**
     * Validates and processes reserve adjustments for the claim's coverages.
     * Equivalent to the KEY-COMMIT trigger in the original Forms code.
     * 
     * @return true if the adjustment was successful, false otherwise
     */
    @Transactional
    public boolean procesarAjusteReservas() {
        log.info("Procesando ajuste de reservas para siniestro: {}", this.numeroSiniestro);
        
        boolean exito = true;
        String mensaje = null;
        
        // Validate if there are adjustments to apply
        if (!ajusteReservaService.validarAjuste()) {
            log.error("No existen ajustes de reserva por aplicar");
            return false;
        }
        
        // Process each coverage adjustment
        for (SiniestroCobertura cobertura : this.coberturas) {
            if (cobertura.getMontoAjuste() != null) {
                boolean resultado = ajusteReservaService.generarAjusteReserva(
                        this.codigoSucursal,
                        this.numeroSiniestro,
                        cobertura.getRamoContable(),
                        cobertura.getCodigoCobertura(),
                        cobertura.getRamoPoliza(),
                        cobertura.getNumeroPoliza(),
                        cobertura.getNumeroCertificado());
                
                if (!resultado) {
                    exito = false;
                    mensaje = cobertura.getMensajeValidacion();
                    log.error("Error al procesar ajuste para cobertura: {} - {}", 
                            cobertura.getCodigoCobertura(), mensaje);
                    break;
                }
            }
        }
        
        if (exito) {
            String tipoMovimiento = utileriasService.obtenerTipoMovimiento();
            String descripcionMovimiento = utileriasService.obtenerDescripcionTipoMovimiento(tipoMovimiento);
            log.info("Se realizó el movimiento de {} correctamente", descripcionMovimiento);
        }
        
        return exito;
    }

    /**
     * Validates adjustments for a coverage.
     * Equivalent to PKG_AJUSTE_RESERVA.fAjusteReserva in the original Forms code.
     * 
     * @param cobertura the coverage to validate
     * @param validarSoloMonto if true, only validates the amount
     * @return true if the adjustment is valid, false otherwise
     */
    public boolean validarAjusteReserva(SiniestroCobertura cobertura, boolean validarSoloMonto) {
        log.info("Validando ajuste de reserva para cobertura: {}", cobertura.getCodigoCobertura());
        
        BigDecimal saldo = cobertura.getSaldo();
        BigDecimal montoAjuste = cobertura.getMontoAjuste();
        BigDecimal sumaAsegurada = cobertura.getSumaAsegurada();
        
        // Validate adjustment amount
        if (montoAjuste == null) {
            cobertura.setMensajeValidacion("Debe indicar el monto de ajuste");
            return false;
        }
        
        // Check if the claim status is 26 (special case)
        if (this.estadoSiniestro == 26) {
            if (montoAjuste.compareTo(BigDecimal.ZERO) < 0) {
                cobertura.setMensajeValidacion("Error: No es permitido ingresar montos negativos en el ajuste");
                return false;
            }
            
            if (montoAjuste.compareTo(BigDecimal.ZERO) == 0 && 
                    !ajusteReservaService.confirmarAjusteCero()) {
                return false;
            }
            
            if (saldo.compareTo(montoAjuste) == 0) {
                cobertura.setMensajeValidacion("Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: " + 
                        montoAjuste + " Saldo: " + saldo);
                return false;
            }
        } else {
            // For other statuses
            if (validarSoloMonto) {
                if (montoAjuste.compareTo(BigDecimal.ZERO) < 0) {
                    cobertura.setMensajeValidacion("Error: No es permitido ingresar montos negativos en el ajuste");
                    return false;
                }
                
                if (montoAjuste.compareTo(BigDecimal.ZERO) == 0 && 
                        !ajusteReservaService.confirmarAjusteCero()) {
                    return false;
                }
                
                if (montoAjuste.compareTo(BigDecimal.ZERO) < 0 && 
                        montoAjuste.abs().compareTo(saldo) > 0) {
                    cobertura.setMensajeValidacion("Error: El ajuste de menos no debe ser mayor al saldo de la reserva");
                    return false;
                }
            }
            
            if (saldo.compareTo(montoAjuste) == 0) {
                cobertura.setMensajeValidacion("Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: " + 
                        montoAjuste + " Saldo: " + saldo);
                return false;
            }
        }
        
        // Validate sum insured
        if (sumaAsegurada.compareTo(BigDecimal.ZERO) == 0) {
            cobertura.setMensajeValidacion("Advertencia: Monto de suma asegurada es igual a cero");
            return false;
        }
        
        // Validate that the adjustment doesn't exceed the sum insured
        if (cobertura.getIndicadorValidaMonto() != null && 
                cobertura.getIndicadorValidaMonto().equals("S")) {
            
            BigDecimal nuevoSaldo = cobertura.calcularNuevoSaldo();
            
            if (nuevoSaldo.compareTo(sumaAsegurada) > 0) {
                // Get max days multiplier for the line of business
                int diasMaximos = utileriasService.obtenerDiasMaximos(cobertura.getRamoPoliza());
                
                if (this.estadoSiniestro != 26 && 
                        nuevoSaldo.compareTo(sumaAsegurada.multiply(BigDecimal.valueOf(diasMaximos))) > 0) {
                    cobertura.setMensajeValidacion("Monto de la reserva debe ser menor o igual que la suma asegurada que es de " + 
                            sumaAsegurada.multiply(BigDecimal.valueOf(diasMaximos)));
                    return false;
                }
            }
        }
        
        // Validate special case for ACP coverage 003
        if (cobertura.getRamoContable() == 3 && "003".equals(cobertura.getCodigoCobertura())) {
            // TODO-BUSINESS-LOGIC: Implement special validation for ACP coverage 003
            // This requires retrieving the ACPT_SINIESTROS.ACSI_NU_DIAS_PARCIAL value
            // and calculating the maximum reserve amount based on the formula:
            // maxReserveAmount = (sumaAsegurada/7) * totalDays
            // Test by creating an adjustment for coverage 003 and verify the validation works
        }
        
        // Validate LUC (Límite Único de Cobertura) if applicable
        if (utileriasService.esRamoLUC(cobertura.getRamoPoliza())) {
            boolean validacionLUC = utileriasService.validarRamoLUC(
                    this.codigoSucursal,
                    this.numeroSiniestro,
                    cobertura.getRamoContable(),
                    cobertura.getCodigoCobertura(),
                    cobertura.getRamoPoliza(),
                    this.estadoSiniestro,
                    "RA", // Tipo movimiento
                    montoAjuste);
            
            if (!validacionLUC) {
                cobertura.setMensajeValidacion("No se generará Reserva de Cobertura por LUC");
                return false;
            }
        }
        
        // Validate cause of death for life insurance (ramo 13)
        if (cobertura.getRamoPoliza() == 13) {
            boolean validacionCausaFallecimiento = ajusteReservaService.validarCausaFallecimiento(
                    cobertura.getSucursalPoliza(),
                    cobertura.getRamoPoliza(),
                    cobertura.getNumeroPoliza(),
                    cobertura.getNumeroCertificado(),
                    this.fechaOcurrencia,
                    cobertura.getRamoContable(),
                    cobertura.getCodigoCobertura(),
                    "RA", // Tipo movimiento
                    montoAjuste);
            
            if (!validacionCausaFallecimiento) {
                // Message is set by the validation method
                return false;
            }
        }
        
        return true;
    }

    /**
     * Calculates priorities for adjustments in life insurance (ramo 13).
     * Equivalent to PKG_AJUSTE_RESERVA.P_Ajuste_Prioridad_R in the original Forms code.
     * 
     * @return true if the calculation was successful, false otherwise
     */
    @Transactional
    public boolean calcularPrioridadAjustes() {
        log.info("Calculando prioridad de ajustes para siniestro: {}", this.numeroSiniestro);
        
        // Only applicable for life insurance (ramo 13)
        if (this.codigoRamo != 13) {
            log.warn("Cálculo de prioridad solo aplica para ramo 13 (Vida)");
            return false;
        }
        
        // Get the first certificate
        if (this.certificados.isEmpty()) {
            log.error("No hay certificados asociados al siniestro");
            return false;
        }
        
        SiniestroCertificado certificado = this.certificados.get(0);
        
        // Calculate pending sum insured
        BigDecimal reservaPendiente = ajusteReservaService.calcularReservaPendiente(
                certificado.getSucursal(),
                certificado.getRamo(),
                certificado.getNumeroPoliza(),
                certificado.getNumeroCertificado(),
                this.fechaOcurrencia,
                10L); // Default ramo contable 10
        
        // Calculate total payments
        BigDecimal totalPagos = ajusteReservaService.calcularTotalPagos(
                certificado.getSucursal(),
                certificado.getRamo(),
                certificado.getNumeroPoliza(),
                certificado.getNumeroCertificado(),
                this.fechaOcurrencia,
                10L); // Default ramo contable 10
        
        // Get total sum insured
        BigDecimal sumaAseguradaTotal = ajusteReservaService.obtenerSumaAseguradaVigente(
                certificado.getSucursal(),
                certificado.getRamo(),
                certificado.getNumeroPoliza(),
                certificado.getNumeroCertificado(),
                this.fechaOcurrencia);
        
        // Calculate global balance
        BigDecimal saldoGlobal = sumaAseguradaTotal.subtract(
                totalPagos.add(reservaPendiente.subtract(totalPagos).abs()));
        
        // Process each coverage by priority
        BigDecimal saldoGlobalRestante = saldoGlobal;
        BigDecimal saldoAcumulado = BigDecimal.ZERO;
        
        // First pass: process coverages with priority
        for (SiniestroCobertura cobertura : this.coberturas) {
            if (cobertura.getPrioridad() != null && cobertura.getMontoAjusteMovimiento() != null) {
                // Mark coverage as being processed for priority
                cobertura.setMarcaValidacion("P");
                
                if (cobertura.getMontoAjusteMovimiento().compareTo(BigDecimal.ZERO) > 0 &&
                        cobertura.getMontoAjusteMovimiento().compareTo(saldoGlobalRestante) > 0) {
                    
                    // Calculate shortage
                    BigDecimal faltante = cobertura.getMontoAjusteMovimiento().subtract(saldoGlobalRestante);
                    
                    // Process first coverages with lower priority
                    BigDecimal montoAjuste = cobertura.getMontoAjuste();
                    BigDecimal saldoOcupado = BigDecimal.ZERO;
                    
                    ajusteReservaService.buscarPrimerosConPrioridad(
                            cobertura.getPrioridad(),
                            montoAjuste,
                            saldoOcupado);
                    
                    // Mark as processed
                    cobertura.setMarcaValidacion("N");
                    
                    // Update accumulated balance
                    saldoAcumulado = saldoAcumulado.add(saldoOcupado);
                    
                    // Process last coverages with higher priority
                    ajusteReservaService.buscarUltimosConPrioridad(
                            faltante,
                            cobertura.getPrioridad(),
                            saldoAcumulado);
                    
                    // Update remaining global balance
                    saldoGlobalRestante = saldoGlobalRestante.subtract(cobertura.getMontoAjusteMovimiento());
                    
                    // Update accumulated balance
                    saldoAcumulado = saldoAcumulado.add(
                            cobertura.getNuevoSaldo() != null ? cobertura.getNuevoSaldo() : BigDecimal.ZERO);
                    
                } else if (cobertura.getMontoAjusteMovimiento().compareTo(BigDecimal.ZERO) > 0 && 
                        cobertura.getMontoAjusteMovimiento().compareTo(saldoGlobalRestante) <= 0) {
                    
                    // Valid adjustment within limits
                    cobertura.setMarcaValidacion("N");
                    
                    // Update remaining global balance
                    saldoGlobalRestante = saldoGlobalRestante.subtract(cobertura.getMontoAjusteMovimiento());
                    
                    // Update accumulated balance
                    saldoAcumulado = saldoAcumulado.add(
                            cobertura.getNuevoSaldo() != null ? cobertura.getNuevoSaldo() : BigDecimal.ZERO);
                    
                } else if (cobertura.getMontoAjusteMovimiento() != null && 
                        cobertura.getMontoAjusteMovimiento().compareTo(BigDecimal.ZERO) < 0) {
                    
                    // Negative adjustment
                    cobertura.setMarcaValidacion("N");
                }
            }
        }
        
        // Second pass: validate remaining coverages with priority
        for (SiniestroCobertura cobertura : this.coberturas) {
            if ("P".equals(cobertura.getMarcaValidacion())) {
                // Validate with sum insured priority
                BigDecimal montoSA = ajusteReservaService.validarSumaPrioridad(
                        certificado.getSucursal(),
                        certificado.getRamo(),
                        certificado.getNumeroPoliza(),
                        certificado.getNumeroCertificado(),
                        this.fechaOcurrencia,
                        cobertura.getRamoContable(),
                        cobertura.getCodigoCobertura(),
                        cobertura.getMontoAjusteMovimiento());
                
                if (montoSA.compareTo(BigDecimal.ZERO) >= 0) {
                    cobertura.setMarcaValidacion("N");
                }
            } else if (cobertura.getMontoAjuste() != null) {
                // Mark non-priority coverages with adjustments
                cobertura.setMarcaValidacion("N");
            }
        }
        
        return true;
    }

    /**
     * Entity class for claim certificates
     */
    @Entity
    @Table(name = "SINT_CERTIFICADOS_SINIESTROS")
    @Data
    public static class SiniestroCertificado {
        
        @Id
        @Column(name = "SICE_SISI_NU_SINIESTRO")
        private Long numeroSiniestro;
        
        @Column(name = "SICE_SISI_CASU_CD_SUCURSAL")
        private Long sucursal;
        
        @Column(name = "SICE_CACE_CARP_CD_RAMO")
        private Long ramo;
        
        @Column(name = "SICE_CACE_CAPO_NU_POLIZA")
        private Long numeroPoliza;
        
        @Column(name = "SICE_CACE_NU_CERTIFICADO")
        private Long numeroCertificado;
        
        @Column(name = "SICE_NU_BENEFICIARIO")
        private Long numeroBeneficiario;
        
        @ManyToOne
        @JoinColumn(name = "SICE_SISI_NU_SINIESTRO", insertable = false, updatable = false)
        private Siniestro siniestro;
    }

    /**
     * Entity class for claim coverages
     */
    @Entity
    @Table(name = "SINT_RESERVA_COBERTURA_CERTIFI")
    @Data
    public static class SiniestroCobertura {
        
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        
        @Column(name = "SICC_SISI_CASU_CD_SUCURSA")
        private Long sucursalSiniestro;
        
        @Column(name = "SICC_SICO_SISI_NU_SINIESTRO")
        private Long numeroSiniestro;
        
        @Column(name = "SICC_SICO_CACB_CARB_CD_RAMO")
        private Long ramoContable;
        
        @Column(name = "SICC_SICO_CACB_CD_COBERTURA")
        private String codigoCobertura;
        
        @Column(name = "SICC_CACE_CASU_CD_SUCURSA")
        private Long sucursalPoliza;
        
        @Column(name = "SICC_SICE_CACE_CARP_CD_RAMO")
        private Long ramoPoliza;
        
        @Column(name = "SICC_CACE_CAPO_NU_POLIZA")
        private Long numeroPoliza;
        
        @Column(name = "SICC_CACE_NU_CERTIFICADO")
        private Long numeroCertificado;
        
        @Column(name = "SICC_CARC_NU_ASEGURADO")
        private Long numeroAsegurado;
        
        @Column(name = "SICC_MT_SUMASEG")
        private BigDecimal sumaAsegurada;
        
        @Column(name = "SICC_MT_RESERVA")
        private BigDecimal montoReserva;
        
        @Column(name = "SICC_MT_AJUSTADO")
        private BigDecimal montoAjustado;
        
        @Column(name = "SICC_MT_LIQUIDACION")
        private BigDecimal montoLiquidacion;
        
        @Column(name = "SICC_MT_RECHAZO")
        private BigDecimal montoRechazo;
        
        @Column(name = "SICC_MT_PAGO")
        private BigDecimal montoPago;
        
        @Column(name = "SICC_CARC_FE_EFECTIVA")
        private LocalDate fechaEfectiva;
        
        @Column(name = "SICC_MT_DEDUCIBLE")
        private BigDecimal montoDeducible;
        
        @Transient
        private BigDecimal saldo;
        
        @Transient
        private BigDecimal montoAjuste;
        
        @Transient
        private BigDecimal nuevoSaldo;
        
        @Transient
        private BigDecimal montoAjusteMovimiento;
        
        @Transient
        private Integer prioridad;
        
        @Transient
        private String marcaValidacion;
        
        @Transient
        private String mensajeValidacion;
        
        @Transient
        private String indicadorValidaMonto;
        
        @ManyToOne
        @JoinColumn(name = "SICC_SICO_SISI_NU_SINIESTRO", insertable = false, updatable = false)
        private Siniestro siniestro;
        
        /**
         * Calculates the new balance after applying the adjustment
         * 
         * @return the new balance
         */
        public BigDecimal calcularNuevoSaldo() {
            if (this.montoAjuste == null) {
                return this.saldo;
            }
            return this.montoAjuste;
        }
    }

    /**
     * Entity class for claim movements
     */
    @Entity
    @Table(name = "SINT_MOVIMIENTO_SINIESTROS")
    @Data
    public static class SiniestroMovimiento {
        
        @Id
        @Column(name = "SIMS_SISI_NU_SINIESTRO")
        private Long numeroSiniestro;
        
        @Column(name = "SIMS_SISI_CASU_CD_SUCURSAL")
        private Long sucursal;
        
        @Column(name = "SIMS_NU_MOVIMIENTO")
        private Long numeroMovimiento;
        
        @Column(name = "SIMS_FE_MOVIMIENTO")
        private LocalDate fechaMovimiento;
        
        @Column(name = "SIMS_TP_MOVIMIENTO")
        private String tipoMovimiento;
        
        @Column(name = "SIMS_CAAN_CD_ANALISTA")
        private String codigoAnalista;
        
        @Column(name = "SIMS_MT_MOVIMIENTO")
        private BigDecimal montoMovimiento;
        
        @Column(name = "SIMS_CAMO_CD_MONEDA")
        private String codigoMoneda;
        
        @Column(name = "SIMS_CARB_CD_RAMO")
        private Long codigoRamoContable;
        
        @Column(name = "SIMS_CACB_CD_COBERTURA")
        private String codigoCobertura;
        
        @Column(name = "SIMS_NU_AVISO_ACEPTADO")
        private String numeroAvisoAceptado;
        
        @ManyToOne
        @JoinColumn(name = "SIMS_SISI_NU_SINIESTRO", insertable = false, updatable = false)
        private Siniestro siniestro;
    }

    /**
     * Interface for Siniestro repository
     */
    @Repository
    public interface SiniestroRepository extends JpaRepository<Siniestro, Long> {
        
        @Query("SELECT s FROM Siniestro s WHERE s.codigoSucursal = :sucursal AND s.codigoRamo = :ramo AND s.numeroSiniestro = :numero")
        Optional<Siniestro> findBySucursalRamoAndNumero(
                @Param("sucursal") Long sucursal, 
                @Param("ramo") Long ramo, 
                @Param("numero") Long numero);
    }

    /**
     * Interface for SiniestroCertificado repository
     */
    @Repository
    public interface SiniestroCertificadoRepository extends JpaRepository<SiniestroCertificado, Long> {
        
        List<SiniestroCertificado> findBySiniestro(Siniestro siniestro);
    }

    /**
     * Interface for SiniestroCobertura repository
     */
    @Repository
    public interface SiniestroCoberturaRepository extends JpaRepository<SiniestroCobertura, Long> {
        
        List<SiniestroCobertura> findBySiniestro(Siniestro siniestro);
    }

    /**
     * Interface for SiniestroMovimiento repository
     */
    @Repository
    public interface SiniestroMovimientoRepository extends JpaRepository<SiniestroMovimiento, Long> {
        
        @Query("SELECT MAX(m.numeroMovimiento) FROM SiniestroMovimiento m WHERE m.sucursal = :sucursal AND m.numeroSiniestro = :siniestro")
        Optional<Long> findMaxNumeroMovimiento(@Param("sucursal") Long sucursal, @Param("siniestro") Long siniestro);
    }

    /**
     * Service for utilities functions
     */
    @Service
    @Slf4j
    public static class UtileriasService {
        
        @Autowired
        private JdbcTemplate jdbcTemplate;
        
        /**
         * Calculates the balance for a coverage
         * Equivalent to PKG_UTILERIAS.fCalculaSaldo in the original Forms code
         */
        public BigDecimal calcularSaldo(Long canal, Long siniestro, Long ramoCont, String cobertura,
                                        Long sucursal, Long ramo, Long poliza, Long certificado) {
            
            String sql = "SELECT (NVL(SICC_MT_RESERVA,0) + NVL(SICC_MT_AJUSTADO,0)) - " +
                    "(NVL(SICC_MT_LIQUIDACION,0) - NVL(SICC_MT_PAGO,0)) + NVL(SICC_MT_RECHAZO,0) AS SALDO " +
                    "FROM SINT_RESERVA_COBERTURA_CERTIFI " +
                    "WHERE SICC_SISI_CASU_CD_SUCURSA = ? " +
                    "AND SICC_SICO_SISI_NU_SINIESTRO = ? " +
                    "AND SICC_SICO_CACB_CARB_CD_RAMO = ? " +
                    "AND SICC_SICO_CACB_CD_COBERTURA = ? " +
                    "AND SICC_CACE_CASU_CD_SUCURSA = ? " +
                    "AND SICC_SICE_CACE_CARP_CD_RAMO = ? " +
                    "AND SICC_CACE_CAPO_NU_POLIZA = ? " +
                    "AND SICC_CACE_NU_CERTIFICADO = ?";
            
            try {
                return jdbcTemplate.queryForObject(sql, BigDecimal.class, 
                        canal, siniestro, ramoCont, cobertura, sucursal, ramo, poliza, certificado);
            } catch (EmptyResultDataAccessException e) {
                return BigDecimal.ZERO;
            }
        }
        
        /**
         * Gets the sum insured for a coverage
         * Equivalent to PKG_UTILERIAS.fObtenerSumaAsegurada in the original Forms code
         */
        public BigDecimal obtenerSumaAsegurada(Long sucursal, Long ramo, Long poliza, Long certificado,
                                              Long ramoCont, String cobertura, LocalDate fechaOcurrencia) {
            
            // Get effective date
            LocalDate fechaEfectiva = obtenerFechaEfectiva(sucursal, ramo, poliza, certificado, 
                    ramoCont, cobertura, fechaOcurrencia);
            
            // Special case for transport lines (15, 41)
            if (ramo == 15 || ramo == 41) {
                String sql = "SELECT MAX(CARC_MT_SUMA_ASEGURADA) " +
                        "FROM CARH_RIESGOS_CUBIERTOS " +
                        "WHERE CARC_CASU_CD_SUCURSAL = ? " +
                        "AND CARC_CARP_CD_RAMO = ? " +
                        "AND CARC_CAPO_NU_POLIZA = ? " +
                        "AND CARC_CACE_NU_CERTIFICADO = ? " +
                        "AND TRUNC(CARC_FE_EFECTIVA) = ?";
                
                try {
                    return jdbcTemplate.queryForObject(sql, BigDecimal.class, 
                            sucursal, ramo, poliza, certificado, fechaEfectiva);
                } catch (EmptyResultDataAccessException e) {
                    return BigDecimal.ZERO;
                }
            } else {
                // Regular case
                String sql = "SELECT NVL(CARC_MT_SUMA_ASEGURADA,0) " +
                        "FROM CART_RIESGOS_CUBIERTOS " +
                        "WHERE CARC_CASU_CD_SUCURSAL = ? " +
                        "AND CARC_CARP_CD_RAMO = ? " +
                        "AND CARC_CAPO_NU_POLIZA = ? " +
                        "AND CARC_CACE_NU_CERTIFICADO = ? " +
                        "AND CARC_CARB_CD_RAMO = ? " +
                        "AND CARC_CACB_CD_COBERTURA = ? " +
                        "AND TRUNC(CARC_FE_EFECTIVA) = ? " +
                        "UNION " +
                        "SELECT NVL(CARC_MT_SUMA_ASEGURADA,0) " +
                        "FROM CARH_RIESGOS_CUBIERTOS " +
                        "WHERE CARC_CASU_CD_SUCURSAL = ? " +
                        "AND CARC_CARP_CD_RAMO = ? " +
                        "AND CARC_CAPO_NU_POLIZA = ? " +
                        "AND CARC_CACE_NU_CERTIFICADO = ? " +
                        "AND CARC_CARB_CD_RAMO = ? " +
                        "AND CARC_CACB_CD_COBERTURA = ? " +
                        "AND TRUNC(CARC_FE_EFECTIVA) = ? " +
                        "AND 0 = (SELECT COUNT(*) FROM CART_RIESGOS_CUBIERTOS " +
                        "        WHERE CARC_CASU_CD_SUCURSAL = ? " +
                        "        AND CARC_CARP_CD_RAMO = ? " +
                        "        AND CARC_CAPO_NU_POLIZA = ? " +
                        "        AND CARC_CACE_NU_CERTIFICADO = ? " +
                        "        AND TRUNC(CARC_FE_EFECTIVA) <= ?)";
                
                try {
                    return jdbcTemplate.queryForObject(sql, BigDecimal.class, 
                            sucursal, ramo, poliza, certificado, ramoCont, cobertura, fechaEfectiva,
                            sucursal, ramo, poliza, certificado, ramoCont, cobertura, fechaEfectiva,
                            sucursal, ramo, poliza, certificado, fechaEfectiva);
                } catch (EmptyResultDataAccessException e) {
                    return BigDecimal.ZERO;
                }
            }
        }
        
        /**
         * Gets the effective date for a coverage
         */
        private LocalDate obtenerFechaEfectiva(Long sucursal, Long ramo, Long poliza, Long certificado,
                                              Long ramoCont, String cobertura, LocalDate fechaOcurrencia) {
            
            String sql = "SELECT MAX(FECHAS.FECHA) AS FECHA " +
                    "FROM ( " +
                    "    SELECT TRUNC(MAX(CARC_FE_EFECTIVA)) AS FECHA " +
                    "    FROM CARH_RIESGOS_CUBIERTOS " +
                    "    WHERE CARC_CASU_CD_SUCURSAL = ? " +
                    "    AND CARC_CARP_CD_RAMO = ? " +
                    "    AND CARC_CAPO_NU_POLIZA = ? " +
                    "    AND CARC_CACE_NU_CERTIFICADO = ? " +
                    "    AND TRUNC(CARC_FE_EFECTIVA) <= ? " +
                    "    AND CARC_CARB_CD_RAMO = ? " +
                    "    AND CARC_CACB_CD_COBERTURA = ? " +
                    "    UNION " +
                    "    SELECT TRUNC(MAX(CARC_FE_EFECTIVA)) AS FECHA " +
                    "    FROM CART_RIESGOS_CUBIERTOS " +
                    "    WHERE CARC_CASU_CD_SUCURSAL = ? " +
                    "    AND CARC_CARP_CD_RAMO = ? " +
                    "    AND CARC_CAPO_NU_POLIZA = ? " +
                    "    AND CARC_CACE_NU_CERTIFICADO = ? " +
                    "    AND TRUNC(CARC_FE_EFECTIVA) <= ? " +
                    "    AND CARC_CARB_CD_RAMO = ? " +
                    "    AND CARC_CACB_CD_COBERTURA = ? " +
                    ") FECHAS";
            
            try {
                return jdbcTemplate.queryForObject(sql, LocalDate.class, 
                        sucursal, ramo, poliza, certificado, fechaOcurrencia, ramoCont, cobertura,
                        sucursal, ramo, poliza, certificado, fechaOcurrencia, ramoCont, cobertura);
            } catch (EmptyResultDataAccessException e) {
                return fechaOcurrencia;
            }
        }
        
        /**
         * Gets the maximum days for a line of business
         */
        public int obtenerDiasMaximos(Long ramo) {
            String sql = "SELECT TO_NUMBER(NVL(RV_ABBREVIATION,1)) " +
                    "FROM CG_REF_CODES " +
                    "WHERE RV_LOW_VALUE = TO_CHAR(?) " +
                    "AND RV_DOMAIN = 'MAXIMAINDEMNIZACION' " +
                    "AND RV_TYPE = 'A'";
            
            try {
                return jdbcTemplate.queryForObject(sql, Integer.class, ramo);
            } catch (EmptyResultDataAccessException e) {
                return 1;
            }
        }
        
        /**
         * Checks if a line of business is LUC (Límite Único de Cobertura)
         */
        public boolean esRamoLUC(Long ramo) {
            String sql = "SELECT PACK_VALIDA_LUC.F_VALIDA_RAMO_LUC(?) FROM DUAL";
            
            try {
                String result = jdbcTemplate.queryForObject(sql, String.class, ramo);
                return "S".equals(result);
            } catch (Exception e) {
                return false;
            }
        }
        
        /**
         * Validates LUC for a coverage
         */
        public boolean validarRamoLUC(Long sucursal, Long siniestro, Long ramoCont, String cobertura,
                                     Long ramo, Integer estadoSiniestro, String tipoMovimiento, BigDecimal diferencia) {
            
            // Call the stored procedure
            String sql = "BEGIN " +
                    "P_VALIDA_LUC_RVA(?, ?, ?, ?, ?, ?, ?, ?, ?); " +
                    "END;";
            
            final String[] result = new String[1];
            
            jdbcTemplate.execute(sql, new PreparedStatementCallback<Boolean>() {
                @Override
                public Boolean doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                    ps.setLong(1, sucursal);
                    ps.setLong(2, siniestro);
                    ps.setInt(3, estadoSiniestro);
                    ps.setLong(4, ramo);
                    ps.setLong(5, ramoCont);
                    ps.setString(6, cobertura);
                    ps.setString(7, tipoMovimiento);
                    ps.setBigDecimal(8, diferencia);
                    ps.registerOutParameter(9, Types.VARCHAR);
                    ps.execute();
                    result[0] = ps.getString(9);
                    return true;
                }
            });
            
            return "OK".equals(result[0]);
        }
        
        /**
         * Gets the next movement number for a claim
         */
        public Long obtenerNumeroMovimiento(Long sucursal, Long siniestro) {
            String sql = "SELECT NVL(GREATEST(MAX(A.SIMS_NU_MOVIMIENTO), MAX(B.SMCC_SIMS_NU_MOVIMIENTO)) + 1, 1) " +
                    "FROM SINT_MOVIMIENTO_SINIESTROS A, SINT_MOVIMIENTOS_COBERTURAS_CE B " +
                    "WHERE A.SIMS_SISI_CASU_CD_SUCURSAL = ? " +
                    "AND A.SIMS_SISI_NU_SINIESTRO = ? " +
                    "AND A.SIMS_SISI_CASU_CD_SUCURSAL = B.SMCC_SIMS_CASU_CD_SUCURSAL(+) " +
                    "AND A.SIMS_SISI_NU_SINIESTRO = B.SMCC_SIMS_SISI_NU_SINIESTRO(+)";
            
            try {
                return jdbcTemplate.queryForObject(sql, Long.class, sucursal, siniestro);
            } catch (EmptyResultDataAccessException e) {
                return 1L;
            }
        }
        
        /**
         * Gets the movement type based on claim status
         */
        public String obtenerTipoMovimiento() {
            return "RA"; // Default value for reserve adjustment
        }
        
        /**
         * Gets the description for a movement type
         */
        public String obtenerDescripcionTipoMovimiento(String tipoMovimiento) {
            String sql = "SELECT DSVALOR FROM CART_TTAPVAT1 WHERE NMTABLA = 85 AND CDCLAVE = ?";
            
            try {
                return jdbcTemplate.queryForObject(sql, String.class, tipoMovimiento);
            } catch (EmptyResultDataAccessException e) {
                return "Ajuste de Reserva";
            }
        }
        
        /**
         * Gets the currency for a claim
         */
        public String obtenerMoneda(Long sucursal, Long siniestro) {
            String sql = "SELECT CAPO_CAMO_CD_MONEDA " +
                    "FROM SINT_CERTIFICADOS_SINIESTROS, CART_POLIZAS " +
                    "WHERE SICE_SISI_CASU_CD_SUCURSAL = ? " +
                    "AND SICE_SISI_NU_SINIESTRO = ? " +
                    "AND CAPO_CASU_CD_SUCURSAL = SICE_CACE_CASU_CD_SUCURSAL " +
                    "AND CAPO_CARP_CD_RAMO = SICE_CACE_CARP_CD_RAMO " +
                    "AND CAPO_NU_POLIZA = SICE_CACE_CAPO_NU_POLIZA";
            
            try {
                return jdbcTemplate.queryForObject(sql, String.class, sucursal, siniestro);
            } catch (EmptyResultDataAccessException e) {
                return "01"; // Default currency
            }
        }
    }

    /**
     * Service for reserve adjustment functions
     */
    @Service
    @Slf4j
    public static class AjusteReservaService {
        
        @Autowired
        private JdbcTemplate jdbcTemplate;
        
        @Autowired
        private UtileriasService utileriasService;
        
        @Autowired
        private SiniestroMovimientoRepository siniestroMovimientoRepository;
        
        private BigDecimal sumaAseguradaPendiente = BigDecimal.ZERO;
        
        /**
         * Sets the pending sum insured
         */
        public void setSumaAseguradaPendiente(BigDecimal valor) {
            this.sumaAseguradaPendiente = valor;
        }
        
        /**
         * Gets the pending sum insured
         */
        public BigDecimal getSumaAseguradaPendiente() {
            return this.sumaAseguradaPendiente;
        }
        
        /**
         * Validates if there are adjustments to apply
         */
        public boolean validarAjuste() {
            // TODO-BUSINESS-LOGIC: Implement validation to check if there are adjustments to apply
            // This should count the number of coverages with valid adjustments
            // Test by creating a form with no adjustments and verify it shows the error message
            return true;
        }
        
        /**
         * Confirms if a zero adjustment is acceptable
         */
        public boolean confirmarAjusteCero() {
            // In a real application, this would show a confirmation dialog
            // For now, we'll just return true
            return true;
        }
        
        /**
         * Generates a reserve adjustment for a coverage
         */
        @Transactional
        public boolean generarAjusteReserva(Long sucursal, Long siniestro, Long ramoCont, String cobertura,
                                           Long ramo, Long poliza, Long certificado) {
            
            try {
                // Get the next movement number
                Long numeroMovimiento = utileriasService.obtenerNumeroMovimiento(sucursal, siniestro);
                
                // Get the movement type
                String tipoMovimiento = utileriasService.obtenerTipoMovimiento();
                
                // Register the adjustment
                if (!registrarReserva(sucursal, siniestro, ramo, ramoCont, cobertura)) {
                    return false;
                }
                
                // Update coverage amounts
                if (!actualizarCobertura(sucursal, siniestro, ramoCont, cobertura, 
                        sucursal, ramo, poliza, certificado)) {
                    return false;
                }
                
                return true;
            } catch (Exception e) {
                log.error("Error al generar ajuste de reserva", e);
                return false;
            }
        }
        
        /**
         * Registers a reserve adjustment
         */
        @Transactional
        private boolean registrarReserva(Long sucursal, Long siniestro, Long ramo, Long ramoCont, String cobertura) {
            try {
                // Get the next movement number
                Long numeroMovimiento = utileriasService.obtenerNumeroMovimiento(sucursal, siniestro);
                
                // Get the movement type
                String tipoMovimiento = utileriasService.obtenerTipoMovimiento();
                
                // Insert movement
                if (!insertarMovimientoAjuste(sucursal, siniestro, ramoCont, cobertura, numeroMovimiento, 
                        BigDecimal.ZERO, tipoMovimiento)) { // Amount will be set by the caller
                    return false;
                }
                
                // Validate LUC if applicable
                if (utileriasService.esRamoLUC(ramo)) {
                    if (!utileriasService.validarRamoLUC(sucursal, siniestro, ramoCont, cobertura, ramo, 
                            0, tipoMovimiento, BigDecimal.ZERO)) {
                        return false;
                    }
                }
                
                return true;
            } catch (Exception e) {
                log.error("Error al registrar reserva", e);
                return false;
            }
        }
        
        /**
         * Inserts an adjustment movement
         */
        @Transactional
        private boolean insertarMovimientoAjuste(Long sucursal, Long siniestro, Long ramoCont, String cobertura,
                                               Long numeroMovimiento, BigDecimal importe, String tipoMovimiento) {
            try {
                // Get currency
                String moneda = utileriasService.obtenerMoneda(sucursal, siniestro);
                
                // Insert into SINT_MOVIMIENTO_SINIESTROS
                String sql1 = "INSERT INTO SINT_MOVIMIENTO_SINIESTROS " +
                        "(SIMS_SISI_CASU_CD_SUCURSAL, SIMS_SISI_NU_SINIESTRO, SIMS_NU_MOVIMIENTO, " +
                        "SIMS_FE_MOVIMIENTO, SIMS_TP_MOVIMIENTO, SIMS_CAAN_CD_ANALISTA, " +
                        "SIMS_MT_MOVIMIENTO, SIMS_CAMO_CD_MONEDA, SIMS_CARB_CD_RAMO, " +
                        "SIMS_CACB_CD_COBERTURA, SIMS_NU_AVISO_ACEPTADO) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                jdbcTemplate.update(sql1, 
                        sucursal, siniestro, numeroMovimiento,
                        java.sql.Date.valueOf(LocalDate.now()), tipoMovimiento, "SYSTEM",
                        importe, moneda, ramoCont,
                        cobertura, null);
                
                // TODO-BUSINESS-LOGIC: Implement insertion into SINT_MOVIMIENTOS_COBERTURAS_CE
                // This requires getting policy information from the certificate
                // Test by creating an adjustment and verifying both tables are updated
                
                // TODO-BUSINESS-LOGIC: Implement accounting entries in SINT_MOVIMIENTO_CONTARVA
                // This requires calling fContabilizaReserva or equivalent functionality
                // Test by creating an adjustment and verifying accounting entries are created
                
                return true;
            } catch (Exception e) {
                log.error("Error al insertar movimiento de ajuste", e);
                return false;
            }
        }
        
        /**
         * Updates coverage amounts
         */
        @Transactional
        private boolean actualizarCobertura(Long sucursal, Long siniestro, Long ramoCont, String cobertura,
                                          Long sucursalPoliza, Long ramo, Long poliza, Long certificado) {
            try {
                String sql = "UPDATE SINT_RESERVA_COBERTURA_CERTIFI " +
                        "SET SICC_CARC_FE_EFECTIVA = ?, " +
                        "SICC_MT_AJUSTADO = (SICC_MT_AJUSTADO + ?) " +
                        "WHERE SICC_SISI_CASU_CD_SUCURSA = ? " +
                        "AND SICC_SICO_SISI_NU_SINIESTRO = ? " +
                        "AND SICC_SICO_CACB_CARB_CD_RAMO = ? " +
                        "AND SICC_SICO_CACB_CD_COBERTURA = ? " +
                        "AND SICC_CACE_CASU_CD_SUCURSA = ? " +
                        "AND SICC_SICE_CACE_CARP_CD_RAMO = ? " +
                        "AND SICC_CACE_CAPO_NU_POLIZA = ? " +
                        "AND SICC_CACE_NU_CERTIFICADO = ?";
                
                // TODO-BUSINESS-LOGIC: Get the effective date and adjustment amount
                // For now, we'll use current date and a placeholder amount
                LocalDate fechaEfectiva = LocalDate.now();
                BigDecimal montoAjuste = BigDecimal.ZERO; // This should come from the coverage
                
                int updated = jdbcTemplate.update(sql, 
                        java.sql.Date.valueOf(fechaEfectiva),
                        montoAjuste,
                        sucursal, siniestro, ramoCont, cobertura,
                        sucursalPoliza, ramo, poliza, certificado);
                
                return updated > 0;
            } catch (Exception e) {
                log.error("Error al actualizar cobertura", e);
                return false;
            }
        }
        
        /**
         * Calculates the pending reserve amount
         */
        public BigDecimal calcularReservaPendiente(Long sucursal, Long ramo, Long poliza, Long certificado,
                                                 LocalDate fechaOcurrencia, Long ramoContable) {
            
            // TODO-BUSINESS-LOGIC: Implement calculation of pending reserve amount
            // This should call PKG_SINT_LUC.fCalcula_Rva_Pendiente or equivalent
            // Test by loading a claim and verifying the pending amount is correct
            
            return BigDecimal.ZERO;
        }
        
        /**
         * Calculates the total payments for a policy
         */
        public BigDecimal calcularTotalPagos(Long sucursal, Long ramo, Long poliza, Long certificado,
                                           LocalDate fechaOcurrencia, Long ramoContable) {
            
            // TODO-BUSINESS-LOGIC: Implement calculation of total payments
            // This should call PKG_SINT_LUC.fCalcula_Sint_Pgos or equivalent
            // Test by loading a claim with payments and verifying the total is correct
            
            return BigDecimal.ZERO;
        }
        
        /**
         * Gets the current sum insured for a policy
         */
        public BigDecimal obtenerSumaAseguradaVigente(Long sucursal, Long ramo, Long poliza, Long certificado,
                                                    LocalDate fechaOcurrencia) {
            
            // TODO-BUSINESS-LOGIC: Implement retrieval of current sum insured
            // This should call PKG_SINT_LUC.fGet_SA_Vigente or equivalent
            // Test by loading a claim and verifying the sum insured is correct
            
            return BigDecimal.ZERO;
        }
        
        /**
         * Validates sum insured priority
         */
        public BigDecimal validarSumaPrioridad(Long sucursal, Long ramo, Long poliza, Long certificado,
                                             LocalDate fechaOcurrencia, Long ramoContable, String cobertura,
                                             BigDecimal montoReserva) {
            
            // TODO-BUSINESS-LOGIC: Implement validation of sum insured priority
            // This should call F_Valida_Sum_Prioridad or equivalent
            // Test by creating an adjustment that exceeds the sum insured and verify it's rejected
            
            return montoReserva;
        }
        
        /**
         * Finds first coverages with priority
         */
        public void buscarPrimerosConPrioridad(Integer prioridad, BigDecimal montoAjustar, BigDecimal saldoOcupado) {
            // TODO-BUSINESS-LOGIC: Implement search for first coverages with priority
            // This should implement pBusca_PrimerosR functionality
            // Test by creating adjustments with priority and verify they're processed correctly
        }
        
        /**
         * Finds last coverages with priority
         */
        public void buscarUltimosConPrioridad(BigDecimal saldoFaltante, Integer prioridad, BigDecimal saldoAcumulado) {
            // TODO-BUSINESS-LOGIC: Implement search for last coverages with priority
            // This should implement pBusca_UltimoR functionality
            // Test by creating adjustments with priority and verify they're processed correctly
        }
        
        /**
         * Validates cause of death for life insurance
         */
        public boolean validarCausaFallecimiento(Long sucursal, Long ramo, Long poliza, Long certificado,
                                               LocalDate fechaOcurrencia, Long ramoContable, String cobertura,
                                               String tipoMovimiento, BigDecimal montoReserva) {
            
            // TODO-BUSINESS-LOGIC: Implement validation of cause of death
            // This should call PKG_SINT_LUC.fValida_CausaFallece or equivalent
            // Test by creating an adjustment for life insurance and verify the validation works
            
            return true;
        }
    }

    /**
     * Exception for when a claim is not found
     */
    public static class SiniestroNotFoundException extends RuntimeException {
        public SiniestroNotFoundException(String message) {
            super(message);
        }
    }
}