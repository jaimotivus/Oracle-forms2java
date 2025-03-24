package com.yourcompany.yourapp.service.impl;

import com.yourcompany.yourapp.domain.*;
import com.yourcompany.yourapp.dto.SiniestroInfoDTO;
import com.yourcompany.yourapp.repository.*;
import com.yourcompany.yourapp.service.SiniestroInfoService;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.util.SessionContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for retrieving information about a Siniestro (insurance claim).
 * This is a conversion from the Oracle Forms procedure pInfoSiniestro.
 */
@Service
public class SiniestroInfoServiceImpl implements SiniestroInfoService {

    @Autowired
    private SiniestroRepository siniestroRepository;
    
    @Autowired
    private ClienteRepository clienteRepository;
    
    @Autowired
    private CertificadoSiniestroRepository certificadoSiniestroRepository;
    
    @Autowired
    private ReservaCoberturaCertificadoRepository reservaCoberturaCertificadoRepository;
    
    @Autowired
    private MovimientoCoberturaCeRepository movimientoCoberturaCeRepository;
    
    @Autowired
    private UtileriasService utileriasService;
    
    @Autowired
    private SintLucService sintLucService;
    
    @Autowired
    private TApoyoService tApoyoService;
    
    @Autowired
    private SessionContext sessionContext;

