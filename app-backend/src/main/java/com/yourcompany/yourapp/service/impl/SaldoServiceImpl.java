package com.yourcompany.yourapp.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourcompany.yourapp.repository.MovimientosCoberturasCeRepository;
import com.yourcompany.yourapp.repository.LiquidacionCoberturasCeRepository;
import com.yourcompany.yourapp.service.SaldoService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Service implementation for calculating balances (saldos) related to insurance claims.
 * This class is a direct conversion of the Oracle Forms function fObtenerSaldo.
 */
@Service
public class SaldoServiceImpl implements SaldoService {

    @Autowired
    private MovimientosCoberturasCeRepository movimientosCoberturasCeRepository;
    
    @Autowired
    private LiquidacionCoberturasCeRepository liquidacionCoberturasCeRepository;

    /**
     * Calculates the balance (saldo) for a specific insurance claim based on its movements and payments.
     * 
     * This method is equivalent to the Oracle Forms function fObtenerSaldo.
     * It calculates the difference between the sum of movements and the sum of payments
     * for a specific claim, branch, and coverage.
     *
     * @param numeroSiniestro The claim number
     * @param codigoSucursal The branch office code
     * @param codigoCobertura The coverage code
     * @param codigoRamo The insurance branch code
     * @return The calculated balance (saldo)
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal obtenerSaldo(Long numeroSiniestro, String codigoSucursal, 
                                  String codigoCobertura, String codigoRamo) {
        
        BigDecimal montoMovimiento = BigDecimal.ZERO;
        BigDecimal montoPago = BigDecimal.ZERO;
        LocalDate fechaDesde = LocalDate.now();
        
        // Constants equivalent to the original PL/SQL variables
        Integer tipoPago = 3;
        Integer egresoInicio = 700;
        Integer egresoFin = 750;
        
        try {
            // First query: Get sum of movements
            // Equivalent to the first SELECT statement in the original function
            List<String> tiposMovimientoExcluidos = Arrays.asList("IC", "CC", "ID");
            
            montoMovimiento = movimientosCoberturasCeRepository.sumMovimientosBySiniestro(
                codigoSucursal, 
                numeroSiniestro, 
                codigoCobertura, 
                codigoRamo, 
                tiposMovimientoExcluidos, 
                fechaDesde
            );
            
            if (montoMovimiento == null) {
                montoMovimiento = BigDecimal.ZERO;
            }
            
            // Second query: Get sum of payments
            // Equivalent to the second SELECT statement in the original function
            List<String> tiposPagoExcluidos = Arrays.asList("Y", "Z");
            
            montoPago = liquidacionCoberturasCeRepository.sumLiquidacionesBySiniestro(
                codigoSucursal, 
                numeroSiniestro, 
                codigoCobertura, 
                codigoRamo, 
                egresoInicio, 
                egresoFin, 
                fechaDesde, 
                tipoPago, 
                tiposPagoExcluidos
            );
            
            if (montoPago == null) {
                montoPago = BigDecimal.ZERO;
            }
            
            // Calculate final balance: movements - payments
            return montoMovimiento.subtract(montoPago.abs());
            
        } catch (Exception e) {
            // Handle the equivalent of NO_DATA_FOUND exception
            // In the original code, it returns lnSaldo (which is 0 if no data was found)
            return BigDecimal.ZERO;
        }
    }
    
    // TODO-BUSINESS-LOGIC: Implement custom exception handling
    // The original PL/SQL code has a simple exception handler for NO_DATA_FOUND
    // Consider implementing more specific exception handling based on your application's needs
    // Test by forcing queries to return no results and verify the method returns zero
}