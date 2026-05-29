# Degraded Response Configuration Guide

## Overview

The gateway supports configuring different degraded response messages based on URL paths. Configuration is stored in the Nacos configuration center and can be modified at any time without restarting the service.

## Configuration Methods

### 1. Nacos Configuration Center

Find the `platform-gateway.yaml` configuration file in the Nacos configuration center and add the following configuration:

```yaml
platform:
  gateway:
    fallback:
      # Whether to enable degraded response configuration (default: true)
      enabled: true

      # Default degraded response message (used when no specific path matches)
      default-message: "Service temporarily unavailable, please try again later!"

      # List of degraded response messages configured by path
      path-messages:
        # Order service degraded response
        - path: "/api/order/**"
          message: "Order service temporarily unavailable, please try again later!"

        # User service degraded response
        - path: "/api/user/**"
          message: "User service temporarily unavailable, please try again later!"

        # Payment service degraded response
        - path: "/api/payment/**"
          message: "Payment service temporarily unavailable, please try again later!"

        # OAuth2.0 interface degraded response
        - path: "/api/oauth2/**"
          message: "Authentication service temporarily unavailable, please try again later!"

        # Generic degraded response for all API interfaces (placed last as fallback)
        - path: "/api/**"
          message: "API service temporarily unavailable, please try again later!"
```

### 2. Path Matching Rules

- **Ant path matching patterns are supported**:
  - `?`: Matches a single character
  - `*`: Matches zero or more characters (excluding path separator `/`)
  - `**`: Matches zero or more path segments (including path separator `/`)

- **Matching order**:
  - Matching proceeds from top to bottom in configuration order
  - The first path that matches successfully uses its corresponding message
  - If no path matches, the default message is used

### 3. Configuration Examples

#### Example 1: Configure by Service Module

```yaml
platform:
  gateway:
    fallback:
      enabled: true
      default-message: "Service temporarily unavailable, please try again later!"
      path-messages:
        - path: "/api/order/**"
          message: "Order service temporarily unavailable, please try again later!"
        - path: "/api/user/**"
          message: "User service temporarily unavailable, please try again later!"
        - path: "/api/product/**"
          message: "Product service temporarily unavailable, please try again later!"
```

#### Example 2: Configure by Interface Type

```yaml
platform:
  gateway:
    fallback:
      enabled: true
      default-message: "Service temporarily unavailable, please try again later!"
      path-messages:
        # Internal interfaces
        - path: "/api/internal/**"
          message: "Internal service temporarily unavailable, please try again later!"

        # Third-party interfaces
        - path: "/api/third-party/**"
          message: "Third-party service temporarily unavailable, please try again later!"

        # Public interfaces
        - path: "/api/public/**"
          message: "Public service temporarily unavailable, please try again later!"
```

#### Example 3: Exact Path Matching

```yaml
platform:
  gateway:
    fallback:
      enabled: true
      default-message: "Service temporarily unavailable, please try again later!"
      path-messages:
        # Exact match for login interface
        - path: "/api/auth/login"
          message: "Login service temporarily unavailable, please try again later!"

        # Exact match for payment interface
        - path: "/api/payment/pay"
          message: "Payment service temporarily unavailable, please try again later!"

        # Wildcard match for other interfaces
        - path: "/api/**"
          message: "API service temporarily unavailable, please try again later!"
```

## Dynamic Refresh

Configuration supports Nacos dynamic refresh. After modifying the configuration:

1. Modify the configuration in the Nacos configuration center
2. Click "Publish" to save the configuration
3. The gateway service automatically refreshes the configuration (no restart required)
4. New degraded response messages take effect immediately

## Notes

1. **Configuration order matters**: More specific paths should be placed before more general paths
   - Correct: `/api/order/**` comes before `/api/**`
   - Incorrect: `/api/**` comes before `/api/order/**` (would cause order interfaces to match the generic message)

2. **Path pattern support**:
   - Ant path matching is supported (`?`, `*`, `**`)
   - Regular expressions are not supported

3. **Default message**:
   - If `enabled=false`, the default message is always returned
   - If no path matches, the default message is returned

4. **Configuration validation**:
   - Neither path nor message can be empty
   - Empty configurations are ignored and the default message is used

## Use Cases

1. **Service degradation**: Return friendly degradation hints when backend services are unavailable
2. **Maintenance notification**: Return maintenance hints during service maintenance periods
3. **Personalized hints**: Different service modules can have different degradation hint copy
4. **Multi-language support**: Different degradation messages can be configured for different language environments (requires i18n component integration)

## Technical Implementation

- **Configuration class**: `FallbackConfig` (located in the `atlas-richie-base` module)
- **Controller**: `GlobalFallbackController` (located in the `atlas-richie-gateway-service` module)
- **Path matching**: Uses Spring's `AntPathMatcher` for path matching
- **Configuration refresh**: Supports Nacos dynamic refresh via the `@RefreshScope` annotation
