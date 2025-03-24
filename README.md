# Insurance Claims Management System

## Project Overview

This repository contains the modernized version of the Insurance Claims Management System, converted from an Oracle Forms application to a modern Java + Angular stack. The application manages insurance claim adjustments, reserves, and related financial operations.

## Project Structure

The project follows a modern client-server architecture with a clear separation of concerns:

### Backend (Java SE 17 + Spring Boot)
```
app-backend/
├── src/
│   ├── main/
│   │   ├── java/com/insurance/claims/
│   │   │   ├── ClaimsApplication.java
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── domain/
│   │   │   ├── dto/
│   │   │   ├── exception/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   ├── service/
│   │   │   └── util/
│   │   └── resources/
│   └── test/
└── build.gradle
```

### Frontend (Angular 12)
```
app-frontend/
├── src/
│   ├── app/
│   │   ├── components/
│   │   ├── models/
│   │   ├── pages/
│   │   ├── services/
│   │   ├── shared/
│   │   └── app-routing.module.ts
│   ├── assets/
│   ├── environments/
│   └── index.html
├── angular.json
└── package.json
```

## Features and Functionality

- **Claims Management**: View and update insurance claim details
- **Policy Information**: Retrieve and display policy details
- **Coverage Management**: Handle insurance coverages, sums insured, and reserves
- **Reserve Adjustment**: Calculate and apply adjustments to claim reserves
- **Payment Processing**: Manage claim-related payments
- **Data Validation**: Validate user inputs with comprehensive business rules
- **Accounting Integration**: Generate accounting entries for claim adjustments
- **User Authentication**: Secure login and role-based access control
- **Responsive Design**: Optimized for desktop and tablet devices

## Prerequisites

### Backend
- Java SE 17 or higher
- Gradle 7.4+
- Oracle Database 19c or higher

### Frontend
- Node.js 14.x or higher
- npm 6.x or higher
- Angular CLI 12.x

## Setup Instructions

### Backend Setup

1. Clone the repository
2. Navigate to the `app-backend` directory
3. Configure database connection in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
   spring.datasource.username=claims_user
   spring.datasource.password=your_password
   spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
   ```
4. Build the application:
   ```bash
   ./gradlew build
   ```

### Frontend Setup

1. Navigate to the `app-frontend` directory
2. Install dependencies:
   ```bash
   npm install
   ```
3. Configure API endpoint in `src/environments/environment.ts`:
   ```typescript
   export const environment = {
     production: false,
     apiUrl: 'http://localhost:8080/api'
   };
   ```

## Running the Application

### Backend

1. Start the Spring Boot application:
   ```bash
   ./gradlew bootRun
   ```
   The backend will be available at http://localhost:8080

### Frontend

1. Start the Angular development server:
   ```bash
   ng serve
   ```
   The frontend will be available at http://localhost:4200

## Conversion Notes

### Key Differences from Original Oracle Forms Application

1. **Architecture**: Moved from monolithic Oracle Forms to a modern client-server architecture
2. **User Interface**: Redesigned with responsive Angular Material components
3. **Business Logic**: Migrated PL/SQL procedures to Java service classes
4. **Data Access**: Replaced Oracle Forms data blocks with JPA entities and repositories
5. **Authentication**: Implemented JWT-based authentication instead of database authentication
6. **Validation**: Client-side validation added to complement server-side validation

### Manual Changes Required

Before running the application, the following manual changes are required:

1. **Database Schema**: Execute the SQL scripts in `app-backend/src/main/resources/db/migration` to create the necessary tables and sequences
2. **Application Properties**: Update the database connection details in `application.properties`
3. **Security Configuration**: Configure CORS settings in `SecurityConfig.java` if deploying to different domains
4. **Environment Variables**: Set up environment-specific variables as needed

## Testing Procedures

### Backend Testing

1. Run unit tests:
   ```bash
   ./gradlew test
   ```
2. Run integration tests:
   ```bash
   ./gradlew integrationTest
   ```
3. Test REST API endpoints using Postman or curl (collection available in `docs/postman`)

### Frontend Testing

1. Run unit tests:
   ```bash
   ng test
   ```
2. Run end-to-end tests:
   ```bash
   ng e2e
   ```

## Troubleshooting

### Common Issues

1. **Database Connection Errors**:
   - Verify database credentials in `application.properties`
   - Ensure Oracle service is running
   - Check network connectivity to the database server

2. **Angular Build Errors**:
   - Clear npm cache: `npm cache clean --force`
   - Delete node_modules and reinstall: `rm -rf node_modules && npm install`

3. **Authentication Issues**:
   - Check JWT token expiration settings in `SecurityConfig.java`
   - Verify user credentials in the database

4. **API Connection Errors**:
   - Verify API URL in Angular environment files
   - Check CORS configuration in Spring Security

## Migration Details

The conversion process involved:

1. **Analysis**: Extracting form definitions, triggers, and business logic from Oracle Forms XML
2. **Mapping**: Creating equivalent Java and Angular components for each Oracle Forms element
3. **Business Logic Migration**: Converting PL/SQL to Java services
4. **UI Redesign**: Building responsive Angular components
5. **Testing**: Ensuring functional equivalence with the original application

## Credits

- Original Oracle Forms application developed by the Insurance Systems team
- Conversion project led by the Application Modernization team
- Special thanks to the QA team for comprehensive testing

## License

This project is proprietary and confidential. Unauthorized copying, distribution, or use is strictly prohibited.