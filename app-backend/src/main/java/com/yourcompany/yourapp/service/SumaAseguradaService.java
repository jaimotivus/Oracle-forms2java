package com.yourcompany.yourapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourcompany.yourapp.domain.CartRiesgosCubiertos;
import com.yourcompany.yourapp.domain.CarhRiesgosCubiertos;
import com.yourcompany.yourapp.domain.SintProductoCoberturas;
import com.yourcompany.yourapp.dto.SumaAseguradaResult;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.repository.CartRiesgosCubiertosRepository;
import com.yourcompany.yourapp.repository.CarhRiesgosCubiertosRepository;
import com.yourcompany.yourapp.repository.SintProductoCoberturasRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for handling suma asegurada (insured sum) operations.
 * This is a conversion from the Oracle Forms function fObtenerSumaAsegurada.
 */
@Service
public class SumaAseguradaService {
    
    private static final Logger logger = Logger.getLogger(SumaAseguradaService.class.getName());
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private CartRiesgosCubiertosRepository cartRiesgosCubiertosRepository;
    
    @Autowired
    private CarhRiesgosCubiertosRepository carhRiesgosCubiertosRepository;
    
    @Autowired
    private SintProductoCoberturasRepository sintProductoCoberturasRepository;
    
    @Autowired
    private FechaEfectivaService fechaEfectivaService;
    
    @Autowired
    private SumaAseguradaIPSService sumaAseguradaIPSService;
    
    @Autowired
    private GlobalDataService globalDataService;
    
