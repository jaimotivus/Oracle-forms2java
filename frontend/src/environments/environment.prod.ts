/**
 * environment.prod.ts
 *
 * This file contains production environment configuration for the Angular application
 * converted from Oracle Forms SINF501041_fmb.xml.
 *
 * The environment configuration includes:
 * - API endpoints for backend services
 * - Feature flags
 * - Application constants
 * - Configuration for specific modules like reservations and adjustments
 * - Authentication and caching settings
 *
 * Tunable fields are marked with a comment indicating they can be overridden by environment variables.
 */

// Define a TypeScript interface for the environment configuration
export interface EnvironmentConfig {
  production: boolean;
  apiUrl: string;
  endpoints: {
    siniestros: string;
    polizas: string;
    coberturas: string;
    reservas: string;
    ajustes: string;
    liquidaciones: string;
  };
  constants: {
    defaults: {
      sucursal: number;
      ramo: number;
      siniestro: number;
    };
    statusCodes: {
      SINIESTRO_ABIERTO: number;
      SINIESTRO_CERRADO: number;
      SINIESTRO_RESERVA: number;
    };
    movementTypes: {
      RESERVA_AJUSTE: string;
      RESERVA_LIQUIDACION: string;
      RESERVA_RECHAZO: string;
    };
    alertTypes: {
      RESERVAS: string;
      CEROS: string;
      INFO_A: string;
      ASK_INFO: string;
      ERROR_A: string;
    };
  };
  reservasConfig: {
    maximoAjuste: number;
    minimoAjuste: number;
    validaciones: {
      validarSumaAsegurada: boolean;
      validarMontoNegativo: boolean;
      validarMontoCero: boolean;
      validarMontoIgualSaldo: boolean;
    };
    ramosEspeciales: {
      VIDA: number;
      TRANSPORTE_MARITIMO: number;
      TRANSPORTE_TERRESTRE: number;
      ACP_COBERTURA: {
        ramo: number;
        cobertura: string;
      };
    };
    prioridadConfig: {
      enabled: boolean;
      maxPrioridad: number;
      variableGlobalSAP: string;
      variableGlobalSAPX: string;
    };
  };
  features: {
    enablePriorityAdjustments: boolean;
    enableContabilizacion: boolean;
    enableReportes: boolean;
    enableValidacionLUC: boolean;
    enableBlindajePlus: boolean;
  };
  errorHandling: {
    showDetailedErrors: boolean;
    logErrorsToServer: boolean;
    defaultErrorMessage: string;
  };
  logging: {
    level: string;
    enableConsoleLogging: boolean;
    enableRemoteLogging: boolean;
  };
  dateFormats: {
    display: string;
    api: string;
    timestamp: string;
  };
  numberFormats: {
    currency: {
      decimalPlaces: number;
      decimalSeparator: string;
      thousandsSeparator: string;
    };
    percentage: {
      decimalPlaces: number;
      decimalSeparator: string;
      thousandsSeparator: string;
    };
  };
  auth: {
    tokenExpirationTime: number;
    refreshTokenExpirationTime: number;
    loginUrl: string;
    logoutUrl: string;
    refreshTokenUrl: string;
  };
  cache: {
    defaultTTL: number;
    maxSize: number;
  };
}

