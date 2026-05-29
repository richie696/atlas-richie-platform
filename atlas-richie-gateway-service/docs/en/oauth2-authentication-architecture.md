# Third-Party System OAuth2.0 Client Credentials Authentication Architecture Design Document

## 1. Overview

### 1.1 Background

This document describes the unified API access authentication mechanism provided for third-party systems, using the OAuth2.0 Client Credentials flow to achieve secure and efficient API access control.

### 1.2 Design Goals

- **Security**: Complete isolation between third-party systems and internal systems, with no mutual impact
- **Performance**: Gateway reads configuration from Redis cache only, zero database access
- **Maintainability**: Unified management of third-party client configuration through the management interface
- **Scalability**: Support for multi-third-party system integration with flexible configuration

### 1.3 Technology Selection

- **Authentication Protocol**: OAuth2.0 Client Credentials flow (RFC 6749)
- **Token Format**:
  - **access_token**: JWT (JSON Web Token, RFC 7519) format, valid for 1 hour
  - **refresh_token**: Random string format, stored in Redis, valid for 30 days
  - HMAC256 signature algorithm ensures access_token security
  - Consistent with internal system JWT token technology stack, reusing existing utility classes
- **Token Renewal Mechanism**: Standard OAuth2.0 refresh_token solution
  - Third-party systems actively call the refresh endpoint to obtain new tokens
  - Conforms to OAuth2.0 standard practices, allowing third-party systems to proactively control renewal timing
- **Configuration Management**: general-service + database + Redis cache
- **Database Version Management**: Liquibase
- **Deployment Solution**: Independent deployment of third-party gateway

---

## 2. Overall Architecture

### 2.1 System Architecture Diagram

```mermaid
graph TB
    subgraph "Management Side"
        A[Management Page<br/>HTML/Frontend] --> B[general-service<br/>Management API]
        B --> C1[(Database<br/>third_party_company)]
        B --> C2[(Database<br/>third_party_client)]
        B --> D[(Redis Cache<br/>Real-time Sync)]
        E[Scheduled Sync Task] --> D
        C1 --> E
        C2 --> E
    end
    
    subgraph "Gateway Side"
        F[Third-Party Gateway<br/>Independent Deployment] --> D
        F --> G[InterfaceAuthFilter<br/>OAuth2.0 Authentication]
        G --> H[Backend Service Cluster]
    end
    
    subgraph "Third-Party Systems"
        I[Third-Party System A] --> F
        J[Third-Party System B] --> F
        K[Third-Party System C] --> F
    end
    
    style A fill:#e1f5ff
    style B fill:#e1f5ff
    style C1 fill:#fff4e1
    style C2 fill:#fff4e1
    style D fill:#ffe1f5
    style E fill:#e1f5ff
    style F fill:#e1ffe1
    style G fill:#e1ffe1
    style H fill:#f5e1ff
```

### 2.2 Core Component Description

| Component | Responsibility | Technology Implementation |
|-----------|----------------|--------------------------|
| **Management Page** | Provides CRUD interface for third-party company and client configuration | HTML + JavaScript / Vue / React |
| **general-service** | Manages third-party company and client configuration, provides REST API | Spring Boot + MyBatis-Plus |
| **Database** | Persistently stores third-party company and client configuration | MySQL + Liquibase |
| **Redis Cache** | Stores client configuration for fast gateway access | Redis |
| **Scheduled Sync Task** | Periodically performs full sync from database to Redis (fallback guarantee) | Spring @Scheduled |
| **Third-Party Gateway** | Independently deployed gateway service handling third-party system requests | Spring Cloud Gateway |
| **InterfaceAuthFilter** | OAuth2.0 authentication filter validating access_token | Gateway Filter |

---

## 3. Management Side Solution

### 3.1 Business Flow

```mermaid
flowchart TD
    A[Admin logs into management page] --> B{Select Operation}
    
    B -->|Create Company| C1[Fill in company information]
    C1 --> D1[Submit creation request]
    D1 --> E1[general-service receives request]
    E1 --> F1[Validate unified social credit code uniqueness]
    F1 --> G1[Save company information to database]
    G1 --> H1[Return company ID]
    H1 --> I1[Display creation success]
    
    B -->|Create Client| C2[Select associated company]
    C2 --> C3[Fill in client information]
    C3 --> D2[Submit creation request]
    D2 --> E2[general-service receives request]
    E2 --> F2[Validate company ID exists]
    F2 --> G2[Generate clientId and clientSecret]
    G2 --> H2[Encrypt clientSecret]
    H2 --> I2[Save to database]
    I2 --> J2[Real-time sync to Redis]
    J2 --> K2[Return clientId and clientSecret]
    K2 --> L2[Display to admin]
    
    B -->|Update Company| L1[Select company to update]
    L1 --> M1[Modify company information]
    M1 --> N1[Submit update request]
    N1 --> O1[general-service receives request]
    O1 --> P1[Update database]
    P1 --> Q1[Return update result]
    
    B -->|Update Client| L2[Select client to update]
    L2 --> M2[Modify configuration information]
    M2 --> N2[Submit update request]
    N2 --> O2[general-service receives request]
    O2 --> P2[Update database]
    P2 --> Q2[Real-time sync to Redis]
    Q2 --> R2[Return update result]
    
    B -->|Delete Company| S1[Select company to delete]
    S1 --> T1[Check for associated clients]
    T1 -->|Has associations| U1[Prompt to delete associated clients first]
    T1 -->|No associations| V1[Confirm deletion]
    V1 --> W1[general-service receives request]
    W1 --> X1[Logical delete database record]
    X1 --> Y1[Return deletion result]
    
    B -->|Delete Client| S2[Select client to delete]
    S2 --> T2[Confirm deletion]
    T2 --> U2[general-service receives request]
    U2 --> V2[Logical delete database record]
    V2 --> W2[Delete cache from Redis]
    W2 --> X2[Return deletion result]
    
    B -->|Reset Secret| Y[Select client to reset]
    Y --> Z[Generate new clientSecret]
    Z --> AA[Update database]
    AA --> AB[Real-time sync to Redis]
    AB --> AC[Return new secret]
    
    style A fill:#e1f5ff
    style E1 fill:#fff4e1
    style E2 fill:#fff4e1
    style G1 fill:#fff4e1
    style I2 fill:#fff4e1
    style J2 fill:#ffe1f5
    style O1 fill:#fff4e1
    style O2 fill:#fff4e1
    style P1 fill:#fff4e1
    style P2 fill:#fff4e1
    style Q2 fill:#ffe1f5
    style W1 fill:#fff4e1
    style W2 fill:#fff4e1
    style X1 fill:#fff4e1
    style V2 fill:#fff4e1
    style W2 fill:#ffe1f5
```