    /**
     * Obtains the insured sum (suma asegurada) for a given policy and coverage.
     * 
     * @param sucursal Branch office code
     * @param ramo Insurance branch code
     * @param poliza Policy number
     * @param certi Certificate number
     * @param ramoCont Contract branch code
     * @param cobert Coverage code
     * @param feEfec Effective date
     * @return SumaAseguradaResult containing the result and any error message
     */
    @Transactional(readOnly = true)
    public SumaAseguradaResult obtenerSumaAsegurada(
            Integer sucursal, 
            Integer ramo, 
            Integer poliza, 
            Integer certi, 
            Integer ramoCont, 
            String cobert, 
            LocalDate feEfec) {
        
        SumaAseguradaResult result = new SumaAseguradaResult();
        String mensaje = "";
        
        try {
            final String IPS = "IPS";
            final String IVS = "IVS";
            String tipoProd = null;
            
            // Check if ramo is not 15 or 41
            if (ramo != 15 && ramo != 41) {
                // Get effective date
                SumaAseguradaResult fechaResult = fechaEfectivaService.obtenerFechaEfectiva();
                if (!fechaResult.isSuccess()) {
                    result.setMessage(fechaResult.getMessage());
                    result.setSuccess(false);
                    // Store message in DT_COBERTURA.MSJ_VALIDA
                    globalDataService.setCoberturaMsjValida(fechaResult.getMessage());
                    return result;
                }
                
                try {
                    // Get suma asegurada from current or historical records
                    LocalDate fechaEfectiva = globalDataService.getSiccCarcFeEfectiva();
                    
                    // First try to get from current records
                    Optional<CartRiesgosCubiertos> currentRecord = cartRiesgosCubiertosRepository
                            .findBySucursalRamoPolizaCertiRamoContCoberturaAndFechaEfectiva(
                                    sucursal, ramo, poliza, certi, ramoCont, cobert, fechaEfectiva);
                    
                    if (currentRecord.isPresent()) {
                        // Found in current records
                        CartRiesgosCubiertos record = currentRecord.get();
                        BigDecimal sumaAsegurada = record.getCarcMtSumaAsegurada() != null ? 
                                record.getCarcMtSumaAsegurada() : BigDecimal.ZERO;
                        String cdPlan = record.getCarcCaplCdPlan();
                        
                        // Store in global data
                        globalDataService.setMtSumaAsegurada(sumaAsegurada);
                        globalDataService.setCdPlan(cdPlan);
                        
                        result.setSumaAsegurada(sumaAsegurada);
                        result.setCdPlan(cdPlan);
                    } else {
                        // Try to get from historical records
                        // First check if there are no current records for this policy
                        long currentRecordsCount = cartRiesgosCubiertosRepository
                                .countBySucursalRamoPolizaCertiAndFechaEfectivaLessThanEqual(
                                        sucursal, ramo, poliza, certi, fechaEfectiva);
                        
                        if (currentRecordsCount == 0) {
                            Optional<CarhRiesgosCubiertos> historicalRecord = carhRiesgosCubiertosRepository
                                    .findBySucursalRamoPolizaCertiRamoContCoberturaAndFechaEfectiva(
                                            sucursal, ramo, poliza, certi, ramoCont, cobert, fechaEfectiva);
                            
                            if (historicalRecord.isPresent()) {
                                CarhRiesgosCubiertos record = historicalRecord.get();
                                BigDecimal sumaAsegurada = record.getCarcMtSumaAsegurada() != null ? 
                                        record.getCarcMtSumaAsegurada() : BigDecimal.ZERO;
                                String cdPlan = record.getCarcCaplCdPlan();
                                
                                // Store in global data
                                globalDataService.setMtSumaAsegurada(sumaAsegurada);
                                globalDataService.setCdPlan(cdPlan);
                                
                                result.setSumaAsegurada(sumaAsegurada);
                                result.setCdPlan(cdPlan);
                            } else {
                                mensaje = "2 - Error, al obtener la suma asegurada.";
                                globalDataService.setCoberturaMsjValida(mensaje);
                                result.setMessage(mensaje);
                                result.setSuccess(false);
                                return result;
                            }
                        } else {
                            mensaje = "2 - Error, al obtener la suma asegurada.";
                            globalDataService.setCoberturaMsjValida(mensaje);
                            result.setMessage(mensaje);
                            result.setSuccess(false);
                            return result;
                        }
                    }
                } catch (Exception e) {
                    if (e.getMessage().contains("Too many results")) {
                        mensaje = "2 - Error, se encontró mas de un registro para obtener la suma asegurada.";
                    } else {
                        mensaje = "2 - Error, al obtener la suma asegurada.";
                    }
                    globalDataService.setCoberturaMsjValida(mensaje);
                    result.setMessage(mensaje);
                    result.setSuccess(false);
                    return result;
                }
            } else {
                // For ramos 15 and 41 (Transporte Terrestre and Transporte Maritimo)
                // These policies are subscribed associating the insured sum to a single coverage
                try {
                    // Get the most recent effective date before the occurrence date
                    LocalDate sisiFeOcurrencia = globalDataService.getSisiFeOcurrencia();
                    
                    LocalDate maxFechaEfectiva = carhRiesgosCubiertosRepository
                            .findMaxFechaEfectivaBeforeOccurrenceDate(
                                    sucursal, ramo, poliza, certi, sisiFeOcurrencia);
                    
                    if (maxFechaEfectiva == null) {
                        mensaje = "2 - Error, al obtener la fecha efectiva.";
                        globalDataService.setCoberturaMsjValida(mensaje);
                        result.setMessage(mensaje);
                        result.setSuccess(false);
                        return result;
                    }
                    
                    // Store the effective date in global data
                    globalDataService.setSiccCarcFeEfectiva(maxFechaEfectiva);
                    
                    // Get the maximum insured sum for the effective date
                    BigDecimal maxSumaAsegurada = carhRiesgosCubiertosRepository
                            .findMaxSumaAseguradaByFechaEfectiva(
                                    sucursal, ramo, poliza, certi, maxFechaEfectiva);
                    
                    if (maxSumaAsegurada == null) {
                        mensaje = "4 - Error, al obtener la sumas asegurada.";
                        globalDataService.setCoberturaMsjValida(mensaje);
                        result.setMessage(mensaje);
                        result.setSuccess(false);
                        return result;
                    }
                    
                    // Store in global data
                    globalDataService.setMtSumaAsegurada(maxSumaAsegurada);
                    result.setSumaAsegurada(maxSumaAsegurada);
                    
                } catch (Exception e) {
                    if (e.getMessage().contains("Too many results")) {
                        if (e.getStackTrace()[0].getMethodName().contains("findMaxFechaEfectiva")) {
                            mensaje = "3 - Error, se encontró mas de un resgitro para la fecha efectiva.";
                        } else {
                            mensaje = "4 - Error, se encontró mas de un registro para obtener la suma asegurada.";
                        }
                    } else {
                        if (e.getStackTrace()[0].getMethodName().contains("findMaxFechaEfectiva")) {
                            mensaje = "2 - Error, al obtener la fecha efectiva.";
                        } else {
                            mensaje = "4 - Error, al obtener la sumas asegurada.";
                        }
                    }
                    globalDataService.setCoberturaMsjValida(mensaje);
                    result.setMessage(mensaje);
                    result.setSuccess(false);
                    return result;
                }
            }
            
            // Functionality LUC
            SintProductoCoberturas proc = sintProductoCoberturasRepository
                    .findByRamoRamoContAndCobertura(ramo, ramoCont, cobert);
            
            if (proc != null) {
                tipoProd = proc.getSipCdcoberturaA();
                
                if (IPS.equals(tipoProd) || IVS.equals(tipoProd)) {
                    BigDecimal sumaAseguradaIPS = sumaAseguradaIPSService.calcularSumaAseguradaIPS(
                            sucursal, ramo, poliza, certi, ramoCont, cobert, 
                            globalDataService.getSisiFeOcurrencia(), tipoProd);
                    
                    globalDataService.setMtSumaAsegurada(sumaAseguradaIPS);
                    result.setSumaAsegurada(sumaAseguradaIPS);
                }
            }
            
            result.setSuccess(true);
            return result;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error al obtener la suma asegurada", e);
            mensaje = "Error al obtener la suma asegura [" + e.getMessage() + "]";
            result.setMessage(mensaje);
            result.setSuccess(false);
            return result;
        }
    }
}