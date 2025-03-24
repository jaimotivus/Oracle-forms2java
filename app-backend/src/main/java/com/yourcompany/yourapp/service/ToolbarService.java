package com.yourcompany.yourapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service class that provides toolbar data information functionality.
 * This is a conversion of the Oracle Forms procedure P_InformaDatosToolbar.
 * 
 * The original procedure retrieved connection information, system date, and current user
 * to display in the toolbar block of the form.
 */
@Service
public class ToolbarService {
    
    private static final Logger logger = LoggerFactory.getLogger(ToolbarService.class);
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private MessageService messageService; // Equivalent to PKG_MSGS in Oracle Forms
    
    /**
     * Retrieves connection information, current date, and user information for display in the toolbar.
     * This method is equivalent to the P_InformaDatosToolbar procedure in Oracle Forms.
     *
     * @return A map containing connection information, system date, and current user
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getToolbarData() {
        Map<String, Object> toolbarData = new HashMap<>();
        
        // Get connection information
        String connectionInfo = getConnectionInfo();
        toolbarData.put("DI_CONEXION", connectionInfo);
        
        // Get system date
        LocalDateTime systemDate = getSystemDate();
        if (systemDate != null) {
            toolbarData.put("CGC$SYSDATE", systemDate);
        }
        
        // Get current user
        String currentUser = getCurrentUser();
        if (currentUser != null) {
            toolbarData.put("CGC$USER", currentUser);
        }
        
        return toolbarData;
    }
    
    /**
     * Retrieves connection information from the database.
     * Equivalent to the first query in P_InformaDatosToolbar.
     *
     * @return The connection information string
     */
    private String getConnectionInfo() {
        try {
            return entityManager.createQuery(
                "SELECT a.cageNomConcep FROM SaiCatGeneral a WHERE a.cageCdCatalogo = 0", 
                String.class)
                .getSingleResult();
        } catch (Exception e) {
            logger.error("Error retrieving connection information", e);
            return "CONEXIÓN NO IDENTIFICADA";
        }
    }
    
    /**
     * Retrieves the current system date from the database.
     * Equivalent to the second query in P_InformaDatosToolbar.
     *
     * @return The current system date
     */
    private LocalDateTime getSystemDate() {
        try {
            // In Java, we can use LocalDateTime.now() instead of querying the database
            // However, to maintain consistency with the original code, we'll use a query
            return entityManager.createNativeQuery(
                "SELECT SYSDATE FROM DUAL", 
                LocalDateTime.class)
                .getSingleResult();
        } catch (Exception e) {
            logger.error("Error retrieving system date", e);
            messageService.showErrorMessage("No se ha podido recuperar la fecha actual de base de datos. " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieves the current database user.
     * Equivalent to the third query in P_InformaDatosToolbar.
     *
     * @return The current user name
     */
    private String getCurrentUser() {
        try {
            return entityManager.createNativeQuery(
                "SELECT USER FROM DUAL", 
                String.class)
                .getSingleResult();
        } catch (Exception e) {
            logger.error("Error retrieving current user", e);
            messageService.showErrorMessage("No se ha podido recuperar el usuario. " + e.getMessage());
            return null;
        }
    }
    
    // TODO-BUSINESS-LOGIC: Implement MessageService class
    // This service should provide functionality similar to PKG_MSGS.Mensaje_Error in Oracle Forms
    // It should display error messages to the user through the UI layer
    // Test by verifying error messages are properly displayed when database errors occur
    // The implementation should preserve the original error message format
}