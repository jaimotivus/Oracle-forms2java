package com.example.application.repository;

import com.example.application.entity.SintReservaCoberturaCertifi;
import com.example.application.entity.SintMovimientoSiniestros;
import com.example.application.entity.SintMovimientosCoberturasCe;
import com.example.application.entity.SintMovimientoContarva;
import com.example.application.entity.SintTmpMasivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for handling insurance claim reserve adjustments.
 * This repository provides methods to access and manipulate data related to
 * insurance claims, coverages, and reserve adjustments.
 */
@Repository
public interface SinfRepository extends JpaRepository<SintReservaCoberturaCertifi, Long> {

    /**
     * Find all coverage reserves for a specific claim
     *
     * @param sucursal  Branch office code
     * @param ramo      Insurance line code
     * @param siniestro Claim number
     * @return List of coverage reserves
     */
    @Query("SELECT r FROM SintReservaCoberturaCertifi r " +
           "JOIN CartRamosContables c ON c.carbCdRamo = r.siccSicoCacbCarbCdRamo " +
           "JOIN CartCoberturas o ON o.cacbCarbCdRamo = r.siccSicoCacbCarbCdRamo " +
           "AND o.cacbCdCobertura = r.siccSicoCacbCdCobertura " +
           "WHERE r.siccSisiCasuCdSucursa = :sucursal " +
           "AND r.siccSiceCaceCarpCdRamo = :ramo " +
           "AND r.siccSicoSisiNuSiniestro = :siniestro")
    List<SintReservaCoberturaCertifi> findCoverageReservesByClaim(
            @Param("sucursal") Integer sucursal,
            @Param("ramo") Integer ramo,
            @Param("siniestro") Integer siniestro);

    /**
     * Find a specific coverage reserve by claim and coverage details
     *
     * @param sucursal    Branch office code
     * @param siniestro   Claim number
     * @param ramoCont    Accounting line code
     * @param cobertura   Coverage code
     * @param sucursalPol Policy branch office code
     * @param ramo        Insurance line code
     * @param poliza      Policy number
     * @param certificado Certificate number
     * @return Optional containing the coverage reserve if found
     */
    @Query("SELECT r FROM SintReservaCoberturaCertifi r " +
           "WHERE r.siccSisiCasuCdSucursa = :sucursal " +
           "AND r.siccSicoSisiNuSiniestro = :siniestro " +
           "AND r.siccSicoCacbCarbCdRamo = :ramoCont " +
           "AND r.siccSicoCacbCdCobertura = :cobertura " +
           "AND r.siccCaceCasuCdSucursa = :sucursalPol " +
           "AND r.siccSiceCaceCarpCdRamo = :ramo " +
           "AND r.siccCaceCapoNuPoliza = :poliza " +
           "AND r.siccCaceNuCertificado = :certificado")
    Optional<SintReservaCoberturaCertifi> findCoverageReserve(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("sucursalPol") Integer sucursalPol,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado);