export const environment: EnvironmentConfig = {
  production: true,

  // API base URL for backend services (Tunable via environment variable)
  apiUrl: '/api',

  // API endpoints for specific services
  endpoints: {
    siniestros: '/siniestros',
    polizas: '/polizas',
    coberturas: '/coberturas',
    reservas: '/reservas',
    ajustes: '/ajustes',
    liquidaciones: '/liquidaciones'
  },

  // Application constants from Oracle Forms
  constants: {
    // Default values for form fields
    defaults: {
      sucursal: 5,
      ramo: 13,
      siniestro: 130001125
    },

    // Status codes from Oracle Forms
    statusCodes: {
      SINIESTRO_ABIERTO: 24,
      SINIESTRO_CERRADO: 25,
      SINIESTRO_RESERVA: 26
    },

    // Movement types from Oracle Forms
    movementTypes: {
      RESERVA_AJUSTE: 'RA',
      RESERVA_LIQUIDACION: 'RL',
      RESERVA_RECHAZO: 'RX'
    },

    // Alert types from Oracle Forms
    alertTypes: {
      RESERVAS: 'La Reserva que trata de crear para este siniestro excede la Suma Asegurada para esta cobertura',
      CEROS: 'Notificador',
      INFO_A: 'Información',
      ASK_INFO: 'Información',
      ERROR_A: 'ERROR'
    }
  },

  // Configuration for reservations module. Used in ReservasComponent and ReservasService.
  reservasConfig: {
    // Maximum allowed values for adjustments
    maximoAjuste: 9999999999.99,
    minimoAjuste: -9999999999.99,

    // Validation rules
    validaciones: {
      // Validate that adjustment amount doesn't exceed sum insured
      validarSumaAsegurada: true,
      // Validate that adjustment amount is not negative
      validarMontoNegativo: true,
      // Validate that adjustment amount is not zero
      validarMontoCero: true,
      // Validate that adjustment amount is not equal to balance
      validarMontoIgualSaldo: true
    },

    // Special handling for specific insurance branches
    ramosEspeciales: {
      // Life insurance (Ramo 13) uses priority-based adjustments
      VIDA: 13,
      // Maritime transport (Ramo 41) has special validation rules
      TRANSPORTE_MARITIMO: 41,
      // Land transport (Ramo 15) has special validation rules
      TRANSPORTE_TERRESTRE: 15,
      // ACP coverage (Ramo 03, Cobertura 003) has special calculation
      ACP_COBERTURA: {
        ramo: 3,
        cobertura: '003'
      }
    },

    // Configuration for priority-based adjustments (used in Life insurance)
    prioridadConfig: {
      enabled: true,
      maxPrioridad: 5,
      // Global variable for pending sum insured adjustment
      variableGlobalSAP: 'SAP',
      // Global variable for temporary sum insured adjustment
      variableGlobalSAPX: 'SAP_X'
    }
  },

  // Feature flags
  features: {
    // Enable/disable specific features
    enablePriorityAdjustments: true,
    enableContabilizacion: true,
    enableReportes: true,
    enableValidacionLUC: true,
    enableBlindajePlus: true
  },

  // Error handling configuration.  Used in ErrorInterceptor and GlobalErrorHandler.
  errorHandling: {
    // Show detailed error messages in production (Tunable via environment variable)
    showDetailedErrors: false,
    // Log errors to server (Tunable via environment variable)
    logErrorsToServer: true,
    // Default error message (Tunable via environment variable).  Consider internationalization.
    defaultErrorMessage: 'Ocurrió un error al procesar su solicitud. Por favor intente nuevamente.'
  },

  // Logging configuration.  Used in LoggingService.
  logging: {
    level: 'error', // 'debug', 'info', 'warn', 'error' (Tunable via environment variable)
    enableConsoleLogging: false, // (Tunable via environment variable)
    enableRemoteLogging: true // (Tunable via environment variable)
  },

  // Date formats.  Used in DatePipe and date input components.
  dateFormats: {
    display: 'dd/MM/yyyy',
    api: 'yyyy-MM-dd',
    timestamp: 'dd/MM/yyyy HH:mm:ss'
  },

  // Number formats. Used in currency and percentage pipes.
  numberFormats: {
    currency: {
      decimalPlaces: 2,
      decimalSeparator: '.',
      thousandsSeparator: ','
    },
    percentage: {
      decimalPlaces: 2,
      decimalSeparator: '.',
      thousandsSeparator: ','
    }
  },

  // Authentication configuration.  Used in AuthService and AuthInterceptor.
  auth: {
    tokenExpirationTime: 28800, // 8 hours in seconds (Tunable via environment variable) - Time in seconds until the token expires.
    refreshTokenExpirationTime: 604800, // 7 days in seconds (Tunable via environment variable) - Time in seconds until the refresh token expires.
    loginUrl: '/auth/login',
    logoutUrl: '/auth/logout',
    refreshTokenUrl: '/auth/refresh'
  },

  // Cache configuration. Used in CacheService.
  cache: {
    // Cache lifetime in seconds (Tunable via environment variable)
    defaultTTL: 300, // 5 minutes
    // Maximum cache size (Tunable via environment variable)
    maxSize: 100
  }
};

// Example of using APP_INITIALIZER to fetch remote config (if needed)
// This is just a placeholder; implement the actual fetching logic.
/*
export function initializeApp(httpClient: HttpClient): () => Promise<any> {
  return (): Promise<any> => {
    return httpClient.get('/assets/config.json')
      .toPromise()
      .then(response => {
        // Merge remote config with environment.prod.ts
        Object.assign(environment, response);
      })
      .catch(error => {
        console.error('Failed to load remote config:', error);
      });
  };
}
*/

// Example of a validation function (can be used in APP_INITIALIZER or a config service)
export function validateEnvironment(config: EnvironmentConfig): void {
  if (!config.apiUrl) {
    console.error('Error: apiUrl is not defined in the environment configuration.');
    // Optionally, throw an error to prevent the app from starting.
    throw new Error('Missing apiUrl configuration');
  }

  if (typeof config.auth.tokenExpirationTime !== 'number' || config.auth.tokenExpirationTime <= 0) {
    console.warn('Warning: Invalid tokenExpirationTime.  Using default.');
    config.auth.tokenExpirationTime = 28800; // Set a default value
  }
  // Add more validations as needed.
}