### 3.2 Data Flow Diagram

```mermaid
flowchart LR
    subgraph "Management Operations"
        A[Admin operations] --> B[Management page]
    end
    
    subgraph "general-service"
        B --> C1[ThirdPartyCompanyController]
        B --> C2[ThirdPartyClientController]
        C1 --> D1[ThirdPartyCompanyService]
        C2 --> D2[ThirdPartyClientService]
        D1 --> E1[ThirdPartyCompanyMapper]
        D2 --> E2[ThirdPartyClientMapper]
    end
    
    subgraph "Data Storage"
        E1 --> F1[(Database<br/>third_party_company)]
        E2 --> F2[(Database<br/>third_party_client)]
        D2 --> G[("Redis Cache<br/>third-party-client:{clientId}")]
    end
    
    subgraph "Scheduled Sync"
        H[Scheduled Sync Task<br/>Every 5 minutes] --> F
        H --> G
    end
    
    style A fill:#e1f5ff
    style B fill:#e1f5ff
    style C1 fill:#fff4e1
    style C2 fill:#fff4e1
    style D1 fill:#fff4e1
    style D2 fill:#fff4e1
    style E1 fill:#fff4e1
    style E2 fill:#fff4e1
    style F1 fill:#fff4e1
    style F2 fill:#fff4e1
    style G fill:#ffe1f5
    style H fill:#e1f5ff
```

### 3.3 Management Side Sequence Diagram

```mermaid
sequenceDiagram
    participant Admin as Admin
    participant UI as Management Page
    participant CompanyController as ThirdPartyCompanyController
    participant ClientController as ThirdPartyClientController
    participant CompanyService as ThirdPartyCompanyService
    participant ClientService as ThirdPartyClientService
    participant CompanyMapper as ThirdPartyCompanyMapper
    participant ClientMapper as ThirdPartyClientMapper
    participant DB as Database
    participant Redis as Redis Cache
    participant Task as Scheduled Sync Task
    
    Note over Admin,Task: Company Creation Flow
    Admin->>UI: Fill in company information and submit
    UI->>CompanyController: POST /api/gateway/third-party-company
    CompanyController->>CompanyService: create(dto)
    CompanyService->>CompanyService: Validate unified social credit code uniqueness
    CompanyService->>CompanyMapper: insert(company)
    CompanyMapper->>DB: INSERT INTO third_party_company
    DB-->>CompanyMapper: Return insert result
    CompanyMapper-->>CompanyService: Return company object
    CompanyService-->>CompanyController: Return company ID
    CompanyController-->>UI: Return creation result
    UI-->>Admin: Display creation success
    
    Note over Admin,Task: Client Creation Flow
    Admin->>UI: Select company and fill in client information
    UI->>ClientController: POST /api/gateway/third-party-client
    ClientController->>ClientService: create(dto)
    ClientService->>ClientService: Validate companyId exists
    ClientService->>ClientService: Generate clientId and clientSecret
    ClientService->>ClientService: Encrypt clientSecret
    ClientService->>ClientMapper: insert(client)
    ClientMapper->>DB: INSERT INTO third_party_client
    DB-->>ClientMapper: Return insert result
    ClientMapper-->>ClientService: Return client object
    ClientService->>Redis: Real-time sync configuration to cache
    Redis-->>ClientService: Sync success
    ClientService-->>ClientController: Return clientId and clientSecret
    ClientController-->>UI: Return creation result
    UI-->>Admin: Display clientId and clientSecret
    
    Note over Admin,Task: Scheduled Sync Task (Fallback Guarantee)
    Task->>DB: Query all enabled clients
    DB-->>Task: Return client list
    Task->>Task: Decrypt clientSecret
    Task->>Redis: Full sync to cache
    Redis-->>Task: Sync complete
```

---

## 4. Gateway Side Solution

### 4.1 Business Flow

```mermaid
flowchart TD
    A[Third-party system request] --> B[Third-party gateway receives request]
    B --> C{Request Type}
    
    C -->|Get access_token| D[OAuth2.0 Token Endpoint]
    D --> E{grant_type}
    E -->|client_credentials| F[InterfaceAuthFilter validation]
    F --> G{Validate clientId and clientSecret}
    G -->|Validation failed| H[Return 401 Unauthorized]
    G -->|Validation success| I[Read client configuration from Redis]
    I --> J[Generate JWT access_token and refresh_token]
    J --> K[Return access_token, refresh_token and expiration time]
    
    E -->|refresh_token| L[Validate refresh_token]
    L --> M{refresh_token valid?}
    M -->|Invalid| N[Return 401 Unauthorized]
    M -->|Valid| O[Generate new access_token and refresh_token]
    O --> P[Return new tokens]
    
    C -->|Access business endpoint| Q[Business endpoint request]
    Q --> R[InterfaceAuthFilter intercepts]
    R --> S{Check access_token}
    S -->|Missing token| T[Return 401 Unauthorized]
    S -->|Token invalid| U[Return 401 Unauthorized]
    S -->|Token expired| V[Return 401 Unauthorized<br/>Prompt to use refresh_token]
    S -->|Token valid| W[Continue processing]
    W --> X[Forward to backend service]
    X --> Y[Return business result]
    
    style A fill:#e1ffe1
    style B fill:#e1ffe1
    style D fill:#e1ffe1
    style F fill:#e1ffe1
    style I fill:#ffe1f5
    style J fill:#e1ffe1
    style L fill:#e1ffe1
    style R fill:#e1ffe1
    style X fill:#f5e1ff
```

### 4.2 Data Flow Diagram

