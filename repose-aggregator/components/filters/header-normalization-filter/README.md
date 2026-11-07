# Header Normalization Filter

The filter removes request and response headers using configured allowlists or
blocklists. Targets match the request URI and HTTP method. Every matching target
is applied; names are compared without case sensitivity.

`doWork` wraps the request, applies request rules, invokes the downstream chain,
then applies response rules and commits the response wrapper. It does not use a
`FilterDirector` or `handleRequest` callback.

See the [configuration and examples](../../../docs/src/asciibinder/filters/header-normalization.adoc).
