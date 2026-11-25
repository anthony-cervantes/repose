# Historical package verification fixtures

The native DEB/RPM release-verification Gradle project has been retired.
The remaining configurations, mock services and supporting scripts are retained
as historical test fixtures for possible migration to container-based tests;
they are not an active deployment or CI path. The existing performance-test
build still uses `src/scripts/node_install.sh`.

Use the active Docker validation from the repository root instead:

```bash
bash scripts/build-container.sh local/repose:dev
```

This validates the actual GHCR image, including the runtime filesystem,
Java and Repose versions, Echo filter, legacy monitor Host handling, and the
Empty Request Filter. No release image is published by this command.