    /**
     * Calculate the balance for a specific coverage
     *
     * @param siniestro Claim number
     * @param sucursal  Branch office code
     * @param cobertura Coverage code
     * @param ramoCont  Accounting line code
     * @return The calculated balance
     */
    @Query(value = "SELECT NVL(SUM(z.smcc_mt_movimiento), 0) - " +
                  "NVL((SELECT SUM(NVL(a.slcc_mt_liquidacion, 0) - NVL(x.sidc_mt_pago, 0)) " +
                  "FROM sint_liquidacion_coberturas_ce a " +
                  "JOIN sint_liquidaciones b ON b.sili_sisi_casu_cd_sucursal = a.slcc_sili_casu_cd_sucursal " +
                  "AND b.sili_sisi_nu_siniestro = a.slcc_sili_sisi_nu_siniestro " +
                  "AND b.sili_nu_liquidacion = a.slcc_sili_nu_liquidacion " +
                  "LEFT JOIN sint_deducible_coaseguro x ON x.sidc_casu_cd_sucursal = a.slcc_sili_casu_cd_sucursal " +
                  "AND x.sidc_sisi_nu_siniestro = a.slcc_sili_sisi_nu_siniestro " +
                  "AND x.sidc_sili_nu_liquidacion = a.slcc_sili_nu_liquidacion " +
                  "AND x.sidc_cacb_carb_cd_ramo = a.slcc_sicc_carb_cd_ramo " +
                  "AND x.sidc_cacb_cd_cobertura = a.slcc_sicc_cacb_cd_cobertura " +
                  "WHERE a.slcc_sili_casu_cd_sucursal = :sucursal " +
                  "AND a.slcc_sili_sisi_nu_siniestro = :siniestro " +
                  "AND a.slcc_sicc_cacb_cd_cobertura = :cobertura " +
                  "AND a.slcc_sicc_carb_cd_ramo = :ramoCont " +
                  "AND b.sili_cjte_cd_egreso BETWEEN 700 AND 750 " +
                  "AND TRUNC(b.sili_fe_pago) <= TRUNC(SYSDATE) " +
                  "AND b.sili_tp_pago NOT IN ('Y', 'Z') " +
                  "AND b.sili_st_liquidacion NOT IN (6, 7) " +
                  "AND x.sidc_sitp_tp_pago(+) = 3), 0) " +
                  "FROM sint_movimientos_coberturas_ce z " +
                  "WHERE z.smcc_sims_casu_cd_sucursal = :sucursal " +
                  "AND z.smcc_sims_sisi_nu_siniestro = :siniestro " +
                  "AND z.smcc_sicc_cacb_cd_cobertura = :cobertura " +
                  "AND z.smcc_sicc_carb_cd_ramo = :ramoCont " +
                  "AND z.smcc_sims_tp_movimiento NOT IN ('IC', 'CC', 'ID') " +
                  "AND TRUNC(z.smcc_sims_fe_movimiento) <= TRUNC(SYSDATE)", 
           nativeQuery = true)
    BigDecimal calculateCoverageBalance(
            @Param("siniestro") Integer siniestro,
            @Param("sucursal") Integer sucursal,
            @Param("cobertura") String cobertura,
            @Param("ramoCont") Integer ramoCont);

    /**
     * Find the next movement number for a claim
     *
     * @param sucursal  Branch office code
     * @param siniestro Claim number
     * @return The next movement number
     */
    @Query(value = "SELECT NVL(GREATEST(MAX(a.sims_nu_movimiento), MAX(b.smcc_sims_nu_movimiento)) + 1, 1) " +
                  "FROM sint_movimiento_siniestros a, sint_movimientos_coberturas_ce b " +
                  "WHERE a.sims_sisi_casu_cd_sucursal = :sucursal " +
                  "AND a.sims_sisi_nu_siniestro = :siniestro " +
                  "AND a.sims_sisi_casu_cd_sucursal = b.smcc_sims_casu_cd_sucursal " +
                  "AND a.sims_sisi_nu_siniestro = b.smcc_sims_sisi_nu_siniestro",
           nativeQuery = true)
    Integer findNextMovementNumber(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro);

    /**
     * Find the next accounting entry number for a claim
     *
     * @param sucursal  Branch office code
     * @param siniestro Claim number
     * @return The next accounting entry number
     */
    @Query(value = "SELECT NVL(MAX(simc_nu_asconta), 0) + 1 " +
                  "FROM sint_movimiento_contarva " +
                  "WHERE simc_nu_sucursal = :sucursal " +
                  "AND simc_nu_siniestro = :siniestro",
           nativeQuery = true)
    Integer findNextAccountingEntryNumber(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro);

