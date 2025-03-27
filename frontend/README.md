# Angular Frontend for SINF Application

## Project Overview

This repository contains the Angular 12 frontend for the modernized SINF application, converted from the original Oracle Forms application. The frontend provides a responsive, user-friendly interface that maintains all the business functionality of the original application while leveraging modern web technologies.

## Angular Architecture and Design Patterns

The application follows these architectural principles:

- **Component-Based Architecture**: UI elements are broken down into reusable, self-contained components
- **Separation of Concerns**: Clear separation between presentation, business logic, and data access
- **Reactive Programming**: Using RxJS for handling asynchronous operations and data streams
- **Container/Presentational Pattern**: Smart (container) components manage state and logic, while presentational components focus on UI rendering
- **Dependency Injection**: Angular's DI system is used for service management and component communication

## Component Structure and Organization

The application is organized into the following structure:

```
src/
├── app/
│   ├── core/                 # Core functionality (services, guards, interceptors)
│   │   ├── guards/           # Route guards for authentication/authorization
│   │   ├── interceptors/     # HTTP interceptors for auth tokens, error handling
│   │   └── services/         # Singleton services (auth, API communication)
│   ├── features/             # Feature modules (organized by domain)
│   │   └── sinf/             # SINF module (converted from Oracle Forms)
│   │       ├── components/   # SINF-specific components
│   │       ├── models/       # SINF-specific models
│   │       ├── services/     # SINF-specific services
│   │       └── sinf.module.ts
│   ├── shared/               # Shared components, directives, pipes
│   │   ├── components/       # Reusable UI components
│   │   ├── directives/       # Custom directives
│   │   ├── pipes/            # Custom pipes
│   │   └── shared.module.ts
│   ├── models/               # Application-wide data models
│   ├── app-routing.module.ts # Main routing configuration
│   ├── app.component.ts      # Root component
│   └── app.module.ts         # Root module
├── assets/                   # Static assets (images, icons)
├── environments/             # Environment configuration
└── styles/                   # Global styles
```

## State Management Approach

The application uses a combination of state management techniques:

- **Service-based State Management**: For simpler features, Angular services with RxJS BehaviorSubjects maintain component state
- **Component State**: Local state for UI-specific concerns
- **URL State**: Router parameters for shareable/bookmarkable state
- **Local Storage/Session Storage**: For persisting user preferences and authentication tokens

## API Communication with Backend

Communication with the Java Spring Boot backend is handled through:

- **HTTP Service Layer**: Angular services encapsulate all API calls using HttpClient
- **Interceptors**: HTTP interceptors handle authentication tokens, error handling, and logging
- **Type-safe Models**: Strongly typed interfaces ensure data consistency between frontend and backend
- **Error Handling**: Centralized error handling with user-friendly error messages

Example from `sinf.service.ts`:

```typescript
@Injectable({
  providedIn: 'root'
})
export class SinfService {
  private apiUrl = environment.apiUrl + '/api/sinf';
  
  constructor(private http: HttpClient) {}
  
  getSinfData(id: string): Observable<SinfModel> {
    return this.http.get<SinfModel>(`${this.apiUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }
  
  saveSinfData(data: SinfModel): Observable<SinfModel> {
    return this.http.post<SinfModel>(this.apiUrl, data)
      .pipe(
        catchError(this.handleError)
      );
  }
  
  private handleError(error: HttpErrorResponse) {
    // Error handling logic
    return throwError(() => new Error('An error occurred. Please try again later.'));
  }
}
```

## Installation and Setup

### Prerequisites

- Node.js (v14.x or later)
- npm (v6.x or later)
- Angular CLI (v12.x)

### Dependencies

The project uses the following key dependencies:

- Angular Material: UI component library
- RxJS: Reactive programming library
- ngx-toastr: Toast notifications
- date-fns: Date manipulation library

### Installation Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/your-org/sinf-frontend.git
   cd sinf-frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Configure environment variables:
   - Copy `src/environments/environment.example.ts` to `src/environments/environment.ts`
   - Update the API URL and other configuration values

## Development Workflow

### Development Server

Run the development server:

```bash
ng serve
```

Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

### Building for Production

```bash
ng build --configuration production
```

The build artifacts will be stored in the `dist/` directory.

### Running Tests

#### Unit Tests

```bash
ng test
```

Unit tests are executed via [Karma](https://karma-runner.github.io).

#### End-to-End Tests

```bash
ng e2e
```

End-to-end tests are executed via [Protractor](http://www.protractortest.org/).

## Key Features Implemented

- **Authentication**: JWT-based authentication with token refresh
- **Form Management**: Dynamic form creation and validation based on the original Oracle Forms
- **Data Grid**: Advanced data grid with sorting, filtering, and pagination
- **Responsive Design**: Mobile-friendly UI that adapts to different screen sizes
- **Accessibility**: WCAG 2.1 AA compliant components
- **Internationalization**: Support for multiple languages
- **Theming**: Customizable theme based on corporate branding
- **Error Handling**: User-friendly error messages and logging
- **Offline Support**: Basic functionality when offline (where applicable)

## Known Limitations or Issues

- Internet Explorer is not supported
- Some complex Oracle Forms validations may behave slightly differently
- Print functionality requires additional browser permissions
- Large datasets may experience performance issues in older browsers

## Troubleshooting

### Common Issues

#### "Unable to connect to API" Error

- Verify the API URL in your environment configuration
- Ensure the backend server is running
- Check network connectivity and CORS settings

#### Authentication Issues

- Clear browser cache and local storage
- Verify that your authentication token hasn't expired
- Check browser console for specific error messages

#### Build Failures

- Update Angular CLI: `npm install -g @angular/cli@12`
- Clear npm cache: `npm cache clean --force`
- Delete node_modules and reinstall: `rm -rf node_modules && npm install`

### Getting Help

If you encounter issues not covered here:

1. Check the project's issue tracker
2. Contact the development team via the internal support channel
3. Consult the Angular documentation for framework-specific issues

---

This frontend implementation successfully converts all functionality from the original Oracle Forms application while providing a modern, responsive user experience. The architecture is designed for maintainability, scalability, and developer productivity.