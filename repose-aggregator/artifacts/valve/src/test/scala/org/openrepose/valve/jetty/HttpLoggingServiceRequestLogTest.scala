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
package org.openrepose.valve.jetty

import java.time.Instant
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.http.{HttpFields, HttpHeader}
import org.eclipse.jetty.server.{Request, Response}
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.{mockStatic, never, verify, when}
import org.openrepose.commons.utils.http.CommonRequestAttributes
import org.openrepose.core.services.httplogging.{HttpLoggingContext, HttpLoggingService}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar

@RunWith(classOf[JUnitRunner])
class HttpLoggingServiceRequestLogTest extends AnyFunSpec with BeforeAndAfterEach with MockitoSugar with Matchers {
  var service: HttpLoggingService = _
  var requestLog: HttpLoggingServiceRequestLog = _
  var request: Request = _
  var response: Response = _
  var servletResponse: HttpServletResponse = _
  var headers: HttpFields.Mutable = _
  var context: HttpLoggingContext = _

  override def beforeEach(): Unit = {
    service = mock[HttpLoggingService]
    requestLog = new HttpLoggingServiceRequestLog(service)
    request = mock[Request]
    response = mock[Response]
    servletResponse = mock[HttpServletResponse]
    context = mock[HttpLoggingContext]
    headers = HttpFields.build()
    when(response.getHeaders).thenReturn(headers)
    when(response.getStatus).thenReturn(200)
    when(request.getAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT)).thenReturn(context, Nil: _*)
    when(request.getAttribute(HttpLoggingServiceRequestLog.ServletResponseAttribute)).thenReturn(servletResponse, Nil: _*)
    when(service.open()).thenReturn(context)
  }

  describe("log") {
    it("should add the servlet response to the context") {
      requestLog.log(request, response)
      verify(context).setOutboundResponse(servletResponse)
    }

    it("should add the response status code") {
      when(response.getStatus).thenReturn(418)
      requestLog.log(request, response)
      verify(context).setOutboundResponseStatusCode(418)
    }

    it("should use the standard reason phrase exposed by the Servlet 6 status API") {
      when(response.getStatus).thenReturn(404)
      requestLog.log(request, response)
      verify(context).setOutboundResponseReasonPhrase("Not Found")
    }

    it("should add the native content byte count") {
      val responses = mockStatic(classOf[Response])
      try {
        responses.when[java.lang.Long](() => { Response.getContentBytesWritten(response); () }).thenReturn(java.lang.Long.valueOf(1234L))
        requestLog.log(request, response)
        verify(context).setOutboundResponseBytesWritten(1234L)
      } finally responses.close()
    }

    it("should add the committed content length") {
      headers.put(HttpHeader.CONTENT_LENGTH, "9876")
      requestLog.log(request, response)
      verify(context).setOutboundResponseContentLength(9876L)
    }

    Set(Long.MinValue, -1L).foreach { length =>
      it(s"should ignore unknown content length $length") {
        headers.put(HttpHeader.CONTENT_LENGTH, length.toString)
        requestLog.log(request, response)
        verify(context, never).setOutboundResponseContentLength(anyLong())
      }
    }

    it("should record completion time and close the context") {
      val before = Instant.now()
      requestLog.log(request, response)
      val completed = ArgumentCaptor.forClass(classOf[Instant])
      verify(context).setTimeRequestCompleted(completed.capture())
      completed.getValue.isBefore(before) shouldBe false
      completed.getValue.isAfter(Instant.now()) shouldBe false
      verify(service).close(context)
    }
  }

  describe("requests rejected before entering servlet scope") {
    it("should open a context and retain the native request metadata") {
      when(request.getAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT)).thenReturn(null, Nil: _*)
      when(request.getAttribute(HttpLoggingServiceRequestLog.ServletResponseAttribute)).thenReturn(null, Nil: _*)
      when(request.getMethod).thenReturn("BAD")
      when(request.getHeaders).thenReturn(HttpFields.build().put("X-Test", "value"))
      requestLog.log(request, response)
      val inbound = ArgumentCaptor.forClass(classOf[HttpServletRequest])
      verify(service).open()
      verify(context).setInboundRequest(inbound.capture())
      inbound.getValue.getMethod shouldBe "BAD"
      inbound.getValue.getHeader("X-Test") shouldBe "value"
      verify(context).setOutboundRequest(inbound.getValue)
      verify(request).setAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT, context)
      verify(service).close(context)
    }
  }
}