    /**
     * Update the adjusted amount for a coverage reserve
     *
     * @param adjustedAmount      New adjusted amount
     * @param effectiveDate       Effective date
     * @param sucursal           Branch office code
     * @param siniestro          Claim number
     * @param ramoCont           Accounting line code
     * @param cobertura          Coverage code
     * @param sucursalPol        Policy branch office code
     * @param ramo               Insurance line code
     * @param poliza             Policy number
     * @param certificado        Certificate number
     * @return Number of rows updated
     */
    @Query(value = "UPDATE sint_reserva_cobertura_certifi " +
                  "SET sicc_carc_fe_efectiva = :effectiveDate, " +
                  "sicc_mt_ajustado = sicc_mt_ajustado + :adjustedAmount " +
                  "WHERE sicc_sisi_casu_cd_sucursa = :sucursal " +
                  "AND sicc_sico_sisi_nu_siniestro = :siniestro " +
                  "AND sicc_sico_cacb_carb_cd_ramo = :ramoCont " +
                  "AND sicc_sico_cacb_cd_cobertura = :cobertura " +
                  "AND sicc_cace_casu_cd_sucursa = :sucursalPol " +
                  "AND sicc_sice_cace_carp_cd_ramo = :ramo " +
                  "AND sicc_cace_capo_nu_poliza = :poliza " +
                  "AND sicc_cace_nu_certificado = :certificado",
           nativeQuery = true)
    int updateCoverageReserveAdjustedAmount(
            @Param("adjustedAmount") BigDecimal adjustedAmount,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("sucursalPol") Integer sucursalPol,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado);

    /**
     * Find coverage reserves with priority for a policy
     *
     * @param sucursal    Branch office code
     * @param ramo        Insurance line code
     * @param poliza      Policy number
     * @param certificado Certificate number
     * @return List of coverage reserves with priority
     */
    @Query(value = "SELECT r.*, scl.scb_columna_1 as prioridad " +
                  "FROM sint_reserva_cobertura_certifi r " +
                  "JOIN sint_cob_luc scl ON scl.scb_carp_ramo = r.sicc_sice_cace_carp_cd_ramo " +
                  "AND scl.scb_carb_ramo = r.sicc_sico_cacb_carb_cd_ramo " +
                  "AND scl.scb_cacb_cobertura = r.sicc_sico_cacb_cd_cobertura " +
                  "WHERE r.sicc_cace_casu_cd_sucursa = :sucursal " +
                  "AND r.sicc_sice_cace_carp_cd_ramo = :ramo " +
                  "AND r.sicc_cace_capo_nu_poliza = :poliza " +
                  "AND r.sicc_cace_nu_certificado = :certificado " +
                  "AND scl.scb_columna_1 IS NOT NULL " +
                  "ORDER BY TO_NUMBER(scl.scb_columna_1)",
           nativeQuery = true)
    List<Object[]> findCoverageReservesWithPriority(
            @Param("sucursal") Integer sucursal,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado);

    /**
     * Insert a new claim movement
     *
     * @param sucursal     Branch office code
     * @param siniestro    Claim number
     * @param movimiento   Movement number
     * @param fechaMov     Movement date
     * @param tipoMov      Movement type
     * @param analista     Analyst
     * @param monto        Amount
     * @param moneda       Currency
     * @param ramoCont     Accounting line code
     * @param cobertura    Coverage code
     * @param avisoAceptado Accepted notice
     * @return Number of rows inserted
     */
    @Query(value = "INSERT INTO sint_movimiento_siniestros(" +
                  "sims_sisi_casu_cd_sucursal, sims_sisi_nu_siniestro, sims_nu_movimiento, " +
                  "sims_fe_movimiento, sims_tp_movimiento, sims_caan_cd_analista, " +
                  "sims_mt_movimiento, sims_camo_cd_moneda, sims_carb_cd_ramo, " +
                  "sims_cacb_cd_cobertura, sims_nu_aviso_aceptado) " +
                  "VALUES(:sucursal, :siniestro, :movimiento, " +
                  ":fechaMov, :tipoMov, :analista, " +
                  ":monto, :moneda, :ramoCont, " +
                  ":cobertura, :avisoAceptado)",
           nativeQuery = true)
    int insertClaimMovement(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro,
            @Param("movimiento") Integer movimiento,
            @Param("fechaMov") LocalDate fechaMov,
            @Param("tipoMov") String tipoMov,
            @Param("analista") String analista,
            @Param("monto") BigDecimal monto,
            @Param("moneda") String moneda,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("avisoAceptado") String avisoAceptado);

