# Echo Filter

This terminal diagnostic filter copies incoming request headers to the response
and sets status 200. It does not echo the request body, contact the origin, or
invoke the rest of the filter chain. It is registered as `echo` in the experimental
filter bundle and has no configuration file.
