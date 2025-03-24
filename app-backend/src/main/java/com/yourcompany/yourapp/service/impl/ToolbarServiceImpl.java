package com.yourcompany.yourapp.service.impl;

import com.yourcompany.yourapp.domain.ConnectionInfo;
import com.yourcompany.yourapp.exception.ApplicationException;
import com.yourcompany.yourapp.repository.GeneralCatalogRepository;
import com.yourcompany.yourapp.service.MessageService;
import com.yourcompany.yourapp.service.ToolbarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service implementation for toolbar-related operations.
 * This class replaces the Oracle Forms procedure P_InformaDatosToolbar which
 * retrieves and displays user information, current date, and database connection details.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolbarServiceImpl implements ToolbarService {

    private final GeneralCatalogRepository generalCatalogRepository;
    private final MessageService messageService;

    /**
     * Retrieves connection information, current date, and current user.
     * This method replaces the Oracle Forms procedure P_InformaDatosToolbar.
     *
     * @return ConnectionInfo object containing connection name, current date, and username
     * @throws ApplicationException if there's an error retrieving the required information
     */
    @Override
    @Transactional(readOnly = true)
    public ConnectionInfo getToolbarConnectionInfo() {
        ConnectionInfo connectionInfo = new ConnectionInfo();
        
        try {
            // Get connection name from general catalog
            // Equivalent to: Select A.Cage_Nom_Concep Into :DI_CONEXION From Sai_Cat_General A Where A.Cage_Cd_Catalogo = 0
            String connectionName = generalCatalogRepository.findConceptNameByCatalogCode(0L)
                    .orElse("CONNECTION NOT IDENTIFIED");
            connectionInfo.setConnectionName(connectionName);
        } catch (Exception e) {
            log.error("Error retrieving connection name", e);
            connectionInfo.setConnectionName("CONNECTION NOT IDENTIFIED");
        }
        
        try {
            // Get current date from database
            // Equivalent to: Select SysDate Into :CG$CTRL.CGC$SYSDATE From Sys.Dual
            LocalDateTime currentDate = LocalDateTime.now();
            connectionInfo.setCurrentDate(currentDate);
        } catch (Exception e) {
            log.error("Error retrieving current date", e);
            messageService.showErrorMessage("Could not retrieve current date from database. " + e.getMessage());
            throw new ApplicationException("Failed to retrieve current date", e);
        }
        
        try {
            // Get current user
            // Equivalent to: Select User Into :CG$CTRL.CGC$USER From Sys.Dual
            // In a Spring application, we can get the current authenticated user
            String currentUser = getCurrentAuthenticatedUser();
            connectionInfo.setUsername(currentUser);
        } catch (Exception e) {
            log.error("Error retrieving current user", e);
            messageService.showErrorMessage("Could not retrieve user. " + e.getMessage());
            throw new ApplicationException("Failed to retrieve current user", e);
        }
        
        return connectionInfo;
    }
    
    /**
     * Gets the current authenticated user from the security context.
     * 
     * @return the username of the current authenticated user
     */
    private String getCurrentAuthenticatedUser() {
        // TODO-BUSINESS-LOGIC: Implement user retrieval from Spring Security context
        // 1. This should retrieve the username from Spring Security's SecurityContextHolder
        // 2. Test by authenticating with different users and verifying the correct username is returned
        // 3. Ensure proper error handling if no authenticated user is found
        
        // Placeholder implementation - replace with actual security context retrieval
        return "current_user";
    }
}