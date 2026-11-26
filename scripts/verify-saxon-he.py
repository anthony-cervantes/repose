#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Verify the staged HE-only candidate; never infer edition from a dependency name."""
from io import BytesIO
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile

PROVIDER = "net/sf/saxon/Version.class"
JAVA = r'''import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

class SaxonHeProbe {
    static StreamSource xml(String value) {
        return new StreamSource(new StringReader(value));
    }

    static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        // Exercise the translation filter's shared JAXP factory API.
        var loader = SaxonHeProbe.class.getClassLoader();
        var factory = (TransformerFactoryImpl) TransformerFactory.newInstance(
            "net.sf.saxon.TransformerFactoryImpl", loader);
        factory.getConfiguration().getDynamicLoader().setClassLoader(loader);
        var identityOutput = new StringWriter();
        factory.newTransformer().transform(xml("<probe>ok</probe>"), new StreamResult(identityOutput));
        if (!identityOutput.toString().contains("<probe>ok</probe>")) {
            throw new AssertionError("Saxon JAXP identity transformation failed");
        }
        // An explicit EE request must not silently become an HE factory.
        try {
            TransformerFactory.newInstance("com.saxonica.config.EnterpriseTransformerFactory", loader);
            throw new AssertionError("Proprietary factory unexpectedly available in HE candidate");
        } catch (TransformerFactoryConfigurationError expected) {
            System.out.println("Explicit EE factory request rejected in HE candidate");
        }

        // Match the API checker's Processor construction.
        var p = new Processor(true);
        expect("HE", p.getSaxonEdition());
        if (p.isSchemaAware() || p.getSchemaManager() != null) {
            throw new AssertionError("Expected an unlicensed, non-schema-aware processor");
        }

        // Match the URI stripper's integrated extension mechanism, not reflection.
        p.registerExtensionFunction(new ExtensionFunctionDefinition() {
            public StructuredQName getFunctionQName() {
                return new StructuredQName("probe", "urn:repose:probe", "value");
            }
            public SequenceType[] getArgumentTypes() { return new SequenceType[0]; }
            public SequenceType getResultType(SequenceType[] arguments) {
                return SequenceType.SINGLE_STRING;
            }
            public ExtensionFunctionCall makeCallExpression() {
                return new ExtensionFunctionCall() {
                    public Sequence call(XPathContext context, Sequence[] arguments) {
                        return new StringValue("extension-ok");
                    }
                };
            }
        });
        var xpath = p.newXPathCompiler();
        xpath.declareNamespace("probe", "urn:repose:probe");
        expect("extension-ok", xpath.evaluateSingle("probe:value()", null).getStringValue());
        expect("6", xpath.evaluateSingle("sum((1, 2, 3))", null).getStringValue());

        var query = p.newXQueryCompiler().compile(
            "xquery version '3.1'; let $values := (1, 2, 3) return sum($values)").load();
        expect("6", query.evaluateSingle().getStringValue());

        var stylesheet = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:probe="urn:repose:probe" version="2.0">
              <xsl:output method="text"/>
              <xsl:template match="/">
                <xsl:value-of select="concat(sum(/root/n), ':', probe:value())"/>
              </xsl:template>
            </xsl:stylesheet>
            """;
        var transform = p.newXsltCompiler().compile(xml(stylesheet)).load();
        var output = new StringWriter();
        transform.setSource(xml("<root><n>2</n><n>4</n></root>"));
        transform.setDestination(p.newSerializer(output));
        transform.transform();
        expect("6:extension-ok", output.toString());
        System.out.println("Saxon " + p.getSaxonProductVersion()
            + " HE: XPath, XQuery, XSLT and integrated extensions passed");
    }
}
'''


def inspect_archive(archive, label):
    """Inspect classes and nested archives, including shaded commercial classes."""
    names = archive.namelist()
    for name in names:
        lowered = name.lower()
        if lowered.startswith("com/saxonica/") and lowered.endswith(".class"):
            raise ValueError(f"{label}: commercial Saxon class {name}")
        if Path(lowered).name.startswith(("saxon-ee", "saxon-pe")) and lowered.endswith(".jar"):
            raise ValueError(f"{label}: proprietary Saxon artifact {name}")
        if lowered.endswith((".jar", ".ear", ".war")):
            with ZipFile(BytesIO(archive.read(name))) as nested:
                inspect_archive(nested, f"{label}!/{name}")
    return PROVIDER in names


def verify_classpath(label, paths, source, require_saxon=True):
    providers = []
    for path in paths:
        with ZipFile(path) as archive:
            if inspect_archive(archive, str(path)):
                providers.append(path.name)
    if not providers and not require_saxon:
        return
    if len(providers) != 1:
        raise ValueError(f"{label}: expected one Saxon provider, found {providers}")
    print(f"{label}: Saxon provider {providers[0]}", flush=True)
    subprocess.run(
        ["java", "--class-path", os.pathsep.join(map(str, paths)), str(source)],
        check=True, timeout=90,
    )


def verify(root):
    with tempfile.TemporaryDirectory(prefix="repose-saxon-he-") as directory:
        work = Path(directory)
        source = work / "SaxonHeProbe.java"
        source.write_text(JAVA, encoding="utf-8")
        verify_classpath("Valve", [root / "repose.jar"], source, require_saxon=False)
        for bundle in ("filter-bundle", "extensions-filter-bundle", "experimental-filter-bundle"):
            ears = sorted((root / "filters").glob(f"{bundle}-*.ear"))
            if len(ears) != 1:
                raise ValueError(f"Expected one {bundle} EAR, got {ears}")
            extracted = work / bundle
            extracted.mkdir()
            paths = []
            with ZipFile(ears[0]) as archive:
                inspect_archive(archive, str(ears[0]))
                for index, name in enumerate(sorted(archive.namelist())):
                    if not name.endswith(".jar"):
                        continue
                    target = extracted / f"{index:04d}-{Path(name).name}"
                    with archive.open(name) as origin, target.open("wb") as output:
                        shutil.copyfileobj(origin, output)
                    paths.append(target)
            verify_classpath(bundle, paths, source,
                             require_saxon=bundle != "experimental-filter-bundle")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: verify-saxon-he.py STAGED_REPOSE_DIRECTORY")
    verify(Path(sys.argv[1]).resolve())
