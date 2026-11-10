/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.filters.emptyrequest

import jakarta.xml.bind.JAXBContext
import javax.xml.XMLConstants
import javax.xml.validation.SchemaFactory

import org.junit.runner.RunWith
import org.openrepose.filters.emptyrequest.config.{EmptyRequestFilterConfig, ObjectFactory}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EmptyRequestFilterSchemaTest extends AnyFunSpec with Matchers {
  describe("Legacy RBA configuration") {
    it("should validate and unmarshal the existing filename and namespace") {
      val schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
        getClass.getResource("/META-INF/schema/config/empty-request-filter-configuration.xsd"))
      val unmarshaller = JAXBContext.newInstance(classOf[ObjectFactory]).createUnmarshaller()
      unmarshaller.setSchema(schema)
      val source = new javax.xml.transform.stream.StreamSource(
        getClass.getResource("/META-INF/schema/examples/empty-request-filter.cfg.xml").toExternalForm)
      val config = unmarshaller.unmarshal(source, classOf[EmptyRequestFilterConfig])
      config.getName.getNamespaceURI shouldBe "http://docs.example.com/rba/empty-request-filter/v1.0"
      config.getName.getLocalPart shouldBe "empty-request-filter"
    }
  }
}