    /**
     * Retrieves detailed information about a siniestro (insurance claim) including
     * policy information, coverages, and financial details.
     *
     * @param sucursal The branch office code
     * @param ramo The insurance branch code
     * @param siniestro The claim number
     * @param ramoC The coverage branch code (optional)
     * @param cobertura The coverage code (optional)
     * @return SiniestroInfoDTO containing all the claim information
     */
    @Override
    @Transactional(readOnly = true)
    public SiniestroInfoDTO getSiniestroInfo(Long sucursal, Long ramo, Long siniestro, 
                                            Long ramoC, String cobertura) {
        // Initialize result object
        SiniestroInfoDTO result = new SiniestroInfoDTO();
        
        // Set initial parameters
        Long lnSucursal = sucursal;
        Long lnRamo = ramo;
        Long lnSiniestro = siniestro;
        Long lnRamoC = (ramoC != null) ? ramoC : 
                       (result.getCobertura() != null ? result.getCobertura().getRamoCd() : null);
        String lnCobert = (cobertura != null) ? cobertura : 
                         (result.getCobertura() != null ? result.getCobertura().getCoberturaCd() : null);
        
        try {
            // Get siniestro information
            Optional<Siniestro> siniestroOpt = siniestroRepository.findBySucursalAndRamoAndNumero(
                    lnSucursal, lnRamo, lnSiniestro);
            
            if (siniestroOpt.isPresent()) {
                Siniestro siniestroEntity = siniestroOpt.get();
                result.setSiniestro(siniestroEntity);
                
                // Get client information
                Optional<Cliente> clienteOpt = clienteRepository.findByNacionalidadAndCedulaRif(
                        siniestroEntity.getCacnCdNacionalidad(), siniestroEntity.getCacnNuCedulaRif());
                
                if (clienteOpt.isPresent()) {
                    Cliente cliente = clienteOpt.get();
                    result.setNombreCliente(cliente.getApellidoRazon().substring(0, 
                            Math.min(60, cliente.getApellidoRazon().length())));
                }
                
                // Get status description
                result.setEstatusSiniestroDesc(tApoyoService.getDescripcion("0STATSIN", 
                        siniestroEntity.getStSiniestro()));
                
                // Calculate pending insured amount by reserve-priority
                BigDecimal sap = sintLucService.calculaRvaPendiente(
                        lnSucursal, 
                        lnRamo, 
                        result.getPoliza() != null ? result.getPoliza().getNumeroPoliza() : null,
                        result.getPoliza() != null ? result.getPoliza().getNumeroCertificado() : null,
                        siniestroEntity.getFeOcurrencia(),
                        10L); // Fixed value from original code
                
                sessionContext.setAttribute("SAP", sap != null ? sap : BigDecimal.ZERO);
            }
            
            // Get policy information
            Optional<CertificadoSiniestro> certificadoOpt = 
                    certificadoSiniestroRepository.findBySucursalAndSiniestro(lnSucursal, lnSiniestro);
            
            if (certificadoOpt.isPresent()) {
                CertificadoSiniestro certificado = certificadoOpt.get();
                result.setPoliza(certificado);
                
                // Store policy values for later use
                Long lnSucPol = certificado.getSucursal();
                Long lnRamoPol = certificado.getRamo();
                Long lnPoliza = certificado.getNumeroPoliza();
                Long lnCert = certificado.getNumeroCertificado();
                
                // Get coverage information
                List<ReservaCoberturaCertificado> coberturas = 
                        reservaCoberturaCertificadoRepository.findCoberturasBySiniestroOrderByPrioridad(
                                lnSucursal, lnRamo, lnSiniestro);
                
                List<CoberturaDTO> coberturasDTO = new ArrayList<>();
                
                for (ReservaCoberturaCertificado cobertura : coberturas) {
                    CoberturaDTO coberturaDTO = new CoberturaDTO();
                    
                    coberturaDTO.setRamoCd(cobertura.getRamoCobertura());
                    coberturaDTO.setRamoDesc(cobertura.getRamoDesc() != null ? 
                            cobertura.getRamoDesc() : "INVALIDO");
                    coberturaDTO.setCoberturaCd(cobertura.getCodigoCobertura());
                    coberturaDTO.setCoberturaDesc(cobertura.getCoberturaDesc() != null ? 
                            cobertura.getCoberturaDesc() : "INVALIDO");
                    coberturaDTO.setPrioridad(cobertura.getPrioridad());
                    coberturaDTO.setSumaAsegurada(cobertura.getSumaAsegurada());
                    
                    // Store suma asegurada in session context
                    sessionContext.setAttribute("MT_SUMA_ASEGURADA", cobertura.getSumaAsegurada());
                    
                    coberturaDTO.setReserva(cobertura.getMontoReserva());
                    coberturaDTO.setAjustado(cobertura.getMontoAjustado());
                    coberturaDTO.setLiquidacion(cobertura.getMontoLiquidacion());
                    
                    // Calculate current balance of coverage
                    BigDecimal saldo = utileriasService.obtenerSaldo(
                            lnSiniestro, lnSucursal, 
                            cobertura.getCodigoCobertura(), 
                            cobertura.getRamoCobertura());
                    
                    coberturaDTO.setSaldo(saldo);
                    
                    // Get rejection amount
                    try {
                        BigDecimal rechazo = movimientoCoberturaCeRepository.calcularMontoRechazo(
                                lnSucursal, lnSiniestro, lnCobert, lnRamoC);
                        coberturaDTO.setRechazo(rechazo);
                    } catch (Exception e) {
                        coberturaDTO.setRechazo(BigDecimal.ZERO);
                    }
                    
                    // Validate insured amount
                    if (sessionContext.getAttribute("MT_SUMA_ASEGURADA") == null || 
                            ((BigDecimal)sessionContext.getAttribute("MT_SUMA_ASEGURADA")).compareTo(BigDecimal.ZERO) == 0) {
                        
                        BigDecimal sumaAsegurada = utileriasService.obtenerSumaAsegurada(
                                lnSucPol, lnRamoPol, lnPoliza, lnCert,
                                coberturaDTO.getRamoCd(), coberturaDTO.getCoberturaCd(),
                                result.getSiniestro().getFeOcurrencia());
                        
                        if (sumaAsegurada != null) {
                            coberturaDTO.setSumaAsegurada(sumaAsegurada);
                        }
                    }
                    
                    coberturasDTO.add(coberturaDTO);
                }
                
                result.setCoberturas(coberturasDTO);
                
                // Generate remesa (batch number)
                try {
                    Long remesa = siniestroRepository.getNextRemesaValue();
                    sessionContext.setAttribute("RemesaCarga", remesa);
                } catch (Exception e) {
                    sessionContext.setAttribute("RemesaCarga", "77777");
                }
            }
            
            return result;
            
        } catch (Exception e) {
            throw new BusinessException("Ocurrió un error al obtener la información del siniestro: " + e.getMessage(), e);
        }
    }
}