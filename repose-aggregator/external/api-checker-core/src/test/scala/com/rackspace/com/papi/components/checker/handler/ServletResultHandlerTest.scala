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
package com.rackspace.com.papi.components.checker.handler

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections

import com.rackspace.com.papi.components.checker.step.results.ErrorResult
import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.springframework.mock.web.MockHttpServletResponse

@RunWith(classOf[JUnitRunner])
class ServletResultHandlerTest extends AnyFunSuite with Matchers {
  test("diagnostics replace buffered content and preserve status and validator headers") {
    val error = mock(classOf[ErrorResult])
    when(error.code).thenReturn(405)
    when(error.message).thenReturn("Invalid parameter π")
    when(error.headers).thenReturn(Collections.singletonMap("Allow", "GET, HEAD"))
    val response = new MockHttpServletResponse()
    response.addHeader("X-Request-Id", "request-123")
    response.getOutputStream.write("stale content".getBytes(UTF_8))

    new ServletResultHandler().sendError(error, response)

    response.getStatus shouldBe 405
    response.getContentAsString shouldBe "Invalid parameter π"
    response.getContentAsByteArray.length shouldBe "Invalid parameter π".getBytes(UTF_8).length
    response.getContentLength shouldBe response.getContentAsByteArray.length
    response.getContentType shouldBe "text/plain;charset=UTF-8"
    response.getHeader("Cache-Control") shouldBe "no-store"
    response.getHeader("Allow") shouldBe "GET, HEAD"
    response.getHeader("X-Request-Id") shouldBe "request-123"
  }

  test("a committed response cannot be replaced with a validation diagnostic") {
    val response = new MockHttpServletResponse()
    response.getOutputStream.write("original".getBytes(UTF_8))
    response.flushBuffer()

    intercept[IllegalStateException] {
      new ServletResultHandler().sendError(mock(classOf[ErrorResult]), response)
    }
    response.getContentAsString shouldBe "original"
  }
}
