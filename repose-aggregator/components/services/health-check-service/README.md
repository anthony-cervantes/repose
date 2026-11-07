# Health Check Service

`HealthCheckService.register()` returns a `HealthCheckServiceProxy` for a component.
Use that proxy to report and resolve named issues, inspect reports, and deregister
at shutdown. `isHealthy()` is false while any report has `Severity.BROKEN`.

See the [service documentation](../../../docs/src/asciibinder/services/health-check.adoc)
and the interfaces in `health-check-service-api/src/main/java/org/openrepose/core/services/healthcheck`.
