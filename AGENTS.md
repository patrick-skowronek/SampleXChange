# SampleXChange - AI Agent Guidelines

This document provides guidelines for AI agents working on the SampleXChange codebase, a Spring Boot application for FHIR resource conversion between BBMRI and MII KDS profiles.

## Project Overview

- **Technology Stack**: Java 25, Spring Boot 4.0.2, HAPI FHIR 8.6.5, Maven
- **Purpose**: Convert FHIR resources between BBMRI Profiles and MII KDS Profiles for biobank data interchange
- **Architecture**: Component-based Spring application with FHIR mapping layers

## Build and Development Commands

```bash
# Build the project
mvn clean install

# Build without tests
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.arguments="--profile=MII2BBMRI"

# Run tests (when implemented)
mvn test

# Run single test class (when implemented)
mvn test -Dtest=ClassName

# Run single test method (when implemented)
mvn test -Dtest=ClassName#methodName

# Docker operations
docker build -t samplexchange .
docker-compose up
```

## Code Style Guidelines

### Package Structure
```
de.samply.samplexchange/
├── configuration/     # Spring configuration classes
├── converters/        # Terminology conversion utilities
├── mapper/fhir/mii/   # MII KDS source mappers
├── resources/         # Resource mapping classes
├── repository/fhir/   # FHIR server export
├── utils/auth/        # Keycloak token handling
├── utils/fhir/        # FHIR client and transfer utilities
└── writers/fhir/      # File export
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `SpecimenMapping`, `TemperatureConverter`)
- **Methods**: camelCase with descriptive names (e.g., `fromMiiToBbmri`, `toBbmri`)
- **Variables**: camelCase (e.g., `bbmriId`, `miiSubject`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `URL`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase with dots (e.g., `de.samply.samplexchange.converters`)

### Import Organization
1. Standard Java imports (`java.*`)
2. Third-party imports (`org.springframework.*`, `ca.uhn.fhir.model.*`)
3. Local imports (`de.samply.samplexchange.*`)

### Code Patterns
- **Lombok Usage**: Heavy use of `@Slf4j`, `@Data`, `@Getter`, `@Setter`
- **Spring Annotations**: `@Component`, `@Value`, `@Service`, `@Repository`
- **Documentation**: JavaDoc required for all public classes and methods
- **Access Modifiers**: Public for API, private for internal utilities

## Type Safety and Generics
- Use generic types in template classes: `ConvertClass<T1, T2>`
- Strong typing with FHIR model classes from HAPI FHIR
- Avoid raw types where possible

## Error Handling
- Use proper exception chaining with descriptive messages
- Log errors appropriately using `@Slf4j`

## FHIR-Specific Guidelines
- Use HAPI FHIR R4 structures exclusively
- Mappers convert MII KDS to bbmri.de (one direction)
- Handle extensions properly for custom data fields
- Follow FHIR resource structure conventions
- Validate resources before conversion

## Configuration Management
- Environment variables: `PROFILE`, `SOURCE_URL`, `SOURCE_AUTH_TYPE`, `SOURCE_DISABLE_SSL`, `TARGET_URL`, `TARGET_AUTH_TYPE`, `TARGET_DISABLE_SSL`, etc.
- Configuration in `application.yml`
- Supported profile: MII2BBMRI (see docs/adr/0001-mapping-architecture.md)
- Proper binding of environment-specific properties

## Testing Guidelines (When Implemented)
- Use JUnit 4.13.2 with Spring Boot Test framework
- Mockito for mocking dependencies
- Testcontainers for integration tests
- Follow AAA pattern (Arrange, Act, Assert)
- Test both successful conversions and error scenarios

## Architecture Patterns
- **Template Method**: Use `ConvertClass<T1, T2>` as base class
- **Strategy Pattern**: Different mappers for profile conversions
- **Factory Pattern**: Resource creation in writers
- **Dependency Injection**: Spring component management

## Development Best Practices
- Maintain separation of concerns between layers
- Use Spring's dependency injection properly
- Follow FHIR best practices for resource handling
- Ensure thread safety for component classes
- Add appropriate logging at INFO/DEBUG/ERROR levels
- Validate inputs at service boundaries

## Environment Setup
- Java 25 required
- Maven 3.6+ for building
- Docker for containerization
- Environment variables for configuration (see application.yml)

## When Making Changes
1. Understand the FHIR profile differences (BBMRI vs MII KDS)
2. Check for existing mapper patterns before creating new ones
3. Ensure proper exception handling for FHIR parsing errors
4. Add appropriate logging for debugging conversion issues
5. Test with both source and target profiles when applicable
6. Verify resource validity after conversion

## Common Pitfalls to Avoid
- Don't mix FHIR versions (use R4 consistently)
- Avoid hardcoded URLs or credentials
- Don't ignore SSL certificate validation in production
- Ensure proper resource ID handling during conversion
- Don't break existing mapper contracts when extending functionality