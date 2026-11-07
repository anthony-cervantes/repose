# Rate Limiting Filter

Uses the Rate Limiting Service to track request quotas for the `X-PP-User` and
optional `X-PP-Groups` headers. Run identity/header-populating filters before it.
Rules in `rate-limiting.cfg.xml` select requests by URI and HTTP method and define
the request limit and time unit. Limits can use the distributed datastore when
configured. Exceeded limits can be rejected with 429 or delegated.

See the [filter documentation](../../../docs/src/asciibinder/filters/rate-limiting.adoc)
and the [service README](../../services/rate-limiting-service/README.md).