    /**
     * Insert a new coverage movement
     *
     * @param sucursal     Branch office code
     * @param siniestro    Claim number
     * @param movimiento   Movement number
     * @param ramoCont     Accounting line code
     * @param cobertura    Coverage code
     * @param sucursalPol  Policy branch office code
     * @param ramo         Insurance line code
     * @param poliza       Policy number
     * @param certificado  Certificate number
     * @param tipoMov      Movement type
     * @param fechaMov     Movement date
     * @param monto        Amount
     * @param moneda       Currency
     * @param cambio       Exchange rate
     * @return Number of rows inserted
     */
    @Query(value = "INSERT INTO sint_movimientos_coberturas_ce(" +
                  "smcc_sims_casu_cd_sucursal, smcc_sims_sisi_nu_siniestro, smcc_sims_nu_movimiento, " +
                  "smcc_sicc_carb_cd_ramo, smcc_sicc_cacb_cd_cobertura, smcc_sicc_casu_cd_sucursal, " +
                  "smcc_sicc_carp_cd_ramo, smcc_sicc_cace_capo_nu_poliza, smcc_sicc_cace_nu_certificado, " +
                  "smcc_sims_tp_movimiento, smcc_sims_fe_movimiento, smcc_mt_movimiento, " +
                  "smcc_camo_cd_moneda, smcc_mt_cambio) " +
                  "VALUES(:sucursal, :siniestro, :movimiento, " +
                  ":ramoCont, :cobertura, :sucursalPol, " +
                  ":ramo, :poliza, :certificado, " +
                  ":tipoMov, :fechaMov, :monto, " +
                  ":moneda, :cambio)",
           nativeQuery = true)
    int insertCoverageMovement(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro,
            @Param("movimiento") Integer movimiento,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("sucursalPol") Integer sucursalPol,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado,
            @Param("tipoMov") String tipoMov,
            @Param("fechaMov") LocalDate fechaMov,
            @Param("monto") BigDecimal monto,
            @Param("moneda") String moneda,
            @Param("cambio") BigDecimal cambio);

    /**
     * Insert a temporary record for batch processing
     *
     * @param sucursal     Branch office code
     * @param ramo         Insurance line code
     * @param poliza       Policy number
     * @param certificado  Certificate number
     * @param fechaOcurre  Occurrence date
     * @param idRemesa     Batch ID
     * @param prioridad    Priority
     * @param msjVal       Validation message
     * @param observaMsj   Observation message
     * @param registro     Registration number
     * @param monto        Amount
     * @param ramoCont     Accounting line code
     * @param cobertura    Coverage code
     * @return Number of rows inserted
     */
    @Query(value = "INSERT INTO sint_tmp_masivo(" +
                  "stm_sucursal, stm_carp_ramo, stm_poliza, stm_certificado, " +
                  "stm_fecha_ocurrencia, stm_id_remesa, stm_prioridad, stm_msj_val, " +
                  "smt_observa_msj, smt_registro, stm_monto, stm_carb_ramo, " +
                  "stm_cobertura) " +
                  "VALUES(:sucursal, :ramo, :poliza, :certificado, " +
                  ":fechaOcurre, :idRemesa, :prioridad, :msjVal, " +
                  ":observaMsj, :registro, :monto, :ramoCont, " +
                  ":cobertura)",
           nativeQuery = true)
    int insertTemporaryRecord(
            @Param("sucursal") Integer sucursal,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado,
            @Param("fechaOcurre") LocalDate fechaOcurre,
            @Param("idRemesa") Integer idRemesa,
            @Param("prioridad") Integer prioridad,
            @Param("msjVal") String msjVal,
            @Param("observaMsj") String observaMsj,
            @Param("registro") Integer registro,
            @Param("monto") BigDecimal monto,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura);

