package com.yourcompany.yourapp.service.impl;

import com.yourcompany.yourapp.domain.CartRiesgosCubiertos;
import com.yourcompany.yourapp.domain.CarhRiesgosCubiertos;
import com.yourcompany.yourapp.domain.SintProductoCoberturas;
import com.yourcompany.yourapp.dto.GlobalContextDTO;
import com.yourcompany.yourapp.dto.CoberturaDTO;
import com.yourcompany.yourapp.dto.SiniestroDTO;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.repository.CartRiesgosCubiertosRepository;
import com.yourcompany.yourapp.repository.CarhRiesgosCubiertosRepository;
import com.yourcompany.yourapp.service.FechaEfectivaService;
import com.yourcompany.yourapp.service.SumaAseguradaIPSService;
import com.yourcompany.yourapp.service.SumaAseguradaService;
import com.yourcompany.yourapp.service.ProductoCoberturaService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

/**
 * Service implementation for handling suma asegurada (insured amount) operations.
 * This class is a conversion from the Oracle Forms function fObtenerSumaAsegurada.
 */
@Service
public class SumaAseguradaServiceImpl implements SumaAseguradaService {

    private static final Logger log = LoggerFactory.getLogger(SumaAseguradaServiceImpl.class);
    
    private static final String IPS = "IPS";
    private static final String IVS = "IVS";
    
    @Autowired
    private CartRiesgosCubiertosRepository cartRiesgosCubiertosRepository;
    
    @Autowired
    private CarhRiesgosCubiertosRepository carhRiesgosCubiertosRepository;
    
    @Autowired
    private FechaEfectivaService fechaEfectivaService;
    
    @Autowired
    private SumaAseguradaIPSService sumaAseguradaIPSService;
    
    @Autowired
    private ProductoCoberturaService productoCoberturaService;

