# Saxon edition migration — Phase 3

## Scope and decision

Run an isolated Saxon-HE compatibility experiment before changing the production
default. The current release uses `net.sf.saxon:Saxon-EE:9.8.0-8` and exposes
licensed engine options to customers. Saxon-HE is MPL-2.0 and cannot implement
EE-only schema-aware processing. Do not silently reinterpret an explicit
`SaxonEE` configuration as Xerces or HE.

The first experiment uses the same 9.8.0-8 release of HE from Maven Central to
separate edition differences from the substantial Saxon Java API changes in
newer releases. This is not a supported-version/security upgrade; it temporarily
aligns any transitive HE dependency to that version only in the candidate.

## Evidence from master at 8ebeec1

- `Config.scala` defaults XSD validation to Xerces, supports explicit
  `SaxonEE`, and exposes both HE and EE as XSL engines.
- `WADLCheckerBuilder.scala` obtains schema managers for its EE paths; the
  default path already uses Xerces for WADL and checker-schema validation.
- `BuilderHelper.scala` and `XPathStepUtil.scala` construct Saxon processors
  using the licensed-edition flag. The latter also uses internal Saxon query
  parser and function-library APIs; an API upgrade needs separate work.
- `api-checker-core/build.gradle` generates optimizer stylesheets with Saxon
  during the build.
- `uri-stripper-filter` registers integrated extension functions; translation
  also uses Saxon. Existing application and customer-provided stylesheets and
  XPaths may require features not available in 9.8 HE.
- The functional-test suite has explicit SaxonEE fixtures. Those are not
  equivalent to the Xerces-default smoke test and must not be deleted to obtain
  a green result.

## Experiment

```bash
ORG_GRADLE_PROJECT_saxonHeCandidate=true bash scripts/audit-dependencies.sh
```

On the candidate PR, the ordinary validation job keeps its existing EE build;
the separate candidate job performs the same clean-cache audit with the
alternate resolution strategy. Candidate mode excludes EE from the Rackspace
repository and substitutes EE requests with Central HE at the existing version.
It verifies staged artifacts for commercial classes and duplicate providers,
exercises the actual packaged HE libraries using XPath, XQuery, XSLT and an
integrated extension, and retains the existing Xerces, Docker, WADL routing,
monitor-host, empty-request and test-classpath probes. Neither job publishes
images. Candidate mode is rejected by the release build entry point.

## Candidate findings

The first candidate run (45754893387) resolved Central HE without downloading
EE, but failed compiling TranslationFilter because it imported the proprietary
EnterpriseTransformerFactory. Both factory implementations inherit
TransformerFactoryImpl in Saxon 9.8. The filter now uses that shared API to
configure its class loader, retaining the exact factory class name selected
by configuration. The HE probe exercises that API and verifies explicit EE
factory construction fails rather than falling back to HE. This does not yet
establish safe hot-reload behavior for all invalid configurations.

- https://www.saxonica.com/html/documentation9.8/javadoc/com/saxonica/config/EnterpriseTransformerFactory.html

## Promotion gates (not satisfied merely by a green candidate job)

1. Review candidate logs for compilation and runtime failures, duplicate
   providers and any EE downloads. Preserve validation failure semantics.
2. Exercise representative WADL request bodies, headers, roles/tenant RBAC,
   assertions, URI transformations and customer XSLT with valid and invalid
   inputs. Compare results against the current EE build. Inventory unsupported
   EE features and explicit licensed engine usage.
3. Decide how to support licensed users (separate licensed build supplied by
   the user, or documented fail-closed migration). Do not redistribute EE
   without confirming license terms.
4. Move to a maintained HE release in a separate compatibility step, verify
   dependency vulnerabilities, update MPL-2.0 notices and source availability.
5. Only after the compatibility and licensing decision is approved, change
   default dependency declarations, remove the EE repository exception, and
   repeat the complete validation before any release.

## References

- https://www.saxonica.com/html/products/feature-matrix-9-8.html
- https://www.saxonica.com/html/documentation9.8/javadoc/net/sf/saxon/s9api/Processor.html
- https://www.saxonica.com/html/documentation9.8/conditions/index.html
- https://repo.maven.apache.org/maven2/net/sf/saxon/Saxon-HE/9.8.0-8/