```mermaid
flowchart LR
    subgraph "Third-Party Systems"
        A[Third-Party Systems] --> B[HTTP Request]
    end
    
    subgraph "Third-Party Gateway"
        B --> C[InterfaceAuthFilter]
        C --> D{Request Type}
        D -->|Get token| E[Validate clientId/Secret]
        D -->|Refresh token| F[Validate refresh_token]
        D -->|Business request| G[Validate access_token]
        E --> H[Read configuration from Redis]
        F --> I[(Redis Cache<br/>refresh_token)]
        G --> H
        H --> J[(Redis Cache<br/>Client Configuration)]
        E --> K[Generate JWT access_token<br/>and refresh_token]
        F --> L[Generate new tokens]
        G --> M{token valid?}
        M -->|Yes| N[Continue processing]
        M -->|No| O[Return 401]
        K --> P[Store refresh_token to Redis]
        L --> P
    end
    
    subgraph "Backend Services"
        N --> Q[Backend Service Cluster]
        Q --> R[Return business result]
    end
    
    style A fill:#e1ffe1
    style C fill:#e1ffe1
    style H fill:#ffe1f5
    style J fill:#ffe1f5
    style I fill:#ffe1f5
    style K fill:#e1ffe1
    style Q fill:#f5e1ff
```

### 4.3 Gateway Side Sequence Diagram

```mermaid
sequenceDiagram
    participant ThirdParty as Third-Party System
    participant Gateway as Third-Party Gateway
    participant Filter as InterfaceAuthFilter
    participant Redis as Redis Cache
    participant Backend as Backend Service
    
    Note over ThirdParty,Backend: Get access_token Flow
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=client_credentials<br/>client_id=xxx<br/>client_secret=yyy
    Gateway->>Filter: Route to Token endpoint
    Filter->>Redis: Read client configuration<br/>third-party-client:{clientId}
    Redis-->>Filter: Return client configuration
    Filter->>Filter: Validate clientSecret
    alt Validation failed
        Filter-->>ThirdParty: 401 Unauthorized
    else Validation success
        Filter->>Filter: Generate JWT access_token<br/>Generate refresh_token
        Filter->>Redis: Store refresh_token<br/>refresh-token:{token}
        Redis-->>Filter: Store success
        Filter-->>ThirdParty: Return access_token, refresh_token<br/>and expiration time
    end
    
    Note over ThirdParty,Backend: Refresh access_token Flow
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=refresh_token<br/>refresh_token=zzz
    Gateway->>Filter: Route to Token endpoint
    Filter->>Redis: Validate refresh_token<br/>refresh-token:{token}
    Redis-->>Filter: Return validation result
    alt refresh_token invalid or expired
        Filter-->>ThirdParty: 401 Unauthorized
    else refresh_token valid
        Filter->>Filter: Generate new access_token<br/>Generate new refresh_token
        Filter->>Redis: Delete old refresh_token<br/>Store new refresh_token
        Filter-->>ThirdParty: Return new access_token<br/>and refresh_token
    end
    
    Note over ThirdParty,Backend: Access Business Endpoint Flow
    ThirdParty->>Gateway: GET /api/business/xxx<br/>Authorization: Bearer {access_token}
    Gateway->>Filter: Intercept request
    Filter->>Filter: Extract access_token
    Filter->>Filter: Validate token validity
    alt Token invalid or expired
        Filter-->>ThirdParty: 401 Unauthorized<br/>Prompt to use refresh_token
    else Token valid
        Filter->>Backend: Forward request to backend service
        Backend-->>Filter: Return business result
        Filter-->>ThirdParty: Return business result
    end
```

---

## 5. Third-Party System and Gateway Interaction Flow

### 5.1 Complete Interaction Swimlane Diagram

```mermaid
sequenceDiagram
    participant Admin as Admin
    participant GeneralService as general-service
    participant DB as Database
    participant Redis as Redis Cache
    participant ThirdParty as Third-Party System
    participant Gateway as Third-Party Gateway
    participant Backend as Backend Service
    
    Note over Admin,Backend: Phase 1: Configuration Management
    Admin->>GeneralService: Create third-party company
    GeneralService->>DB: Save company information
    DB-->>GeneralService: Return company ID
    GeneralService-->>Admin: Return company ID
    
    Admin->>GeneralService: Create third-party client (associated company)
    GeneralService->>GeneralService: Validate company ID exists
    GeneralService->>GeneralService: Generate clientId and clientSecret
    GeneralService->>DB: Save client configuration
    DB-->>GeneralService: Save success
    GeneralService->>Redis: Real-time sync configuration
    Redis-->>GeneralService: Sync success
    GeneralService-->>Admin: Return clientId and clientSecret
    
    Note over Admin,Backend: Phase 2: Get access_token
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=client_credentials<br/>client_id=xxx<br/>client_secret=yyy
    Gateway->>Redis: Read client configuration<br/>third-party-client:xxx
    Redis-->>Gateway: Return client configuration
    Gateway->>Gateway: Validate clientSecret
    alt Validation success
        Gateway->>Gateway: Generate JWT access_token<br/>(valid for 1 hour)
        Gateway-->>ThirdParty: Return access_token<br/>{"access_token":"xxx",<br/>"expires_in":3600}
    else Validation failed
        Gateway-->>ThirdParty: 401 Unauthorized
    end
    
    Note over Admin,Backend: Phase 3: Refresh access_token (Active Renewal)
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=refresh_token<br/>refresh_token=zzz
    Gateway->>Redis: Validate refresh_token
    Redis-->>Gateway: Return validation result
    alt refresh_token valid
        Gateway->>Gateway: Generate new access_token<br/>and refresh_token
        Gateway->>Redis: Update refresh_token
        Gateway-->>ThirdParty: Return new access_token<br/>and refresh_token
    else refresh_token invalid
        Gateway-->>ThirdParty: 401 Unauthorized
    end
    
    Note over Admin,Backend: Phase 4: Access Business Endpoints
    ThirdParty->>Gateway: GET /api/business/xxx<br/>Authorization: Bearer {access_token}
    Gateway->>Gateway: Validate access_token
    alt Token valid
        Gateway->>Backend: Forward request to backend service
        Backend-->>Gateway: Return business result
        Gateway-->>ThirdParty: Return business result
    else Token invalid or expired
        Gateway-->>ThirdParty: 401 Unauthorized<br/>Prompt to use refresh_token to refresh
    end
    
    Note over Admin,Backend: Phase 5: Scheduled Sync (Fallback Guarantee)
    GeneralService->>DB: Query all enabled clients
    DB-->>GeneralService: Return client list
    GeneralService->>Redis: Full sync to cache
    Redis-->>GeneralService: Sync complete
```

