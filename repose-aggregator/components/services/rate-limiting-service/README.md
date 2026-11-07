The `RateLimitingService` API exposes `trackLimits` and `queryLimits`.
`RateLimitingServiceImpl` selects configured limits for a user, groups, URI, parameters, and HTTP method.
`RateLimiter.handleRateLimit` updates the counters through `RateLimitCache.updateLimit` and throws `OverLimitException` when no requests remain.

See the [Rate Limiting guide](../../../docs/src/asciibinder/filters/rate-limiting.adoc) for configuration, distributed datastore behavior, and response codes.
