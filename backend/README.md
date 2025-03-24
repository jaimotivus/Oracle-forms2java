# Backend Project Documentation

## Overview

This project is a modern Java SE 17 backend implementation that replaces a legacy Oracle Forms application. Built with Spring Boot, it provides a RESTful API layer that supports the Angular frontend while maintaining all the business logic previously implemented in Oracle Forms and PL/SQL.

## Architecture and Design Patterns

The application follows a layered architecture with clear separation of concerns:

- **Controller Layer**: REST endpoints that handle HTTP requests and responses
- **Service Layer**: Business logic implementation, replacing Oracle Forms triggers and PL/SQL procedures
- **Repository Layer**: Data access using Spring Data JPA
- **Domain Layer**: JPA entities representing the business domain

Design patterns implemented:
- **Repository Pattern**: For data access abstraction
- **Dependency Injection**: Using Spring's IoC container
- **DTO Pattern**: For data transfer between layers
- **Service Facade**: Simplifying complex business operations
- **Strategy Pattern**: For flexible algorithm implementations

## Package Structure

```
com.insurance.claims/
├── config/                 # Configuration classes
│   ├── SecurityConfig.java
│   ├── JpaConfig.java
│   └── WebConfig.java
├── controller/             # REST API controllers
│   ├── ClaimController.java
│   ├── PolicyController.java
│   └── InsuranceController.java
├── service/                # Business logic services
│   ├── impl/               # Service implementations
│   │   ├── ClaimServiceImpl.java
│   │   ├── PolicyServiceImpl.java
│   │   └── InsuranceServiceImpl.java
│   ├── ClaimService.java   # Service interfaces
│   ├── PolicyService.java
│   └── InsuranceService.java
├── repository/             # Data access repositories
│   ├── ClaimRepository.java
│   ├── PolicyRepository.java
│   └── InsuranceRepository.java
├── domain/                 # JPA entities
│   ├── Claim.java
│   ├── Policy.java
│   └── Insurance.java
├── dto/                    # Data Transfer Objects
│   ├── ClaimDTO.java
│   ├── PolicyDTO.java
│   └── InsuranceDTO.java
├── exception/              # Custom exceptions
│   ├── ResourceNotFoundException.java
│   ├── BusinessLogicException.java
│   └── GlobalExceptionHandler.java
├── util/                   # Utility classes
│   ├── DateUtils.java
│   └── ValidationUtils.java
└── Application.java        # Main application class
```

## Database Connectivity

The application uses Spring Data JPA with Hibernate as the ORM provider to connect to the Oracle database. Connection settings are configured in `application.properties` or `application.yml`.

Key database configuration:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//localhost:1521/XEPDB1
    username: claims_user
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.Oracle12cDialect
        format_sql: true
    show-sql: false
```

## API Endpoints

The application exposes the following RESTful endpoints:

### Claims Management
- `GET /api/claims` - List all claims
- `GET /api/claims/{id}` - Get claim details
- `POST /api/claims` - Create new claim
- `PUT /api/claims/{id}` - Update claim
- `DELETE /api/claims/{id}` - Delete claim
- `GET /api/claims/policy/{policyId}` - Get claims by policy

### Policy Management
- `GET /api/policies` - List all policies
- `GET /api/policies/{id}` - Get policy details
- `GET /api/policies/calculate-balance/{id}` - Calculate policy balance
- `GET /api/policies/insured-sum/{id}` - Get insured sum

### Insurance Information
- `GET /api/insurance/info/{id}` - Get insurance information
- `GET /api/insurance/calculate-ips/{id}` - Calculate IPS insured sum

Full API documentation is available via Swagger UI at `/swagger-ui.html` when the application is running.

## Installation and Setup

### Prerequisites
- Java SE Development Kit 17
- Gradle 7.4+
- Oracle Database 19c+ (or compatible version)
- Git

### Dependencies

Key dependencies include:
- Spring Boot 2.7.x
- Spring Data JPA
- Spring Security
- Oracle JDBC Driver
- Lombok
- MapStruct
- Springdoc OpenAPI (Swagger)

### Setup Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/your-organization/claims-backend.git
   cd claims-backend
   ```

2. Configure database connection in `src/main/resources/application.yml`

3. Set up environment variables:
   ```bash
   export DB_PASSWORD=your_database_password
   export JWT_SECRET=your_jwt_secret
   ```

4. Build the project:
   ```bash
   ./gradlew clean build
   ```

5. Run the application:
   ```bash
   ./gradlew bootRun
   ```

### Database Configuration

The application requires an Oracle database. You can configure the connection details in `application.yml` or provide them as environment variables:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:oracle:thin:@//localhost:1521/XEPDB1}
    username: ${DB_USERNAME:claims_user}
    password: ${DB_PASSWORD}
```

## Development Workflow

### Building the Project

```bash
# Clean and build
./gradlew clean build

# Build without tests
./gradlew build -x test
```

### Running the Application

```bash
# Run with default profile
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.insurance.claims.service.ClaimServiceTest"

# Run integration tests
./gradlew integrationTest
```

### Code Quality Checks

```bash
# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# Run spotbugs
./gradlew spotbugsMain spotbugsTest

# Run all quality checks
./gradlew check
```

## Key Features Implemented

1. **Policy Balance Calculation**: Reimplementation of `fObtenerSaldo.txt` logic in Java
2. **Insured Sum Calculation**: Conversion of `fObtenerSumaAsegurada.txt` to Java service methods
3. **IPS Insured Sum Calculation**: Implementation of `fSumaAsegIPS.txt` business logic
4. **Claim Information Processing**: Conversion of `pInfoSiniestro.txt` to RESTful endpoints
5. **Toolbar Information Display**: Reimplementation of `P_InformaDatosToolbar.txt` functionality
6. **Form Initialization Logic**: Java equivalent of `when_new_form_instance.txt` trigger
7. **Comprehensive Security**: JWT-based authentication and role-based authorization
8. **Audit Logging**: Tracking all data changes with user information
9. **Caching Strategy**: Performance optimization for frequently accessed data
10. **Batch Processing**: For handling large data operations efficiently

## Known Limitations and Issues

1. **Legacy Data Format Compatibility**: Some complex data types from Oracle Forms require special handling
2. **Transaction Boundaries**: In some cases, transaction boundaries differ from the original implementation
3. **Performance Considerations**: Some complex queries may require optimization
4. **Concurrent Access**: Handling of concurrent modifications needs thorough testing
5. **Oracle-Specific Features**: Some Oracle-specific database features have been replaced with standard JPA functionality

## Troubleshooting

### Common Issues

1. **Database Connection Errors**
   - Verify database credentials and connection string
   - Ensure Oracle JDBC driver is correctly configured
   - Check network connectivity to the database server

2. **Application Startup Failures**
   - Verify Java version (must be 17+)
   - Check for port conflicts
   - Review application logs in `logs/application.log`

3. **API Response Errors**
   - Check request format and content type
   - Verify authentication token validity
   - Review server logs for detailed error information

4. **Performance Issues**
   - Enable SQL logging to identify slow queries
   - Check database indexing
   - Review JPA fetch strategies in entity mappings

### Logging

The application uses SLF4J with Logback for logging. Log levels can be configured in `src/main/resources/logback-spring.xml`.

To enable debug logging:

```yaml
logging:
  level:
    com.insurance.claims: DEBUG
    org.hibernate.SQL: DEBUG
```

### Support

For additional support, please contact the development team at backend-support@insurance-company.com or create an issue in the project repository.