    /**
     * Obtains the insured amount (suma asegurada) based on the provided parameters.
     * 
     * @param sucursal Branch code
     * @param ramo Branch code
     * @param poliza Policy number
     * @param certi Certificate number
     * @param ramoCont Contract branch code
     * @param cobert Coverage code
     * @param feEfec Effective date
     * @param globalContext Global context data (equivalent to DT_GLOBAL in Forms)
     * @param coberturaDTO Coverage data (equivalent to DT_COBERTURA in Forms)
     * @param siniestroDTO Claim data (equivalent to DB_SINIESTRO in Forms)
     * @return A message indicating the result of the operation
     * @throws BusinessException If an error occurs during processing
     */
    @Override
    @Transactional(readOnly = true)
    public String obtenerSumaAsegurada(
            Integer sucursal, 
            Integer ramo, 
            Integer poliza, 
            Integer certi, 
            Integer ramoCont, 
            String cobert, 
            Date feEfec, 
            GlobalContextDTO globalContext,
            CoberturaDTO coberturaDTO,
            SiniestroDTO siniestroDTO) throws BusinessException {
        
        String mensaje = null;
        String tipoProd = null;
        
        try {
            // For branches other than 15 and 41
            if (ramo != 15 && ramo != 41) {
                // Get effective date
                mensaje = fechaEfectivaService.obtenerFechaEfectiva();
                if (mensaje != null) {
                    coberturaDTO.setMsjValida(mensaje);
                    return mensaje;
                }
                
                try {
                    // Query to get insured amount and plan from current or historical records
                    Optional<Object[]> result = cartRiesgosCubiertosRepository.findSumaAseguradaAndPlan(
                            sucursal, ramo, poliza, certi, ramoCont, cobert, globalContext.getSiccCarcFeEfectiva());
                    
                    if (result.isEmpty()) {
                        // If not found in current records, check historical records
                        result = carhRiesgosCubiertosRepository.findSumaAseguradaAndPlanWithNoCurrentRecords(
                                sucursal, ramo, poliza, certi, ramoCont, cobert, globalContext.getSiccCarcFeEfectiva());
                    }
                    
                    if (result.isPresent()) {
                        Object[] data = result.get();
                        globalContext.setMtSumaAsegurada((BigDecimal) data[0]);
                        globalContext.setCdPlan((String) data[1]);
                    } else {
                        mensaje = "2 - Error, al obtener la suma asegurada.";
                        coberturaDTO.setMsjValida(mensaje);
                        return mensaje;
                    }
                    
                } catch (Exception e) {
                    if (e instanceof org.springframework.dao.IncorrectResultSizeDataAccessException) {
                        mensaje = "2 - Error, se encontró mas de un registro para obtener la suma asegurada.";
                    } else {
                        mensaje = "2 - Error, al obtener la suma asegurada.";
                    }
                    coberturaDTO.setMsjValida(mensaje);
                    return mensaje;
                }
            } else {
                /* For maritime transport (41) and land transport (15) branches,
                   policies are underwritten by associating the insured amount to a single coverage */
                try {
                    // Get the most recent effective date before the claim occurrence date
                    LocalDate effectiveDate = carhRiesgosCubiertosRepository.findMaxEffectiveDateBeforeOccurrence(
                            sucursal, ramo, poliza, certi, siniestroDTO.getSisiFeOcurrencia());
                    
                    if (effectiveDate != null) {
                        globalContext.setSiccCarcFeEfectiva(effectiveDate);
                    } else {
                        mensaje = "2 - Error, al obtener la fecha efectiva.";
                        coberturaDTO.setMsjValida(mensaje);
                        return mensaje;
                    }
                } catch (Exception e) {
                    if (e instanceof org.springframework.dao.IncorrectResultSizeDataAccessException) {
                        mensaje = "3 - Error, se encontró mas de un registro para la fecha efectiva.";
                    } else {
                        mensaje = "2 - Error, al obtener la fecha efectiva.";
                    }
                    coberturaDTO.setMsjValida(mensaje);
                    return mensaje;
                }
                
                try {
                    // Get the maximum insured amount for the effective date
                    BigDecimal maxSumaAsegurada = carhRiesgosCubiertosRepository.findMaxSumaAseguradaForEffectiveDate(
                            sucursal, ramo, poliza, certi, globalContext.getSiccCarcFeEfectiva());
                    
                    if (maxSumaAsegurada != null) {
                        globalContext.setMtSumaAsegurada(maxSumaAsegurada);
                    } else {
                        mensaje = "4 - Error, al obtener la sumas asegurada.";
                        coberturaDTO.setMsjValida(mensaje);
                        return mensaje;
                    }
                } catch (Exception e) {
                    if (e instanceof org.springframework.dao.IncorrectResultSizeDataAccessException) {
                        mensaje = "4 - Error, se encontró mas de un registro para obtener la suma asegurada.";
                    } else {
                        mensaje = "4 - Error, al obtener la sumas asegurada.";
                    }
                    coberturaDTO.setMsjValida(mensaje);
                    return mensaje;
                }
            }
            
            // LUC functionality
            SintProductoCoberturas proc = productoCoberturaService.getProdCober(ramo, ramoCont, cobert);
            tipoProd = proc.getSipCdcoberturaA();
            
            if (IPS.equals(tipoProd) || IVS.equals(tipoProd)) {
                BigDecimal sumaAseguradaIPS = sumaAseguradaIPSService.calcularSumaAseguradaIPS(
                        sucursal, ramo, poliza, certi, ramoCont, cobert, 
                        siniestroDTO.getSisiFeOcurrencia(), tipoProd);
                
                globalContext.setMtSumaAsegurada(sumaAseguradaIPS);
            }
            
            return null; // Success - no error message
            
        } catch (Exception e) {
            log.error("Error al obtener la suma asegurada", e);
            mensaje = "Error al obtener la suma asegura [" + e.getMessage() + "]";
            throw new BusinessException(mensaje, e);
        }
    }
}