    /**
     * Delete temporary records for a specific coverage
     *
     * @param sucursal     Branch office code
     * @param ramo         Insurance line code
     * @param poliza       Policy number
     * @param certificado  Certificate number
     * @param ramoCont     Accounting line code
     * @param cobertura    Coverage code
     * @param idRemesa     Batch ID
     * @return Number of rows deleted
     */
    @Query(value = "DELETE FROM sint_tmp_masivo " +
                  "WHERE stm_sucursal = :sucursal " +
                  "AND stm_carp_ramo = :ramo " +
                  "AND stm_poliza = :poliza " +
                  "AND stm_certificado = :certificado " +
                  "AND stm_carb_ramo = :ramoCont " +
                  "AND stm_cobertura = :cobertura " +
                  "AND stm_id_remesa IN (:idRemesa, 77777)",
           nativeQuery = true)
    int deleteTemporaryRecords(
            @Param("sucursal") Integer sucursal,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("idRemesa") Integer idRemesa);

    /**
     * Find the sum of temporary record amounts for a batch
     *
     * @param idRemesa Batch ID
     * @return Sum of amounts
     */
    @Query(value = "SELECT NVL(SUM(stm_monto), 0) " +
                  "FROM sint_tmp_masivo " +
                  "WHERE stm_id_remesa = :idRemesa",
           nativeQuery = true)
    BigDecimal sumTemporaryRecordAmounts(@Param("idRemesa") Integer idRemesa);

    /**
     * Find the currency for a claim
     *
     * @param sucursal  Branch office code
     * @param siniestro Claim number
     * @return The currency code
     */
    @Query(value = "SELECT capo_camo_cd_moneda " +
                  "FROM sint_certificados_siniestros, cart_polizas " +
                  "WHERE sice_sisi_casu_cd_sucursal = :sucursal " +
                  "AND sice_sisi_nu_siniestro = :siniestro " +
                  "AND capo_casu_cd_sucursal = sice_cace_casu_cd_sucursal " +
                  "AND capo_carp_cd_ramo = sice_cace_carp_cd_ramo " +
                  "AND capo_nu_poliza = sice_cace_capo_nu_poliza",
           nativeQuery = true)
    String findClaimCurrency(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro);

    /**
     * Find the sum assured for a coverage
     *
     * @param sucursal    Branch office code
     * @param ramo        Insurance line code
     * @param poliza      Policy number
     * @param certificado Certificate number
     * @param ramoCont    Accounting line code
     * @param cobertura   Coverage code
     * @param fechaEfectiva Effective date
     * @return The sum assured amount
     */
    @Query(value = "SELECT NVL(carc_mt_suma_asegurada, 0) " +
                  "FROM cart_riesgos_cubiertos " +
                  "WHERE carc_casu_cd_sucursal = :sucursal " +
                  "AND carc_carp_cd_ramo = :ramo " +
                  "AND carc_capo_nu_poliza = :poliza " +
                  "AND carc_cace_nu_certificado = :certificado " +
                  "AND carc_carb_cd_ramo = :ramoCont " +
                  "AND carc_cacb_cd_cobertura = :cobertura " +
                  "AND TRUNC(carc_fe_efectiva) = :fechaEfectiva " +
                  "UNION " +
                  "SELECT NVL(carc_mt_suma_asegurada, 0) " +
                  "FROM carh_riesgos_cubiertos " +
                  "WHERE carc_casu_cd_sucursal = :sucursal " +
                  "AND carc_carp_cd_ramo = :ramo " +
                  "AND carc_capo_nu_poliza = :poliza " +
                  "AND carc_cace_nu_certificado = :certificado " +
                  "AND carc_carb_cd_ramo = :ramoCont " +
                  "AND carc_cacb_cd_cobertura = :cobertura " +
                  "AND TRUNC(carc_fe_efectiva) = :fechaEfectiva " +
                  "AND 0 = (SELECT COUNT(*) " +
                  "         FROM cart_riesgos_cubiertos " +
                  "         WHERE carc_casu_cd_sucursal = :sucursal " +
                  "         AND carc_carp_cd_ramo = :ramo " +
                  "         AND carc_capo_nu_poliza = :poliza " +
                  "         AND carc_cace_nu_certificado = :certificado " +
                  "         AND TRUNC(carc_fe_efectiva) <= :fechaEfectiva)",
           nativeQuery = true)
    BigDecimal findSumAssured(
            @Param("sucursal") Integer sucursal,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("fechaEfectiva") LocalDate fechaEfectiva);