### 5.2 Third-Party System Interaction Sequence Diagram

```mermaid
sequenceDiagram
    participant ThirdParty as Third-Party System
    participant Gateway as Third-Party Gateway
    participant Redis as Redis Cache
    participant Backend as Backend Service
    
    Note over ThirdParty,Backend: First Access: Get access_token and refresh_token
    ThirdParty->>Gateway: 1. POST /api/oauth2/token<br/>Body: grant_type=client_credentials<br/>client_id=xxx<br/>client_secret=yyy
    Gateway->>Redis: 2. Read client configuration
    Redis-->>Gateway: 3. Return configuration
    Gateway->>Gateway: 4. Validate clientSecret
    Gateway->>Gateway: 5. Generate JWT access_token<br/>Generate refresh_token
    Gateway->>Redis: 6. Store refresh_token<br/>refresh-token:{token}
    Gateway-->>ThirdParty: 7. Return token<br/>{"access_token":"eyJ...",<br/>"refresh_token":"zzz...",<br/>"token_type":"Bearer",<br/>"expires_in":3600}
    
    Note over ThirdParty,Backend: Business Request: Use access_token
    ThirdParty->>Gateway: 8. GET /api/business/xxx<br/>Header: Authorization: Bearer {access_token}
    Gateway->>Gateway: 9. Extract and validate access_token
    alt Token valid and not expired
        Gateway->>Backend: 10. Forward request to backend service<br/>Header: X-Third-Party-Client-Id
        Backend-->>Gateway: 11. Return business result
        Gateway-->>ThirdParty: 12. Return business result
    else Token invalid or expired
        Gateway-->>ThirdParty: 13. 401 Unauthorized<br/>{"error":"invalid_token",<br/>"error_description":"Token expired"}
    end
    
    Note over ThirdParty,Backend: Token about to expire: Active refresh (Recommended)
    ThirdParty->>Gateway: 14. POST /api/oauth2/token<br/>grant_type=refresh_token<br/>refresh_token=zzz
    Gateway->>Redis: 15. Validate refresh_token
    Redis-->>Gateway: 16. Return validation result
    alt refresh_token valid
        Gateway->>Gateway: 17. Generate new access_token<br/>Generate new refresh_token
        Gateway->>Redis: 18. Delete old refresh_token<br/>Store new refresh_token
        Gateway-->>ThirdParty: 19. Return new token<br/>{"access_token":"new_xxx",<br/>"refresh_token":"new_zzz",<br/>"expires_in":3600}
    else refresh_token invalid or expired
        Gateway-->>ThirdParty: 20. 401 Unauthorized<br/>{"error":"invalid_grant"}
    end
    
    Note over ThirdParty,Backend: Continue accessing with new token
    ThirdParty->>Gateway: 21. GET /api/business/xxx<br/>Header: Authorization: Bearer {new_access_token}
    Gateway->>Gateway: 22. Validate new access_token
    Gateway->>Backend: 23. Forward request
    Backend-->>Gateway: 24. Return business result
    Gateway-->>ThirdParty: 25. Return business result
```

---

## 6. Data Model Design

### 6.1 Database Table Structure

#### 6.1.1 Third-Party Company Information Table

**Table Name: `third_party_company`**

| Field Name | Type | Description | Constraint |
|-----------|------|-------------|------------|
| id | BIGINT | Primary key ID | PRIMARY KEY |
| company_name | VARCHAR(256) | Company name | NOT NULL |
| company_code | VARCHAR(64) | Unified social credit code | UNIQUE, NOT NULL |
| contact_name | VARCHAR(64) | Contact person name | |
| contact_email | VARCHAR(128) | Contact email | |
| contact_phone | VARCHAR(32) | Contact phone | |
| company_status | VARCHAR(32) | Company status | DEFAULT 'NORMAL' |
| create_id | VARCHAR(64) | Creator ID | |
| create_time | DATETIME | Create time | |
| update_id | VARCHAR(64) | Updater ID | |
| update_time | DATETIME | Update time | |
| deleted | TINYINT(1) | Delete flag | DEFAULT 0 |

**Indexes:**
- `idx_company_code` (company_code) - Unified social credit code unique index
- `idx_company_name` (company_name) - Company name index
- `idx_company_status` (company_status) - Company status index
- `idx_deleted` (deleted) - Delete flag index

**Notes:**
- One company can associate with multiple clients (client_id)
- Company information is relatively stable with low change frequency
- Must check for associated clients before deleting a company

#### 6.1.2 Third-Party Client Table

**Table Name: `third_party_client`**

| Field Name | Type | Description | Constraint |
|-----------|------|-------------|------------|
| id | BIGINT | Primary key ID | PRIMARY KEY |
| company_id | BIGINT | Associated company ID | FOREIGN KEY, NOT NULL |
| client_id | VARCHAR(64) | Client ID (appId) | UNIQUE, NOT NULL |
| client_secret | VARCHAR(256) | Client secret (encrypted storage) | NOT NULL |
| client_name | VARCHAR(128) | Client name | NOT NULL |
| description | VARCHAR(512) | Description | |
| scopes | VARCHAR(512) | Permission scopes (JSON array or comma-separated) | |
| enabled | TINYINT(1) | Whether enabled | DEFAULT 1 |
| ip_whitelist | TEXT | IP whitelist (JSON array) | |
| token_valid_duration | INT | Token validity duration (hours) | DEFAULT 24 |
| expiration_renewal_time | INT | Pre-expiration renewal time (minutes) | DEFAULT 60 |
| contact_name | VARCHAR(64) | Contact person name | |
| contact_email | VARCHAR(128) | Contact email | |
| contact_phone | VARCHAR(32) | Contact phone | |
| create_id | VARCHAR(64) | Creator ID | |
| create_time | DATETIME | Create time | |
| update_id | VARCHAR(64) | Updater ID | |
| update_time | DATETIME | Update time | |
| deleted | TINYINT(1) | Delete flag | DEFAULT 0 |

**Indexes:**
- `idx_company_id` (company_id) - Company ID index (foreign key)
- `idx_client_id` (client_id) - Client ID unique index
- `idx_enabled` (enabled) - Enabled status index
- `idx_deleted` (deleted) - Delete flag index

**Foreign Key Constraints:**
- `fk_client_company` (company_id) REFERENCES `third_party_company`(id)

