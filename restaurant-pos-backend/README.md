# Restaurant POS Backend

A client-facing Restaurant POS System backend built with Spring Boot and PostgreSQL.

## Technologies
- **Java**: 21 (or 17 LTS)
- **Framework**: Spring Boot 3.4.3
- **Build Tool**: Maven
- **Database**: PostgreSQL 18
- **Database Name**: `restaurant_pos`
- **Database User**: `restaurant_app`
- **Architecture**: Modular Monolith

## Environment Setup (PowerShell)

Before starting the Spring Boot application, set the `DB_PASSWORD` and `JWT_SECRET` environment variables in your PowerShell session (do NOT hardcode secrets in source files):

```powershell
$env:DB_PASSWORD="your_actual_database_password"
$env:JWT_SECRET="your_actual_jwt_secret"
```

To generate a secure local development `JWT_SECRET` in PowerShell:
```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

## Running the Application

Ensure PostgreSQL is running locally on port `5432` with database `restaurant_pos` and user `restaurant_app`.

Run the application using Maven:

```powershell
mvn spring-boot:run
```

## Server Details
- **Server URL**: `http://localhost:8080`
- **Database URL**: `jdbc:postgresql://localhost:5432/restaurant_pos`
