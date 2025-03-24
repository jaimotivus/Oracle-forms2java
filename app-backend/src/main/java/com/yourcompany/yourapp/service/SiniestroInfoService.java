package com.yourcompany.yourapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourcompany.yourapp.domain.SiniestroInfo;
import com.yourcompany.yourapp.domain.PolizaInfo;
import com.yourcompany.yourapp.domain.CoberturaInfo;
import com.yourcompany.yourapp.repository.SiniestroRepository;
import com.yourcompany.yourapp.repository.PolizaRepository;
import com.yourcompany.yourapp.repository.CoberturaRepository;
import com.yourcompany.yourapp.repository.MovimientosCoberturaRepository;
import com.yourcompany.yourapp.exception.BusinessException;
import com.yourcompany.yourapp.util.GlobalVariables;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service class for handling Siniestro (Claim) information operations.
 * This class replaces the Oracle Forms procedure pInfoSiniestro.
 */
@Service
public class SiniestroInfoService {
    
    private static final Logger logger = Logger.getLogger(SiniestroInfoService.class.getName());
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private SiniestroRepository siniestroRepository;
    
    @Autowired
    private PolizaRepository polizaRepository;
    
    @Autowired
    private CoberturaRepository coberturaRepository;
    
    @Autowired
    private MovimientosCoberturaRepository movimientosCoberturaRepository;
    
    @Autowired
    private UtileriasService utileriasService;
    
    @Autowired
    private SintLucService sintLucService;
    
    @Autowired
    private TApoyoService tApoyoService;
    
    @Autowired
    private GlobalVariables globalVariables;