**Notes:**
- Each client must associate with one company
- One company can have multiple clients
- Must delete or transfer all associated clients before deleting a company

### 6.2 Redis Cache Structure

#### 6.2.1 Client Configuration Cache

**Key Format:** `third-party-client:{clientId}`

**Value Structure (Hash):**
```
third-party-client:client-001
  ├── clientId: "client-001"
  ├── clientSecret: "plain_secret_xxx"  (plain text, for validation)
  ├── clientName: "Third-Party System A"
  ├── scopes: "read,write"
  ├── enabled: "true"
  ├── ipWhitelist: "[\"192.168.1.1\", \"10.0.0.0/8\"]"
  ├── tokenValidDuration: "1"  (hours)
  └── refreshTokenValidDuration: "720"  (hours, 30 days)
```

#### 6.2.2 Refresh Token Cache

**Key Format:** `refresh-token:{token_value}`

**Value Structure (Hash):**
```
refresh-token:abc123def456...
  ├── clientId: "client-001"
  ├── scopes: "read,write"
  ├── issuedAt: "1234567890"
  ├── expiresAt: "1237246290"  (30 days later)
  └── enabled: "true"
```

**TTL Settings:** Consistent with refresh_token validity period (e.g., 30 days), Redis auto-cleanup on expiration

### 6.3 JWT Token Structure

#### 6.3.1 Token Format Description

The `access_token` issued by the OAuth2.0 Client Credentials flow uses **JWT (JSON Web Token)** format. Although the OAuth2.0 specification (RFC 6749) does not mandate that access_token must be JWT format, using JWT format provides the following advantages:

- **Self-contained**: Token itself contains client information (client_id, scopes, etc.), no database query needed for validation
- **Stateless**: Gateway does not need to store token state, supports horizontal scaling and high concurrency
- **Verifiable**: HMAC256 signature algorithm verifies token integrity and authenticity
- **Parsable**: Client information and permission scopes can be extracted directly from the token
- **Technology Unification**: Consistent with internal system JWT token technology stack, reusing existing `JwtUtils` utility class

#### 6.3.2 JWT Structure Definition

**Header:**
```json
{
  "alg": "HS256",    // Signature algorithm: HMAC SHA256
  "typ": "JWT"       // Token type: JSON Web Token
}
```

**Payload:**
```json
{
  "client_id": "client-001",              // Client ID (required)
  "scopes": ["read", "write"],            // Permission scopes (required)
  "iat": 1234567890,                      // Issued At
  "exp": 1234654290,                      // Expiration Time
  "jti": "550e8400-e29b-41d4-a716-446655440000",  // JWT ID (for replay prevention)
  "iss": "Richie Gateway",                // Issuer
  "sub": "client_credentials",            // Subject, identifies OAuth2.0 flow
  "aud": "api.rydeen.com"                 // Audience, API service address
}
```

#### 6.3.5 Refresh Token Description

**Refresh Token Characteristics:**
- **Format**: Random string (not JWT), stored in Redis
- **Validity**: Usually longer than access_token (e.g., 30 days)
- **Purpose**: Used to obtain new access_token without re-providing client_secret
- **Storage Location**: Redis, Key format: `refresh-token:{token_value}`
- **Security Requirement**: refresh_token must be securely stored and not leaked

**Refresh Token Storage Structure (Redis):**
```
refresh-token:abc123def456...
  ├── clientId: "client-001"
  ├── scopes: "read,write"
  ├── issuedAt: 1234567890
  ├── expiresAt: 1237246290  (30 days later)
  └── enabled: "true"
```

**Signature:**
```
HMACSHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  secret
)
```

#### 6.3.3 Differences from Internal System JWT

| Feature | Internal System JWT | Third-Party System JWT |
|---------|---------------------|------------------------|
| **User Identifier** | `username` | `client_id` |
| **Tenant Information** | `tenantCode` (optional) | None |
| **Permission Scope** | User role permissions | `scopes` (OAuth2.0 standard) |
| **Signing Key** | Internal system dedicated key | Third-party system dedicated key |
| **Subject** | `"Interactive token"` | `"client_credentials"` |
| **Audience** | Username | API service address |
| **Purpose** | User login authentication | OAuth2.0 Client Credentials authentication |

#### 6.3.4 Token Generation Example

```java
// Generate JWT access_token for third-party systems
public static String generateThirdPartyAccessToken(
    String clientId, 
    List<String> scopes, 
    String secret, 
    long expiredTime) {
    
    var algorithm = Algorithm.HMAC256(secret);
    var builder = JWT.create()
            .withClaim("client_id", clientId)
            .withClaim("scopes", scopes)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(expiredTime))
            .withJWTId(UUID.randomUUID().toString())
            .withIssuer("Richie Gateway")
            .withSubject("client_credentials")
            .withAudience("api.rydeen.com");
    
    return builder.sign(algorithm);
}

// Generate refresh_token (random string)
public static String generateRefreshToken() {
    return UUID.randomUUID().toString().replace("-", "") + 
           SecureRandom.getInstanceStrong().nextLong();
}
```

---

## 7. Security Design

### 7.1 Key Management

- **Database Storage**: `clientSecret` is encrypted (using platform encryption utility)
- **Redis Cache**: Stores plaintext `clientSecret` (only for fast validation)
- **Transport Security**: All endpoints use HTTPS

### 7.2 Access Control

- **IP Whitelist**: Supports configurable IP whitelist (optional)
- **Permission Scope**: Access control through `scopes`
- **Enable/Disable**: Supports dynamic enable/disable of clients

### 7.3 Token Security

#### 7.3.1 JWT Token Security Features

- **Signature Verification**: Uses HMAC256 algorithm to sign tokens ensuring tokens are not tampered with
- **Validity Period Control**: access_token default 1 hour validity, controlled through `exp` field
- **Replay Attack Prevention**: Each token uniquely identified through `jti` (JWT ID) field, supports blacklist mechanism
- **Key Isolation**: Third-party systems use independent signing key, separated from internal system JWT key
- **Token Blacklist**: Supports adding tokens to blacklist (optional), used for proactive token revocation

#### 7.3.2 Refresh Token Security Features

