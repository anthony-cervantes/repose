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

import java.io.{IOException, InputStream}
import java.nio.charset.StandardCharsets.UTF_8

import jakarta.servlet._
import jakarta.servlet.http.HttpServletRequest
import org.junit.runner.RunWith
import org.openrepose.commons.utils.io.stream.ServletInputStreamWrapper
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.springframework.mock.web.{MockHttpServletRequest, MockHttpServletResponse}

import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnitRunner])
class EmptyRequestFilterTest extends AnyFunSpec with Matchers {
  private def request(method: String, body: Array[Byte], length: Option[Int]): MockHttpServletRequest = {
    val result = new MockHttpServletRequest() {
      override def getContentLength: Int = length.getOrElse(-1)
      override def getContentLengthLong: Long = getContentLength.toLong
    }
    result.setMethod(method)
    result.setContent(body)
    result.setContentType("text/plain;charset=UTF-16")
    result.addHeader("X-Preserve", "one")
    result.addHeader("X-Preserve", "two")
    length.foreach(value => result.addHeader("Content-Length", value.toString))
    result
  }

  private def forward(request: HttpServletRequest): HttpServletRequest = {
    var result: HttpServletRequest = null
    var calls = 0
    val response = new MockHttpServletResponse()
    val chain = new FilterChain {
      override def doFilter(req: ServletRequest, res: ServletResponse): Unit = {
        calls += 1
        res should be theSameInstanceAs response
        result = req.asInstanceOf[HttpServletRequest]
      }
    }
    val filter = new EmptyRequestFilter()
    filter.init(null)
    filter.doFilter(request, response, chain)
    filter.destroy()
    calls shouldBe 1
    result
  }

  describe("Empty request filter") {
    for (method <- Seq("POST", "PUT"); length <- Seq(None, Some(0))) {
      it(s"should supply JSON for empty $method with Content-Length $length") {
        val original = request(method, Array.emptyByteArray, length)
        original.addHeader("Transfer-Encoding", "chunked")
        original.addHeader("Content-Encoding", "gzip")
        val result = forward(original)

        result.getInputStream.readAllBytes().toSeq shouldBe "{}".getBytes(UTF_8).toSeq
        result.getMethod shouldBe method
        result.getContentType shouldBe "application/json"
        result.getContentLength shouldBe 2
        result.getContentLengthLong shouldBe 2L
        result.getCharacterEncoding shouldBe "UTF-8"
        result.getHeader("content-type") shouldBe "application/json"
        result.getHeaders("CONTENT-TYPE").asScala.toList shouldBe List("application/json")
        result.getHeaders("content-length").asScala.toList shouldBe List("2")
        result.getIntHeader("Content-Length") shouldBe 2
        result.getHeader("Transfer-Encoding") shouldBe null
        result.getHeader("Content-Encoding") shouldBe null
        result.getHeaderNames.asScala.map(_.toLowerCase).toSet shouldBe
          Set("content-type", "content-length", "x-preserve")
        result.getHeaders("X-Preserve").asScala.toList shouldBe List("one", "two")
        original.getHeader("Content-Length") shouldBe length.map(_.toString).orNull
        original.getContentType shouldBe "text/plain;charset=UTF-16"
      }
    }

    it("should expose the replacement through the reader as well") {
      forward(request("POST", Array.emptyByteArray, None)).getReader.readLine() shouldBe "{}"
    }

    it("should add JSON headers when the empty request has no Content-Type") {
      val original = request("PUT", Array.emptyByteArray, None)
      original.setContentType(null)
      val result = forward(original)
      result.getContentType shouldBe "application/json"
      result.getHeader("Content-Type") shouldBe "application/json"
      result.getInputStream.readAllBytes().toSeq shouldBe "{}".getBytes(UTF_8).toSeq
    }

    val bodies = Seq(
      "JSON" -> """{ "hello": "world" }""".getBytes(UTF_8),
      "whitespace" -> " \t\r\n".getBytes(UTF_8),
      "single NUL byte" -> Array[Byte](0),
      "binary" -> Array.tabulate[Byte](8193)(i => (i % 256).toByte))

    for (method <- Seq("POST", "PUT"); (name, body) <- bodies; length <- Seq(None, Some(body.length))) {
      it(s"should preserve every byte and header of $name $method with Content-Length $length") {
        val original = request(method, body, length)
        if (length.isEmpty) original.addHeader("Transfer-Encoding", "chunked")
        val result = forward(original)

        result.getInputStream.readAllBytes().toSeq shouldBe body.toSeq
        result.getContentType shouldBe original.getContentType
        result.getContentLength shouldBe original.getContentLength
        result.getContentLengthLong shouldBe original.getContentLengthLong
        result.getHeaderNames.asScala.toSet shouldBe original.getHeaderNames.asScala.toSet
        original.getHeaderNames.asScala.foreach { name =>
          result.getHeaders(name).asScala.toList shouldBe original.getHeaders(name).asScala.toList
        }
      }
    }

    for (method <- Seq("GET", "HEAD", "DELETE", "PATCH", "OPTIONS", "TRACE", "CUSTOM");
         body <- Seq(Array.emptyByteArray, "unchanged".getBytes(UTF_8))) {
      it(s"should pass $method with ${body.length} bytes through without accessing the body") {
        val original = new MockHttpServletRequest() {
          override def getInputStream: ServletInputStream = fail("Body must not be accessed")
        }
        original.setMethod(method)
        original.setContent(body)
        forward(original) should be theSameInstanceAs original
      }
    }

    it("should propagate an input failure without forwarding a synthetic body") {
      val original = new MockHttpServletRequest() {
        override def getInputStream: ServletInputStream = new ServletInputStreamWrapper(new InputStream {
          override def read(): Int = throw new IOException("broken body")
        })
      }
      original.setMethod("POST")
      val chain = new FilterChain {
        override def doFilter(req: ServletRequest, res: ServletResponse): Unit = fail("Must not forward")
      }
      intercept[IOException] {
        new EmptyRequestFilter().doFilter(original, new MockHttpServletResponse(), chain)
      }.getMessage shouldBe "broken body"
    }
  }
}