    /**
     * Find the maximum effective date for a coverage
     *
     * @param sucursal    Branch office code
     * @param ramo        Insurance line code
     * @param poliza      Policy number
     * @param certificado Certificate number
     * @param ramoCont    Accounting line code
     * @param cobertura   Coverage code
     * @param fechaOcurrencia Occurrence date
     * @return The maximum effective date
     */
    @Query(value = "SELECT MAX(FECHAS.FECHA) " +
                  "FROM (SELECT TRUNC(MAX(carc_fe_efectiva)) FECHA " +
                  "      FROM carh_riesgos_cubiertos " +
                  "      WHERE carc_casu_cd_sucursal = :sucursal " +
                  "      AND carc_carp_cd_ramo = :ramo " +
                  "      AND carc_capo_nu_poliza = :poliza " +
                  "      AND carc_cace_nu_certificado = :certificado " +
                  "      AND TRUNC(carc_fe_efectiva) <= :fechaOcurrencia " +
                  "      AND carc_carb_cd_ramo = :ramoCont " +
                  "      AND carc_cacb_cd_cobertura = :cobertura " +
                  "      UNION " +
                  "      SELECT TRUNC(MAX(carc_fe_efectiva)) FECHA " +
                  "      FROM cart_riesgos_cubiertos " +
                  "      WHERE carc_casu_cd_sucursal = :sucursal " +
                  "      AND carc_carp_cd_ramo = :ramo " +
                  "      AND carc_capo_nu_poliza = :poliza " +
                  "      AND carc_cace_nu_certificado = :certificado " +
                  "      AND TRUNC(carc_fe_efectiva) <= :fechaOcurrencia " +
                  "      AND carc_carb_cd_ramo = :ramoCont " +
                  "      AND carc_cacb_cd_cobertura = :cobertura) FECHAS",
           nativeQuery = true)
    LocalDate findMaxEffectiveDate(
            @Param("sucursal") Integer sucursal,
            @Param("ramo") Integer ramo,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("fechaOcurrencia") LocalDate fechaOcurrencia);

    /**
     * Find accounting components for a movement
     *
     * @param compania   Company code
     * @param sucursal   Branch office code
     * @param ramoPol    Policy line code
     * @param ramoCont   Accounting line code
     * @param tipoMov    Movement type
     * @param cobertura  Coverage code
     * @param tipoSini   Claim type
     * @return List of accounting components
     */
    @Query(value = "SELECT SUBSTR(rc.sirc_capp_cd_componente, 4, 1) AS pos_neg, " +
                  "rc.sirc_cnma_cd_mayor, rc.sirc_in_codif " +
                  "FROM sint_ramos_componentes rc " +
                  "WHERE rc.sirc_cjcm_cd_compania = :compania " +
                  "AND rc.sirc_casu_cd_sucursal = :sucursal " +
                  "AND rc.sirc_carp_cd_ramo = :ramoPol " +
                  "AND rc.sirc_carb_cd_ramo = :ramoCont " +
                  "AND rc.sirc_sims_tp_movimiento = :tipoMov " +
                  "AND rc.sirc_capp_cd_componente LIKE :cobertura || '%' " +
                  "AND rc.sirc_sitp_cd_tipo_siniestro = :tipoSini " +
                  "AND ((SIGN(:monto) > 0 AND SUBSTR(rc.sirc_capp_cd_componente, 4, 1) = 'P') " +
                  "OR (SIGN(:monto) < 0 AND SUBSTR(rc.sirc_capp_cd_componente, 4, 1) = 'N'))",
           nativeQuery = true)
    List<Object[]> findAccountingComponents(
            @Param("compania") Integer compania,
            @Param("sucursal") Integer sucursal,
            @Param("ramoPol") Integer ramoPol,
            @Param("ramoCont") Integer ramoCont,
            @Param("tipoMov") String tipoMov,
            @Param("cobertura") String cobertura,
            @Param("tipoSini") String tipoSini,
            @Param("monto") BigDecimal monto);