- **Independent Storage**: refresh_token stored in Redis, separated from access_token
- **Longer Validity**: refresh_token validity is usually 30 days, longer than access_token
- **One-time Use**: Each use of refresh_token to obtain new token invalidates the old refresh_token
- **Client Binding**: refresh_token bound to client_id, preventing cross-client usage
- **Scope Preservation**: New access_token obtained using refresh_token preserves original scopes
- **Proactive Revocation**: Supports adding refresh_token to blacklist for immediate invalidation
- **Concurrent Refresh Protection**: Uses Redis distributed lock + Lua script atomic operations to prevent security issues from concurrent refresh
  - Issue: If third-party system concurrently calls refresh endpoint, multiple requests may simultaneously pass validation and generate multiple new refresh_tokens
  - Solution: Uses distributed lock to ensure same refresh_token can only be processed by one request, uses Lua script to ensure atomic validation and deletion

#### 7.3.3 Token Validation Flow

**Access Token Validation Flow:**
1. **Extract Token**: Extract from request header `Authorization: Bearer {access_token}`
2. **Verify Signature**: Use third-party system dedicated key to verify token signature
3. **Verify Validity Period**: Check `exp` field ensuring token is not expired
4. **Verify Client**: Extract `client_id` from token, verify client is enabled
5. **Verify Permissions**: Check `scopes` field, verify client has permission to access the resource
6. **Blacklist Check**: Check if token is in blacklist (optional)

**Refresh Token Validation Flow (with Concurrent Protection):**
1. **Acquire Distributed Lock**: Use `refresh-token-lock:{token}` as lock key to prevent concurrent refresh
2. **Extract Token**: Extract from request parameter `refresh_token`
3. **Atomic Validation and Deletion**: Use Lua script to atomically validate refresh_token existence and delete from Redis
4. **Verify Client**: Verify refresh_token bound client_id matches
5. **Verify Enabled Status**: Check if refresh_token is disabled or revoked
6. **Generate New Token**: After validation passes, generate new access_token and refresh_token
7. **Update Storage**: Store new refresh_token
8. **Release Lock**: Release distributed lock

**Concurrent Refresh Protection Implementation Example:**
```java
public TokenResponse refreshToken(String refreshToken) {
    String lockKey = "refresh-token-lock:" + refreshToken;
    
    // 1. Acquire distributed lock (prevent concurrent refresh)
    if (!GlobalCache.pessimisticLockWithRenewal(lockKey, 5, TimeUnit.SECONDS)) {
        throw new BusinessException("Refresh token is being processed, please retry later");
    }
    
    try {
        // 2. Atomic validation and deletion (using Lua script)
        String luaScript = 
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "  local data = redis.call('hgetall', KEYS[1]); " +
            "  redis.call('del', KEYS[1]); " +
            "  return data; " +
            "else " +
            "  return nil; " +
            "end";
        
        // 3. Execute atomic operation
        Map<String, String> tokenData = executeLuaScript(luaScript, refreshTokenKey);
        
        if (tokenData == null) {
            throw new BusinessException("invalid_grant", "Refresh token is invalid or has been used");
        }
        
        // 4. Generate new token
        return generateNewTokens(tokenData);
    } finally {
        // 5. Release lock
        GlobalCache.releaseLock(lockKey);
    }
}
```

#### 7.3.4 Error Information Leakage Prevention

**Problem Description:**
- Error responses may leak sensitive information (e.g., whether client_id exists)
- Attackers may enumerate valid client_ids through error messages

**Solution:**
- **Unified Error Response Format**: Conforms to OAuth2.0 standard (RFC 6749)
- **Don't Distinguish Error Types**: client_id not exists and client_secret incorrect both return `invalid_client`
- **Standard Error Codes**:
  - `invalid_request`: Missing or malformed request parameters
  - `invalid_client`: Client authentication failed (not distinguishing client_id or secret)
  - `invalid_grant`: Authorization code, refresh_token, etc. is invalid
  - `unauthorized_client`: Client is not authorized to use this authorization type
  - `unsupported_grant_type`: Unsupported grant_type
  - `invalid_scope`: Requested scope is invalid
  - `invalid_token`: Access token is invalid or expired
  - `rate_limit_exceeded`: Too many requests

**Standard Error Response Format:**
```json
{
  "error": "invalid_client",
  "error_description": "Client authentication failed",
  "error_uri": "https://docs.rydeen.com/oauth2/errors#invalid_client"
}
```

#### 7.3.5 Token Revocation Mechanism

**Revocation Endpoint:**
```java
@PostMapping("/api/oauth2/revoke")
public ResultVO<Void> revokeToken(@RequestParam String token) {
    // 1. Verify token type (access_token or refresh_token)
    // 2. Add token to blacklist
    // 3. If refresh_token, delete from Redis
    // 4. Record revocation log
}
```

**Revocation Scenarios:**
- Third-party system proactively revokes leaked token
- Admin forcibly revokes tokens for a client
- After client secret reset, automatically revoke all related tokens

#### 7.3.6 Key Rotation Mechanism

**Configuration Example:**
```yaml
platform:
  gateway:
    interface-auth:
      token-secret: "current-secret"
      token-secret-rotation:
        enabled: true
        rotation-interval-days: 90  # Rotate every 90 days
        previous-secrets:           # Keep old keys for validation (multi-version support)
          - "previous-secret-1"
          - "previous-secret-2"
```

**Rotation Strategy:**
- Regularly rotate signing keys (recommended every 90 days)
- Keep old keys for validating already-issued tokens (multi-version key support)
- New tokens are signed with new key
- Old tokens can still be validated with old key before expiration

#### 7.3.7 Security Best Practices

- **HTTPS Transport**: All token transmission must use HTTPS to prevent man-in-the-middle attacks
- **Key Management**: Regularly rotate signing keys, use strong random keys
- **Token Storage**:
  - access_token: Client memory storage, clear immediately after use
  - refresh_token: Client secure storage (encrypted storage or secure key vault)
- **Principle of Least Privilege**: Assign minimum necessary `scopes` permissions to each client
- **Token Rotation**: After each use of refresh_token, the old refresh_token is immediately invalidated
- **Monitoring and Alerting**: Monitor abnormal token usage (e.g., frequent refresh, abnormal IPs) and alert timely
- **Regular Refresh**: Recommend third-party systems proactively refresh before access_token expires to avoid business interruption
- **Error Information Protection**: Unified error response format, do not leak sensitive information
- **Concurrent Protection**: Use distributed locks and atomic operations to prevent concurrent refresh issues

