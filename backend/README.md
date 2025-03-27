# Java Backend for SINF501041 Application

## 1. Project Overview

This repository contains the Java SE 17 backend implementation of the modernized SINF501041 application, originally built in Oracle Forms. The backend is developed using Spring Boot framework, providing RESTful APIs that will be consumed by the Angular frontend. This implementation maintains all the business logic and data operations from the original Oracle Forms application while leveraging modern Java technologies and design patterns.

## 2. Architecture and Design Patterns

The application follows a layered architecture with clear separation of concerns:

- **Controller Layer**: Handles HTTP requests and responses
- **Service Layer**: Contains business logic and transaction management
- **Repository Layer**: Manages data access and persistence
- **Entity Layer**: Represents database tables as Java objects
- **DTO Layer**: Provides data transfer objects for API communication

Design patterns implemented:
- **Dependency Injection**: Using Spring's IoC container
- **Repository Pattern**: For data access abstraction
- **DTO Pattern**: For API request/response encapsulation
- **Service Layer Pattern**: For business logic encapsulation
- **Builder Pattern**: For complex object construction
- **Factory Pattern**: For object creation where appropriate

## 3. Package Structure and Organization

```
src/main/java/com/example/application/
├── config/                 # Application configuration classes
│   ├── SecurityConfig.java # Security configuration
│   ├── JpaConfig.java      # Database configuration
│   └── WebConfig.java      # Web-related configuration
├── controller/             # REST controllers
│   └── SinfController.java # Main controller for SINF501041 functionality
├── dto/                    # Data Transfer Objects
│   ├── request/            # Request DTOs
│   └── response/           # Response DTOs
├── entity/                 # JPA entities
│   └── SinfEntity.java     # Main entity mapped from Oracle Forms blocks
├── exception/              # Custom exceptions and error handling
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
├── repository/             # Spring Data JPA repositories
│   └── SinfRepository.java
├── service/                # Business logic services
│   ├── SinfService.java    # Service interface
│   └── impl/               # Service implementations
│       └── SinfServiceImpl.java
├── util/                   # Utility classes
│   ├── DateUtils.java
│   └── ValidationUtils.java
└── Application.java        # Main Spring Boot application class
```

## 4. Database Connectivity

The application uses Spring Data JPA with Hibernate as the ORM provider to interact with the database. The database connection is configured in `application.properties` or `application.yml`.

Key database features:
- Connection pooling with HikariCP
- Transaction management with Spring's `@Transactional`
- Entity auditing for creation and modification timestamps
- Database migration with Flyway

## 5. API Endpoints and Documentation

The API is documented using Swagger/OpenAPI. Once the application is running, you can access the API documentation at:
```
http://localhost:8080/swagger-ui.html
```

Key endpoints include:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET    | `/api/sinf/records` | Get all SINF records with pagination |
| GET    | `/api/sinf/records/{id}` | Get a specific SINF record by ID |
| POST   | `/api/sinf/records` | Create a new SINF record |
| PUT    | `/api/sinf/records/{id}` | Update an existing SINF record |
| DELETE | `/api/sinf/records/{id}` | Delete a SINF record |
| GET    | `/api/sinf/search` | Search SINF records with filtering |
| POST   | `/api/sinf/validate` | Validate SINF data without saving |

## 6. Installation and Setup

### Prerequisites
- Java Development Kit (JDK) 17
- Gradle 7.4+ or use the included Gradle wrapper
- Database server (Oracle, PostgreSQL, or MySQL)
- IDE with Spring Boot support (IntelliJ IDEA, Eclipse, VS Code)

### Dependencies
The project uses Gradle for dependency management. Key dependencies include:
- Spring Boot 2.7.x
- Spring Data JPA
- Spring Security
- Spring Web
- Flyway Migration
- Lombok
- MapStruct
- Swagger/OpenAPI

### Database Configuration
Configure your database connection in `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sinf_db
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### Environment Configuration
The application supports multiple environments through Spring profiles:
- `dev`: Development environment
- `test`: Testing environment
- `prod`: Production environment

To activate a specific profile, set the `spring.profiles.active` property:
```
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 7. Development Workflow

### Building the Project
```bash
# Clean and build the project
./gradlew clean build

# Build without running tests
./gradlew build -x test
```

### Running the Application
```bash
# Run with Gradle
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run the JAR file
java -jar build/libs/sinf-application-0.0.1-SNAPSHOT.jar
```

### Running Tests
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.example.application.service.SinfServiceTest"

# Run with test coverage report
./gradlew test jacocoTestReport
```

## 8. Key Features Implemented

- **Complete Data Management**: Full CRUD operations for all entities
- **Advanced Search Capabilities**: Flexible search with multiple criteria
- **Business Logic Migration**: All PL/SQL logic converted to Java services
- **Validation Rules**: Complex validation rules implemented in Java
- **Security**: JWT-based authentication and role-based authorization
- **Audit Logging**: Comprehensive logging of all data changes
- **Error Handling**: Robust exception handling with meaningful error messages
- **Pagination and Sorting**: Efficient data retrieval with pagination support
- **Caching**: Performance optimization with strategic caching
- **Asynchronous Processing**: Background processing for long-running tasks

## 9. Known Limitations or Issues

- **Legacy Data Compatibility**: Some complex data structures from Oracle Forms required simplification
- **Performance Considerations**: Certain complex queries may require optimization for large datasets
- **Browser Compatibility**: API designed for modern browsers; legacy browser support may be limited
- **Concurrent Editing**: Basic optimistic locking implemented, but complex concurrent editing scenarios may require additional handling

## 10. Troubleshooting

### Common Issues and Solutions

**Issue**: Application fails to start with database connection errors
**Solution**: Verify database credentials and ensure the database server is running

**Issue**: "No bean found" exceptions during startup
**Solution**: Check component scanning configuration and ensure all required beans are properly annotated

**Issue**: Hibernate schema validation errors
**Solution**: Run with `ddl-auto: update` temporarily to see the required schema changes, then create appropriate Flyway migrations

**Issue**: JWT token validation failures
**Solution**: Check that the JWT secret is consistent and that clocks are synchronized between servers

**Issue**: Slow query performance
**Solution**: Enable SQL logging and analyze queries; add appropriate indexes to the database

### Logging

The application uses SLF4J with Logback for logging. Configure logging levels in `src/main/resources/logback-spring.xml`.

To enable debug logging for troubleshooting:
```yaml
logging:
  level:
    com.example.application: DEBUG
    org.springframework: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Support

For additional support, please contact the development team or create an issue in the project repository.