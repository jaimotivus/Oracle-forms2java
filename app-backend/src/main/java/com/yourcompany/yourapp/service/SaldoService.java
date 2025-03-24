package com.yourcompany.yourapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class that provides functionality to calculate balance (saldo) for insurance claims.
 * This is a conversion of the Oracle Forms function fObtenerSaldo.
 */
@Service
public class SaldoService {

    private static final Logger logger = Logger.getLogger(SaldoService.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Calculates the balance (saldo) for a specific insurance claim based on coverage and branch.
     * 
     * This method retrieves the sum of movement amounts and payments for a specific claim,
     * then calculates the balance as the difference between movements and payments.
     *
     * @param siniestro The claim number
     * @param sucursal The branch office code
     * @param cobertura The coverage code
     * @param ramoCont The insurance branch code
     * @return The calculated balance (saldo)
     */
    @Transactional(readOnly = true)
    public BigDecimal obtenerSaldo(Long siniestro, String sucursal, String cobertura, String ramoCont) {
        BigDecimal saldo = BigDecimal.ZERO;
        BigDecimal montoMovimiento = BigDecimal.ZERO;
        BigDecimal pago = BigDecimal.ZERO;
        LocalDate fechaDesde = LocalDate.now();
        
        // Constants from the original PL/SQL function
        final int tipoPago = 3;
        final int egresoMin = 700;
        final int egresoMax = 750;

        try {
            // Get the sum of movement amounts
            String movimientosQuery = 
                "SELECT COALESCE(SUM(z.smcc_mt_movimiento), 0) " +
                "FROM sint_movimientos_coberturas_ce z " +
                "WHERE z.smcc_sims_casu_cd_sucursal = :sucursal " +
                "AND z.smcc_sims_sisi_nu_siniestro = :siniestro " +
                "AND z.smcc_sicc_cacb_cd_cobertura = :cobertura " +
                "AND z.smcc_sicc_carb_cd_ramo = :ramoCont " +
                "AND z.smcc_sims_tp_movimiento NOT IN ('IC', 'CC', 'ID') " +
                "AND TRUNC(z.smcc_sims_fe_movimiento) <= :fechaDesde";

            Query query = entityManager.createNativeQuery(movimientosQuery);
            query.setParameter("sucursal", sucursal);
            query.setParameter("siniestro", siniestro);
            query.setParameter("cobertura", cobertura);
            query.setParameter("ramoCont", ramoCont);
            query.setParameter("fechaDesde", fechaDesde);
            
            Object result = query.getSingleResult();
            if (result != null) {
                montoMovimiento = new BigDecimal(result.toString());
            }

            // Get the sum of payments
            String pagosQuery = 
                "SELECT COALESCE(SUM(NVL(a.slcc_mt_liquidacion, 0) - NVL(x.sidc_mt_pago, 0)), 0) " +
                "FROM sint_liquidacion_coberturas_ce a " +
                "JOIN sint_liquidaciones b ON b.sili_sisi_casu_cd_sucursal = a.slcc_sili_casu_cd_sucursal " +
                "    AND b.sili_sisi_nu_siniestro = a.slcc_sili_sisi_nu_siniestro " +
                "    AND b.sili_nu_liquidacion = a.slcc_sili_nu_liquidacion " +
                "LEFT JOIN sint_deducible_coaseguro x ON x.sidc_casu_cd_sucursal = a.slcc_sili_casu_cd_sucursal " +
                "    AND x.sidc_sisi_nu_siniestro = a.slcc_sili_sisi_nu_siniestro " +
                "    AND x.sidc_sili_nu_liquidacion = a.slcc_sili_nu_liquidacion " +
                "    AND x.sidc_cacb_carb_cd_ramo = a.slcc_sicc_carb_cd_ramo " +
                "    AND x.sidc_cacb_cd_cobertura = a.slcc_sicc_cacb_cd_cobertura " +
                "    AND x.sidc_sitp_tp_pago = :tipoPago " +
                "WHERE a.slcc_sili_casu_cd_sucursal = :sucursal " +
                "AND a.slcc_sili_sisi_nu_siniestro = :siniestro " +
                "AND a.slcc_sicc_cacb_cd_cobertura = :cobertura " +
                "AND a.slcc_sicc_carb_cd_ramo = :ramoCont " +
                "AND b.sili_cjte_cd_egreso BETWEEN :egresoMin AND :egresoMax " +
                "AND TRUNC(b.sili_fe_pago) <= :fechaDesde " +
                "AND b.sili_tp_pago NOT IN ('Y', 'Z')";

            query = entityManager.createNativeQuery(pagosQuery);
            query.setParameter("sucursal", sucursal);
            query.setParameter("siniestro", siniestro);
            query.setParameter("cobertura", cobertura);
            query.setParameter("ramoCont", ramoCont);
            query.setParameter("tipoPago", tipoPago);
            query.setParameter("egresoMin", egresoMin);
            query.setParameter("egresoMax", egresoMax);
            query.setParameter("fechaDesde", fechaDesde);
            
            result = query.getSingleResult();
            if (result != null) {
                pago = new BigDecimal(result.toString());
            }

            // Calculate the balance
            saldo = montoMovimiento.subtract(pago.abs());
            
            return saldo;
            
        } catch (NoResultException e) {
            // Equivalent to the NO_DATA_FOUND exception in PL/SQL
            logger.log(Level.INFO, "No data found when calculating balance for siniestro: {0}", siniestro);
            return saldo;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calculating balance for siniestro: " + siniestro, e);
            // TODO-BUSINESS-LOGIC: Determine if exceptions other than NoResultException should be handled differently
            // In the original PL/SQL, only NO_DATA_FOUND was explicitly handled, returning the default saldo value
            // For other exceptions, consider whether to propagate them or handle them gracefully
            return saldo;
        }
    }

    /**
     * Alternative implementation using JPA repositories instead of native queries.
     * This method is provided as a reference for future refactoring.
     * 
     * @param siniestro The claim number
     * @param sucursal The branch office code
     * @param cobertura The coverage code
     * @param ramoCont The insurance branch code
     * @return The calculated balance (saldo)
     */
    // TODO-BUSINESS-LOGIC: Implement this method using JPA repositories
    // 1. Create entity classes for sint_movimientos_coberturas_ce, sint_liquidacion_coberturas_ce, 
    //    sint_liquidaciones, and sint_deducible_coaseguro
    // 2. Create repository interfaces for these entities
    // 3. Use repository methods to fetch the data instead of native queries
    // 4. Test by comparing results with the native query implementation
    public BigDecimal obtenerSaldoJpa(Long siniestro, String sucursal, String cobertura, String ramoCont) {
        throw new UnsupportedOperationException("JPA implementation not yet available");
    }
}