    /**
     * Retrieves and processes information about a siniestro (claim).
     * This method fetches claim details, policy information, and coverage details.
     * 
     * @param sucursal Branch office code
     * @param ramo Line of business code
     * @param siniestro Claim number
     * @param ramoC Coverage line of business code (optional)
     * @param cobertura Coverage code (optional)
     * @return SiniestroInfo object containing all the claim information
     * @throws BusinessException if an error occurs during processing
     */
    @Transactional
    public SiniestroInfo obtenerInformacionSiniestro(
            Integer sucursal, 
            Integer ramo, 
            Integer siniestro, 
            Integer ramoC, 
            String cobertura) throws BusinessException {
        
        try {
            // Initialize variables
            Integer finalRamoC = (ramoC != null) ? ramoC : 0;
            String finalCobertura = (cobertura != null) ? cobertura : "";
            String message;
            
            // Create result objects
            SiniestroInfo siniestroInfo = new SiniestroInfo();
            PolizaInfo polizaInfo = new PolizaInfo();
            
            // Set basic siniestro information
            siniestroInfo.setSucursal(sucursal);
            siniestroInfo.setRamo(ramo);
            siniestroInfo.setNumeroSiniestro(siniestro);
            
            // Fetch siniestro data
            try {
                // Equivalent to the first SQL query in the original code
                String siniestroQuery = 
                    "SELECT s.sisi_fe_ocurrencia, s.sisi_st_siniestro, " +
                    "SUBSTR(c.cacn_nm_apellido_razon, 1, 60) " +
                    "FROM sint_siniestros s, cart_clientes c " +
                    "WHERE s.sisi_casu_cd_sucursal = :sucursal " +
                    "AND s.sisi_nu_siniestro = :siniestro " +
                    "AND s.sisi_cacn_cd_nacionalidad = c.cacn_cd_nacionalidad " +
                    "AND s.sisi_cacn_nu_cedula_rif = c.cacn_nu_cedula_rif";
                
                Query query = entityManager.createNativeQuery(siniestroQuery);
                query.setParameter("sucursal", sucursal);
                query.setParameter("siniestro", siniestro);
                
                Object[] result = (Object[]) query.getSingleResult();
                
                if (result != null) {
                    siniestroInfo.setFechaOcurrencia((LocalDate) result[0]);
                    siniestroInfo.setEstadoSiniestro((String) result[1]);
                    siniestroInfo.setNombreCliente((String) result[2]);
                }
            } catch (Exception e) {
                logger.warning("Error fetching siniestro data: " + e.getMessage());
                siniestroInfo.setFechaOcurrencia(null);
            }
            
            // Calculate pending insured sum by reserve-priority
            BigDecimal sapValue = sintLucService.calculaRvaPendiente(
                sucursal, 
                ramo, 
                polizaInfo.getNumeroPoliza(), 
                polizaInfo.getNumeroCertificado(), 
                siniestroInfo.getFechaOcurrencia(), 
                10);
            
            globalVariables.setSap(sapValue != null ? sapValue : BigDecimal.ZERO);
            
            // Get status description
            siniestroInfo.setDescripcionEstado(
                tApoyoService.getDescripcion("0STATSIN", siniestroInfo.getEstadoSiniestro()));
            
            // Fetch policy information
            try {
                String polizaQuery = 
                    "SELECT sice_cace_casu_cd_sucursal, sice_cace_carp_cd_ramo, " +
                    "sice_cace_capo_nu_poliza, sice_cace_nu_certificado, sice_nu_beneficiario " +
                    "FROM sint_certificados_siniestros " +
                    "WHERE sice_sisi_casu_cd_sucursal = :sucursal " +
                    "AND sice_sisi_nu_siniestro = :siniestro";
                
                Query query = entityManager.createNativeQuery(polizaQuery);
                query.setParameter("sucursal", sucursal);
                query.setParameter("siniestro", siniestro);
                
                Object[] result = (Object[]) query.getSingleResult();
                
                if (result != null) {
                    polizaInfo.setSucursal((Integer) result[0]);
                    polizaInfo.setRamo((Integer) result[1]);
                    polizaInfo.setNumeroPoliza((Integer) result[2]);
                    polizaInfo.setNumeroCertificado((Integer) result[3]);
                    polizaInfo.setNumeroBeneficiario((Integer) result[4]);
                }
            } catch (Exception e) {
                logger.warning("Error fetching policy data: " + e.getMessage());
                polizaInfo.setSucursal(null);
                polizaInfo.setRamo(null);
                polizaInfo.setNumeroPoliza(null);
                polizaInfo.setNumeroCertificado(null);
            }
            
            // Store policy info in siniestroInfo
            siniestroInfo.setPolizaInfo(polizaInfo);
            
            // Fetch coverage information
            String coberturaQuery = 
                "SELECT sicc_sisi_casu_cd_sucursa, sicc_sico_sisi_nu_siniestro, " +
                "sicc_sico_cacb_carb_cd_ramo, sicc_sico_cacb_cd_cobertura, " +
                "sicc_mt_liquidacion, c.carb_de_ramo, o.cacb_de_cobertura, " +
                "sicc_mt_pago, sicc_mt_rechazo, sicc_mt_ajustado, " +
                "NVL(sicc_mt_sumaseg, 0) suma_aseg, sicc_mt_reserva, " +
                "to_number(u.scb_columna_1) prioridad " +
                "FROM sint_reserva_cobertura_certifi, " +
                "cart_ramos_contables c, " +
                "cart_coberturas o, " +
                "sint_cob_luc u " +
                "WHERE c.carb_cd_ramo = sicc_sico_cacb_carb_cd_ramo " +
                "AND o.cacb_carb_cd_ramo = sicc_sico_cacb_carb_cd_ramo " +
                "AND o.cacb_cd_cobertura = sicc_sico_cacb_cd_cobertura " +
                "AND sicc_sisi_casu_cd_sucursa = :sucursal " +
                "AND sicc_sice_cace_carp_cd_ramo = :ramo " +
                "AND sicc_sico_sisi_nu_siniestro = :siniestro " +
                "AND u.scb_carp_ramo(+) = sicc_sice_cace_carp_cd_ramo " +
                "AND u.scb_carb_ramo(+) = sicc_sico_cacb_carb_cd_ramo " +
                "AND u.scb_cacb_cobertura(+) = sicc_sico_cacb_cd_cobertura " +
                "ORDER BY prioridad";
            
            Query query = entityManager.createNativeQuery(coberturaQuery);
            query.setParameter("sucursal", sucursal);
            query.setParameter("ramo", ramo);
            query.setParameter("siniestro", siniestro);
            
            List<Object[]> coberturaResults = query.getResultList();
            
            for (Object[] row : coberturaResults) {
                CoberturaInfo coberturaInfo = new CoberturaInfo();
                
                coberturaInfo.setRamoCodigo((Integer) row[2]);
                coberturaInfo.setRamoDescripcion(row[5] != null ? (String) row[5] : "INVALIDO");
                coberturaInfo.setCoberturaCodigo((String) row[3]);
                coberturaInfo.setCoberturaDescripcion(row[6] != null ? (String) row[6] : "INVALIDO");
                coberturaInfo.setPrioridad(new BigDecimal((Double) row[12]));
                coberturaInfo.setSumaAsegurada(new BigDecimal((Double) row[10]));
                coberturaInfo.setReserva(new BigDecimal((Double) row[11]));
                coberturaInfo.setAjustado(new BigDecimal((Double) row[9]));
                coberturaInfo.setLiquidacion(new BigDecimal((Double) row[4]));
                
                // Set global variable
                globalVariables.setMtSumaAsegurada(coberturaInfo.getSumaAsegurada());
                
                // Calculate current balance of coverage
                coberturaInfo.setSaldo(utileriasService.obtenerSaldo(
                    siniestro, 
                    sucursal, 
                    coberturaInfo.getCoberturaCodigo(), 
                    coberturaInfo.getRamoCodigo()));
                
                // Get rejection amount
                try {
                    String rechazoQuery = 
                        "SELECT NVL(SUM(z.smcc_mt_movimiento), 0) * -1 " +
                        "FROM sint_movimientos_coberturas_ce z " +
                        "WHERE z.smcc_sims_casu_cd_sucursal = :sucursal " +
                        "AND z.smcc_sims_sisi_nu_siniestro = :siniestro " +
                        "AND z.smcc_sicc_cacb_cd_cobertura = :cobertura " +
                        "AND z.smcc_sicc_carb_cd_ramo = :ramoC " +
                        "AND z.smcc_sims_tp_movimiento IN ('CR') " +
                        "AND TRUNC(z.smcc_sims_fe_movimiento) <= SYSDATE";
                    
                    Query rechazoQueryObj = entityManager.createNativeQuery(rechazoQuery);
                    rechazoQueryObj.setParameter("sucursal", sucursal);
                    rechazoQueryObj.setParameter("siniestro", siniestro);
                    rechazoQueryObj.setParameter("cobertura", finalCobertura);
                    rechazoQueryObj.setParameter("ramoC", finalRamoC);
                    
                    BigDecimal rechazo = (BigDecimal) rechazoQueryObj.getSingleResult();
                    coberturaInfo.setRechazo(rechazo);
                } catch (Exception e) {
                    logger.warning("Error fetching rejection amount: " + e.getMessage());
                    coberturaInfo.setRechazo(BigDecimal.ZERO);
                }
                
                // Validate insured sum
                if (globalVariables.getMtSumaAsegurada() == null || 
                    globalVariables.getMtSumaAsegurada().compareTo(BigDecimal.ZERO) == 0) {
                    
                    boolean result = utileriasService.obtenerSumaAsegurada(
                        polizaInfo.getSucursal(),
                        polizaInfo.getRamo(),
                        polizaInfo.getNumeroPoliza(),
                        polizaInfo.getNumeroCertificado(),
                        coberturaInfo.getRamoCodigo(),
                        coberturaInfo.getCoberturaCodigo(),
                        siniestroInfo.getFechaOcurrencia(),
                        new StringBuilder());
                    
                    if (result) {
                        coberturaInfo.setSumaAsegurada(globalVariables.getMtSumaAsegurada());
                    }
                }
                
                // Add coverage to the list
                siniestroInfo.addCobertura(coberturaInfo);
            }
            
            // Generate remesa (batch number)
            try {
                String remesaQuery = "SELECT gioseg.seq_sint_tmp_masivo.NEXTVAL FROM dual";
                Query remesaQueryObj = entityManager.createNativeQuery(remesaQuery);
                BigDecimal remesa = (BigDecimal) remesaQueryObj.getSingleResult();
                globalVariables.setRemesaCarga(remesa.toString());
            } catch (Exception e) {
                logger.warning("Error generating remesa: " + e.getMessage());
                globalVariables.setRemesaCarga("77777");
            }
            
            return siniestroInfo;
            
        } catch (Exception e) {
            logger.severe("Error retrieving siniestro information: " + e.getMessage());
            throw new BusinessException("Ocurrió un error al obtener la información del siniestro: " + e.getMessage());
        }
    }
    
    // TODO-BUSINESS-LOGIC: Implement pAsignaMontos method
    // This method was commented out in the original code but may be needed.
    // It should assign current amounts to the coverage information.
    // Test by verifying that all monetary values are correctly assigned to coverage objects.
    // Preserve any business rules related to amount calculations.
}