---

## 8. Performance Optimization and Rate Limiting

### 8.1 Rate Limiting Mechanism

**Problem Description:**
- Token endpoint lacks rate limiting protection, which may lead to brute force attacks or resource exhaustion

**Rate Limiting Strategy:**
- **Token Endpoint (by client_id)**: 10 times/minute
- **Token Endpoint (by IP)**: 30 times/minute (unauthenticated requests)
- **Refresh Token Endpoint**: 5 times/minute (prevent frequent refresh)
- **Business Endpoints**: QPS limit based on client configuration

**Implementation Example:**
```java
@Component
public class InterfaceAuthFilter extends AbstractBaseFilter {
    
    @Override
    public Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // Token endpoint rate limiting
        if (path.equals("/api/oauth2/token")) {
            String clientId = extractClientId(exchange);
            String ip = getClientIp(exchange);
            
            // Rate limit by client_id: max 10 times per minute
            String limitKey = "token-limit:client:" + clientId;
            if (!GlobalCache.tryAcquire(limitKey, 10, 60)) {
                return NetworkUtils.returnError(
                    exchange.getResponse(), 
                    HttpStatus.TOO_MANY_REQUESTS,
                    OAuth2ErrorResponse.rateLimitExceeded()
                );
            }
            
            // Rate limit by IP: max 30 times per minute (prevent unauthenticated brute force)
            String ipLimitKey = "token-limit:ip:" + ip;
            if (!GlobalCache.tryAcquire(ipLimitKey, 30, 60)) {
                return NetworkUtils.returnError(
                    exchange.getResponse(), 
                    HttpStatus.TOO_MANY_REQUESTS,
                    OAuth2ErrorResponse.rateLimitExceeded()
                );
            }
        }
        
        return chain.filter(exchange);
    }
}
```

### 8.2 Cache Pre-warming and Degradation

**Cache Pre-warming:**
```java
@PostConstruct
public void warmupCache() {
    // Load all enabled client configurations from database to Redis
    // Ensure cache is ready after service startup
}
```

**Cache Degradation Strategy:**
- **Priority Read from Redis**: Read client configuration from Redis under normal conditions
- **Degrade to Database**: Temporarily read from database when Redis is unavailable (emergency only)
- **Record Alerts**: Record alert logs during degradation for operations staff to handle promptly

**Implementation Example:**
```java
public ThirdPartyClientConfig getClientConfig(String clientId) {
    // Priority read from Redis
    ThirdPartyClientConfig config = getFromRedis(clientId);
    if (config != null) {
        return config;
    }
    
    // Degrade to database when Redis is unavailable (record alert)
    if (isRedisDown()) {
        log.warn("Redis unavailable, degrading to database read: clientId={}", clientId);
        return getFromDatabase(clientId);
    }
    
    return null;
}
```

### 8.3 Refresh Token Batch Cleanup

**Problem Description:**
- Expired refresh_tokens rely on Redis TTL automatic cleanup
- If a large number of refresh_tokens expire, they may occupy Redis memory

**Solution:**
```java
// Scheduled task: Clean up expired refresh_tokens
@Scheduled(cron = "0 0 2 * * ?")  // Execute at 2 AM daily
public void cleanupExpiredRefreshTokens() {
    // Scan all refresh-token:* keys
    // Check expiration time, delete expired tokens
    // Record cleanup count
}
```

---

## 9. Deployment Solution

### 8.1 Deployment Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Management Side (general-service)                      │
│  - Management API (REST API)                             │
│  - Management Page (HTML/Frontend)                       │
│  - Scheduled Sync Task                                   │
│  - Database Connection (MySQL)                            │
│  - Redis Connection (Write)                              │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Third-Party Gateway (Independent Deployment)            │
│  - InterfaceAuthFilter (OAuth2.0 Authentication)          │
│  - Route Forwarding                                      │
│  - Redis Connection (Read-only)                          │
│  - No Database Connection                                 │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Backend Service Cluster                                  │
│  - Business Service A                                    │
│  - Business Service B                                    │
│  - Business Service C                                    │
└─────────────────────────────────────────────────────────┘
```

### 8.2 Configuration Isolation

- **Third-Party Gateway**: Independent configuration file, does not contain internal system related configuration
- **Filter Enable**: Third-party gateway only enables necessary filters
- **Performance Isolation**: Third-party system access does not affect internal system performance

---

## 10. Monitoring and Operations

### 10.1 Key Metrics Monitoring

**Core Metrics:**
- **Token Issuance Volume**: Number of access_tokens issued per minute
- **Token Refresh Volume**: Number of refresh_tokens refreshed per minute
- **Authentication Failure Rate**: Proportion of failed authentication requests (classified by error type)
- **Cache Hit Rate**: Redis cache hit rate
- **Sync Task Execution**: Scheduled sync task execution status
- **Rate Limit Trigger Count**: Number of times rate limiting mechanism is triggered
- **Abnormal IP Access Statistics**: Access frequency and failure count of abnormal IPs
- **Client Activity Statistics**: Token usage frequency of each client

**Metrics Collection Implementation:**
```java
@Component
public class OAuth2Metrics {
    
    // Token issuance counter
    private final Counter tokenIssuedCounter;
    
    // Token refresh counter
    private final Counter tokenRefreshedCounter;
    
    // Authentication failure counter (by error type)
    private final Counter authFailureCounter;
    
    // Rate limit trigger counter
    private final Counter rateLimitCounter;
    
    public void recordTokenIssued(String clientId) {
        tokenIssuedCounter.increment("client_id", clientId);
    }
    
