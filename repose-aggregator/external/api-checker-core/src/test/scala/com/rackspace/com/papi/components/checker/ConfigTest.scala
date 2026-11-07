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
package com.rackspace.com.papi.components.checker

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConfigTest extends AnyFunSuite with Matchers {
  private val checkerOptions = Set(
    "removeDups", "disableSaxonByteCodeGen", "checkWellFormed", "checkXSDGrammar",
    "doXSDGrammarTransform", "checkJSONGrammar", "checkElements", "checkPlainParams",
    "enablePreProcessExtension", "enableIgnoreXSDExtension", "enableIgnoreJSONSchemaExtension",
    "enableMessageExtension", "enableRaxRolesExtension", "enableRaxIsTenantExtension",
    "enableRaxRepresentationExtension", "maskRaxRoles403", "enableCaptureHeaderExtension",
    "enableAnyMatchExtension", "enableAuthenticatedByExtension", "enableAssertExtension",
    "preserveMethodLabels", "joinXPathChecks", "checkHeaders", "setParamDefaults",
    "preserveRequestBody", "warnAgent", "enableWarnHeaders", "xpathVersion", "xslEngine")

  test("every checker generation option changes its cache key") {
    val defaults = new Config().checkerMetaMap
    defaults.keySet shouldBe checkerOptions
    defaults.foreach { case (name, value) =>
      val config = new Config()
      value match {
        case b: Boolean =>
          classOf[Config].getMethod(name + "_$eq", java.lang.Boolean.TYPE)
            .invoke(config, Boolean.box(!b))
        case _ if name == "warnAgent" => config.warnAgent = "custom-agent"
        case _ if name == "xpathVersion" => config.xpathVersion = 31
        case _ if name == "xslEngine" => config.xslEngine = "SaxonHE"
        case _ => fail(s"Uncovered checker option: $name")
      }
      withClue(s"cache key for $name: ") {
        config.checkerMetaMap(name) should not be value
        (config.checkerMetaMap - name) shouldBe (defaults - name)
      }
    }
  }

  test("runtime-only options do not invalidate a generated checker") {
    val config = new Config()
    val original = config.checkerMetaMap
    config.validateChecker = !config.validateChecker
    config.resultHandler = null
    config.xsdEngine = "SaxonEE"
    config.checkerMetaMap shouldBe original
  }
}
