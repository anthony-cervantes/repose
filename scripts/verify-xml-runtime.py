#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the XSD 1.1 provider in the staged JAR and each XML-enabled EAR."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile

FACTORIES = (
    "org/apache/xerces/jaxp/validation/XMLSchema11Factory.class",
    "org/apache/xerces/jaxp/DocumentBuilderFactoryImpl.class",
)
FORBIDDEN = ("xerces-xsd11-", "xml-apis-2.0.2-rax", "org.eclipse.wst.xml.xpath2.processor-")
JAVA = r'''import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class XmlRuntimeProbe {
    private static StreamSource xml(String text) {
        return new StreamSource(new StringReader(text));
    }

    private static void reject(Schema schema, String document) throws Exception {
        try {
            schema.newValidator().validate(xml(document));
        } catch (SAXException expected) {
            return;
        }
        throw new AssertionError("Invalid XML was accepted: " + document);
    }

    public static void main(String[] args) throws Exception {
        var loader = XmlRuntimeProbe.class.getClassLoader();
        var factory = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1",
            "org.apache.xerces.jaxp.validation.XMLSchema11Factory", loader);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var schema = factory.newSchema(xml("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" version="1.1">
              <xs:element name="range">
                <xs:complexType>
                  <xs:attribute name="start" type="xs:int" use="required"/>
                  <xs:attribute name="end" type="xs:int" use="required"/>
                  <xs:assert test="@start lt @end"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """));
        schema.newValidator().validate(xml("<range start='1' end='3'/>"));
        reject(schema, "<range start='3' end='1'/>");
        reject(schema, "<range start='1' end='1'/>");

        var classic = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI,
            "org.apache.xerces.jaxp.validation.XMLSchemaFactory", loader);
        var classicSchema = classic.newSchema(xml("""
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="count" type="xs:int"/>
            </xs:schema>
            """));
        classicSchema.newValidator().validate(xml("<count>3</count>"));
        reject(classicSchema, "<count>bad</count>");

        var parser = DocumentBuilderFactory.newInstance(
            "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl", loader);
        parser.setNamespaceAware(true);
        parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parser.newDocumentBuilder().parse(new InputSource(new StringReader("<ok/>")));
        try {
            parser.newDocumentBuilder().parse(new InputSource(new StringReader(
                "<!DOCTYPE x [<!ENTITY test 'blocked'>]><x>&test;</x>")));
            throw new AssertionError("Prohibited DOCTYPE was accepted");
        } catch (SAXException expected) {
            // The configured security feature must still reject this document.
        }
        System.out.println("XSD 1.0, XSD 1.1 assertions and secure parser features passed");

        // Match the API checker's Xalan XSLTC identity-transformer initialization.
        var transformer = TransformerFactory.newInstance(
            "org.apache.xalan.xsltc.trax.TransformerFactoryImpl", loader).newTransformer();
        var result = new StringWriter();
        transformer.transform(xml("<probe>ok</probe>"), new StreamResult(result));
        if (!result.toString().contains("<probe>ok</probe>")) {
            throw new AssertionError("Xalan identity transformation failed: " + result);
        }
        System.out.println("Xalan identity transformation passed");
    }
}
'''


def verify_classpath(label, paths, source):
    providers = {marker: [] for marker in FACTORIES}
    for path in paths:
        with ZipFile(path) as archive:
            entries = set(archive.namelist())
            for marker in FACTORIES:
                if marker in entries:
                    providers[marker].append(path.name)
    for marker, matches in providers.items():
        if len(matches) != 1:
            raise ValueError(f"{label}: expected one provider for {marker}, found {matches}")
    print(f"{label}: {providers}", flush=True)
    subprocess.run(["java", "--class-path", os.pathsep.join(map(str, paths)), str(source)],
                   check=True, timeout=90)


def verify(root):
    with tempfile.TemporaryDirectory(prefix="repose-xml-") as directory:
        work = Path(directory)
        source = work / "XmlRuntimeProbe.java"
        source.write_text(JAVA, encoding="utf-8")
        verify_classpath("Valve", [root / "repose.jar"], source)
        for bundle in ("filter-bundle", "extensions-filter-bundle"):
            candidates = sorted((root / "filters").glob(f"{bundle}-*.ear"))
            if len(candidates) != 1:
                raise ValueError(f"Expected exactly one {bundle} EAR, got {candidates}")
            paths = []
            extracted = work / bundle
            extracted.mkdir()
            with ZipFile(candidates[0]) as archive:
                members = sorted(name for name in archive.namelist() if name.endswith(".jar"))
                for index, member in enumerate(members):
                    name = Path(member).name
                    if any(name.startswith(prefix) for prefix in FORBIDDEN):
                        raise ValueError(f"Retired XML library remains in {bundle}: {member}")
                    target = extracted / f"{index:04d}-{name}"
                    with archive.open(member) as origin, target.open("wb") as output:
                        shutil.copyfileobj(origin, output)
                    paths.append(target)
                    if name.startswith(("xercesImpl-", "xpath2-", "java-cup-")):
                        with target.open("rb") as stream:
                            digest = hashlib.file_digest(stream, "sha256").hexdigest()
                        print(f"{bundle}: {name} SHA-256 {digest}", flush=True)
            if not any("xercesImpl-" in path.name and "-xml-schema-1.1.jar" in path.name for path in paths):
                raise ValueError(f"{bundle}: missing classified Xerces XSD 1.1 JAR")
            verify_classpath(bundle, paths, source)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: verify-xml-runtime.py STAGED_REPOSE_DIRECTORY")
    verify(Path(sys.argv[1]).resolve())
