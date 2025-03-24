# Angular Frontend - Oracle Forms Migration

## Project Overview

This Angular 12 application represents the frontend portion of a migration from an Oracle Forms application to a modern web architecture. The application provides a responsive, user-friendly interface that maintains the core functionality of the original Oracle Forms while leveraging modern web technologies and design patterns.

The frontend communicates with a Java Spring Boot backend via RESTful APIs, handling data retrieval, form submissions, and business logic that was previously embedded in Oracle Forms.

## Architecture and Design Patterns

The application follows these key architectural principles:

- **Component-Based Architecture**: Modular components that encapsulate specific functionality
- **Separation of Concerns**: Clear distinction between presentation, business logic, and data access
- **Reactive Programming**: Using RxJS for handling asynchronous operations and data streams
- **Single Responsibility Principle**: Each component and service has a well-defined purpose
- **Presentational and Container Components**: Separation between UI rendering and data management

## Component Structure

```
src/
├── app/
│   ├── components/               # Reusable UI components
│   │   ├── toolbar/              # Application toolbar
│   │   ├── sidebar/              # Navigation sidebar
│   │   ├── data-table/           # Reusable data table component
│   │   └── form-controls/        # Custom form controls
│   ├── pages/                    # Page components (mapped from Oracle Forms)
│   │   ├── siniestro/            # Siniestro form components
│   │   ├── suma-asegurada/       # Suma Asegurada form components
│   │   └── saldo/                # Saldo form components
│   ├── models/                   # TypeScript interfaces
│   ├── services/                 # Services for API communication
│   │   ├── api/                  # API communication services
│   │   ├── auth/                 # Authentication services
│   │   └── utility/              # Utility services
│   ├── shared/                   # Shared modules, directives, pipes
│   ├── core/                     # Core application services, guards
│   └── app-routing.module.ts     # Application routing
```

## State Management

The application uses a combination of approaches for state management:

- **Service-based State**: Angular services with RxJS BehaviorSubjects for application-wide state
- **Component State**: Local state managed within components for UI-specific state
- **Angular Reactive Forms**: For form state management and validation

This approach was chosen over NgRx/Redux for its simplicity and appropriateness for the application's complexity level.

## API Communication

Communication with the backend is handled through dedicated Angular services that:

1. Make HTTP requests to the REST API endpoints
2. Transform data between frontend models and API formats
3. Handle error conditions and retries
4. Provide observables for components to consume

Example service pattern:

```typescript
@Injectable({
  providedIn: 'root'
})
export class SiniestroService {
  private apiUrl = environment.apiUrl + '/siniestros';

  constructor(private http: HttpClient) {}

  getSiniestro(id: number): Observable<Siniestro> {
    return this.http.get<Siniestro>(`${this.apiUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  // Additional methods...
  
  private handleError(error: HttpErrorResponse) {
    // Error handling logic
  }
}
```

## Installation and Setup

### Prerequisites

- Node.js (v14.x or later)
- npm (v6.x or later)
- Angular CLI (v12.x)

### Installation

```bash
# Clone the repository
git clone [repository-url]

# Navigate to the project directory
cd app-frontend

# Install dependencies
npm install
```

### Environment Configuration

The application uses environment files for configuration:

- `src/environments/environment.ts` - Development environment
- `src/environments/environment.prod.ts` - Production environment

Configure these files with the appropriate API URLs and other environment-specific settings.

## Development Workflow

### Development Server

```bash
# Start the development server
ng serve

# The application will be available at http://localhost:4200/
```

### Building for Production

```bash
# Build the application for production
ng build --configuration production

# The build artifacts will be stored in the dist/ directory
```

### Running Tests

```bash
# Run unit tests
ng test

# Run end-to-end tests
ng e2e

# Run tests with coverage report
ng test --code-coverage
```

## Key Features Implemented

- **Responsive UI**: Fully responsive design that works on desktop and mobile devices
- **Form Validation**: Complex validation rules migrated from Oracle Forms
- **Dynamic Forms**: Forms that adapt based on user input and business rules
- **Data Tables**: Interactive tables with sorting, filtering, and pagination
- **Authentication**: Secure login and session management
- **Error Handling**: Comprehensive error handling and user feedback
- **Accessibility**: WCAG 2.1 AA compliant components
- **Internationalization**: Support for multiple languages
- **PDF Generation**: Export of form data to PDF format

## Oracle Forms Mapping

The following Oracle Forms components were mapped to Angular equivalents:

| Oracle Forms Concept | Angular Implementation |
|----------------------|------------------------|
| Forms | Angular components/pages |
| Blocks | Component sections with related form controls |
| Items | Form controls (inputs, selects, etc.) |
| Triggers | Angular event handlers and services |
| Validations | Reactive Forms validators |
| Alerts | Angular Material snackbars/dialogs |
| Menus | Angular Material toolbar/sidenav |
| Master-Detail | Parent-child components with @Input/@Output |

## Known Limitations

- Internet Explorer is not supported
- Some complex Oracle Forms validations may behave slightly differently
- PDF exports may have minor formatting differences compared to original reports
- Session timeout handling requires user to re-authenticate

## Troubleshooting

### Common Issues

**Issue**: API connection errors
**Solution**: Verify the API URL in the environment files and ensure the backend server is running

**Issue**: "Module not found" errors
**Solution**: Run `npm install` to ensure all dependencies are installed

**Issue**: Form validation errors
**Solution**: Check browser console for specific validation errors and review the form validation logic

**Issue**: Slow performance with large datasets
**Solution**: Implement pagination or virtual scrolling for large data tables

### Debugging Tips

- Use Chrome DevTools for debugging JavaScript and inspecting network requests
- Enable source maps in production builds for easier debugging
- Check the browser console for errors and warnings
- Use Angular Augury browser extension for component debugging

## Contributing

Please follow the established coding standards and patterns when contributing to the project:

- Follow the Angular style guide
- Write unit tests for new functionality
- Use the existing folder structure for new components and services
- Document public methods and complex logic

## Contact

For questions or support, please contact the development team at [team-email@example.com]