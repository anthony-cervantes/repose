# SLF4J HTTP Logging Filter

Logs configured request and response fields through SLF4J loggers around the
downstream filter chain. Place it early to observe the whole chain. Configuration
is read from `slf4j-http-logging.cfg.xml`.

The separate HTTP Logging Service also remains available; its JTwig configuration
is described in [HTTP Logging Service](../../../docs/src/asciibinder/services/http-logging.adoc).
See the [filter page](../../../docs/src/asciibinder/filters/slf4j-http-logging.adoc)
for this filter's configuration example.