    public void recordAuthFailure(String clientId, String errorType) {
        authFailureCounter.increment("client_id", clientId, "error", errorType);
    }
}
```

### 10.2 Alert Rules

**Alert Threshold Recommendations:**
- **Authentication failure rate > 10%**: Possible attack or configuration error
- **Same IP authentication failures > 50 times/hour**: Possible brute force attack
- **Refresh Token refresh failure rate > 5%**: Possible token leakage
- **Token issuance volume abnormal growth > 200%**: Possible abnormal access
- **Redis cache hit rate < 90%**: Cache may have issues
- **Sync task failure**: Data sync exception, requires immediate handling

### 10.3 Health Check Endpoint

**Health Check Endpoint:**
```java
@GetMapping("/api/oauth2/health")
public ResultVO<Map<String, Object>> health() {
    Map<String, Object> health = new HashMap<>();
    health.put("status", "UP");
    health.put("redis", checkRedisHealth());
    health.put("cache", checkCacheHealth());
    health.put("timestamp", System.currentTimeMillis());
    return ResultVO.success(health);
}
```

**Health Check Items:**
- Redis connection status
- Cache availability
- Sync task status
- Overall service status

### 10.4 Logging

**Log Classification:**
- **Management Operation Logs**: Record all CRUD operations for client configuration (including operator, operation time, operation content)
- **Authentication Logs**: Record token issuance and validation process (including client_id, IP, time, result)
- **Exception Logs**: Record authentication failures, sync failures and other exceptions (including error type, error details)
- **Audit Logs**: Record all security-related operations (including token revocation, secret reset, etc.)

**Audit Log Implementation:**
```java
@Aspect
public class OAuth2AuditAspect {
    
    @Around("@annotation(OAuth2Operation)")
    public Object audit(ProceedingJoinPoint joinPoint) {
        String operation = getOperation(joinPoint);
        String clientId = extractClientId(joinPoint);
        String ip = getClientIp(joinPoint);
        
        try {
            Object result = joinPoint.proceed();
            // Record success log
            auditLogService.logSuccess(operation, clientId, ip);
            return result;
        } catch (Exception e) {
            // Record failure log (including error type)
            auditLogService.logFailure(operation, clientId, ip, e);
            throw e;
        }
    }
}
```

---

## 11. Configuration Management Optimization

### 11.1 Client Configuration Versioning

**Problem Description:**
- After client configuration changes, rollback is not possible
- Missing configuration change history

**Solution:**
```sql
-- Add configuration version table
CREATE TABLE third_party_client_history (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    config_snapshot TEXT,  -- Configuration snapshot in JSON format
    change_type VARCHAR(32),  -- CREATE/UPDATE/DELETE
    change_reason VARCHAR(512),
    operator_id VARCHAR(64),
    change_time DATETIME,
    INDEX idx_client_id (client_id),
    INDEX idx_change_time (change_time)
);
```

**Feature Capabilities:**
- Record complete snapshot of each configuration change
- Support configuration rollback
- Provide configuration change history query
- Record change reason and operator

### 11.2 Configuration Templates and Batch Operations (Optional)

**Configuration Template:**
```java
@Data
public class ClientConfigTemplate {
    private String templateName;
    private String scopes;
    private Integer tokenValidDuration;
    private Integer refreshTokenValidDuration;
    // ...
}
```

**Batch Create:**
```java
@PostMapping("/api/gateway/third-party-client/batch")
public ResultVO<List<Long>> batchCreate(@RequestBody List<ThirdPartyClientDTO> clients) {
    // Batch create clients
}
```

---

## 12. Implementation Plan

### 12.1 Development Phase

1. **Phase 1: Database and Model**
   - Create Liquibase changelog (company table, client table)
   - Create entity classes (ThirdPartyCompany, ThirdPartyClient)
   - Create DTO, VO classes
   - Create Mapper interfaces

2. **Phase 2: Management Functionality**
   - **Company Information Management**
     - Implement ThirdPartyCompanyService business logic
     - Implement ThirdPartyCompanyController REST API
     - Implement company information CRUD functionality
   - **Client Management**
     - Implement ThirdPartyClientService business logic (add company association validation)
     - Implement ThirdPartyClientController REST API
     - Implement client information CRUD functionality
   - **Management Page (Frontend)**
     - Implement company information management page
     - Implement client management page (support selecting associated company)

3. **Phase 3: Gateway Functionality**
   - Refactor InterfaceAuthFilter
   - Implement OAuth2.0 Token endpoint
   - Implement access_token validation and renewal

4. **Phase 4: Sync Mechanism**
   - Implement real-time sync logic
   - Implement scheduled sync task
   - Test data consistency

### 12.2 Testing Phase

- **Unit Tests**: Service, Filter and other core logic
- **Integration Tests**: End-to-end flow testing
- **Performance Tests**: Gateway performance, cache performance
- **Security Tests**: Authentication security, data security

### 12.3 Launch Phase

- **Gradual Release**: Launch management function first, then launch gateway function
- **Monitoring and Alerting**: Configure key metrics monitoring and alerting
- **Documentation Delivery**: API documentation, user manual

---

### 12.4 Improvement Priority

**Red - High Priority (Must Implement):**
1. Refresh Token concurrent refresh issue (use distributed lock + atomic operation)
2. Error information leakage risk (unified error response format)
3. Rate limiting mechanism (prevent brute force and resource exhaustion)
4. OAuth2.0 standard error response (conform to RFC 6749)
5. Key metrics monitoring and alerting

**Yellow - Medium Priority (Recommended to Implement):**
1. Refresh Token revocation mechanism
2. Key rotation mechanism
3. Cache degradation strategy
4. Error logging and audit
5. Configuration version management
6. Health check endpoint

**Green - Low Priority (Optional to Implement):**
1. Refresh Token batch cleanup
2. Configuration templates and batch operations
3. Token usage statistics
4. Multi-environment support

---

## 13. Appendix

### 13.1 Reference Documentation

- [OAuth2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [JWT RFC 7519](https://tools.ietf.org/html/rfc7519)
- [Liquibase Official Documentation](https://docs.liquibase.com/)

### 13.2 Glossary

| Term | Description |
|------|-------------|
| **company_id** | Company ID, associated with third-party company information table |
| **company_code** | Unified social credit code, uniquely identifies third-party company |
| **client_id** | Client identifier, uniquely identifies third-party system |
| **client_secret** | Client secret, used to verify client identity |
| **access_token** | Access token, used to access protected resources (JWT format, short validity like 1 hour) |
| **refresh_token** | Refresh token, used to obtain new access_token (random string format, long validity like 30 days) |
| **scopes** | Permission scopes, defines resources the client can access |
| **JWT** | JSON Web Token, a compact, URL-safe token format |
| **grant_type** | Authorization type, specifies how to obtain token in OAuth2.0 (e.g., client_credentials, refresh_token) |

---

**Document Version:** v1.0  
**Creation Date:** 2025-01-XX  
**Author:** richie696