    /**
     * Insert an accounting entry
     *
     * @param sucursal     Branch office code
     * @param siniestro    Claim number
     * @param movimiento   Movement number
     * @param tipoMov      Movement type
     * @param ramoCont     Accounting line code
     * @param cobertura    Coverage code
     * @param sucursalPol  Policy branch office code
     * @param ramoPol      Policy line code
     * @param poliza       Policy number
     * @param certificado  Certificate number
     * @param movtoCon     Accounting movement number
     * @param compania     Company code
     * @param nuMayor      Ledger number
     * @param auxiliar1    Auxiliary 1
     * @param auxiliar2    Auxiliary 2
     * @param mtHaber      Credit amount
     * @param mtDebe       Debit amount
     * @param documento    Document number
     * @param fechaMov     Movement date
     * @param fechaCont    Accounting date
     * @param stCont       Accounting status
     * @param cdRamalt     Alternative line code
     * @param cdCoberalt   Alternative coverage code
     * @param nuAsconta    Accounting entry number
     * @param dsContable   Accounting description
     * @param cdContable   Accounting code
     * @param dsCobercon   Coverage description
     * @return Number of rows inserted
     */
    @Query(value = "INSERT INTO sint_movimiento_contarva(" +
                  "simc_nu_sucursal, simc_nu_siniestro, simc_nu_movimiento, " +
                  "simc_tp_movto, simc_carb_cdramo, simc_cd_cobertura, " +
                  "simc_cd_sucursal, simc_carp_cd_ramo, simc_nu_poliza, " +
                  "simc_nu_certificado, simc_nu_movtocon, simc_nu_compania, " +
                  "simc_nu_mayor, simc_auxiliar_1, simc_auxiliar_2, " +
                  "simc_mt_haber, simc_mt_debe, simc_nu_documento, " +
                  "simc_fe_movimiento, simc_fe_contable, simc_st_contable, " +
                  "simc_cd_ramalt, simc_cd_coberalt, simc_nu_asconta, " +
                  "simc_ds_contable, simc_cd_contable, simc_ds_cobercon) " +
                  "VALUES(:sucursal, :siniestro, :movimiento, " +
                  ":tipoMov, :ramoCont, :cobertura, " +
                  ":sucursalPol, :ramoPol, :poliza, " +
                  ":certificado, :movtoCon, :compania, " +
                  ":nuMayor, :auxiliar1, :auxiliar2, " +
                  ":mtHaber, :mtDebe, :documento, " +
                  ":fechaMov, :fechaCont, :stCont, " +
                  ":cdRamalt, :cdCoberalt, :nuAsconta, " +
                  ":dsContable, :cdContable, :dsCobercon)",
           nativeQuery = true)
    int insertAccountingEntry(
            @Param("sucursal") Integer sucursal,
            @Param("siniestro") Integer siniestro,
            @Param("movimiento") Integer movimiento,
            @Param("tipoMov") String tipoMov,
            @Param("ramoCont") Integer ramoCont,
            @Param("cobertura") String cobertura,
            @Param("sucursalPol") Integer sucursalPol,
            @Param("ramoPol") Integer ramoPol,
            @Param("poliza") Integer poliza,
            @Param("certificado") Integer certificado,
            @Param("movtoCon") Integer movtoCon,
            @Param("compania") Integer compania,
            @Param("nuMayor") String nuMayor,
            @Param("auxiliar1") String auxiliar1,
            @Param("auxiliar2") String auxiliar2,
            @Param("mtHaber") BigDecimal mtHaber,
            @Param("mtDebe") BigDecimal mtDebe,
            @Param("documento") Integer documento,
            @Param("fechaMov") LocalDate fechaMov,
            @Param("fechaCont") LocalDate fechaCont,
            @Param("stCont") String stCont,
            @Param("cdRamalt") String cdRamalt,
            @Param("cdCoberalt") String cdCoberalt,
            @Param("nuAsconta") Integer nuAsconta,
            @Param("dsContable") String dsContable,
            @Param("cdContable") String cdContable,
            @Param("dsCobercon") String dsCobercon);
}