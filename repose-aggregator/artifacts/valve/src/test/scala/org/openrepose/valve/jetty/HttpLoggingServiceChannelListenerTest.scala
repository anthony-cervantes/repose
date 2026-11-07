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
import org.eclipse.jetty.ee11.servlet.{ServletApiRequest, ServletApiResponse, ServletContextRequest}
import org.eclipse.jetty.server.Request
import org.junit.runner.RunWith
import org.mockito.Mockito.{mockStatic, verify, verifyNoInteractions, when}
import org.openrepose.commons.utils.http.CommonRequestAttributes
import org.openrepose.core.services.httplogging.{HttpLoggingContext, HttpLoggingService}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar

@RunWith(classOf[JUnitRunner])
class HttpLoggingServiceChannelListenerTest extends AnyFunSpec with BeforeAndAfterEach with MockitoSugar with Matchers {
  var service: HttpLoggingService = _
  var context: HttpLoggingContext = _
  var listener: HttpLoggingServiceChannelListener = _
  var request: ServletContextRequest = _
  var servletRequest: ServletApiRequest = _
  var servletResponse: ServletApiResponse = _

  override def beforeEach(): Unit = {
    service = mock[HttpLoggingService]
    context = mock[HttpLoggingContext]
    listener = new HttpLoggingServiceChannelListener(service)
    request = mock[ServletContextRequest]
    servletRequest = mock[ServletApiRequest]
    servletResponse = mock[ServletApiResponse]
    when(service.open()).thenReturn(context)
    when(request.getServletApiRequest).thenReturn(servletRequest)
    when(request.getServletApiResponse).thenReturn(servletResponse)
  }

  describe("enterScope") {
    it("should open a logging context") {
      listener.enterScope(null, request)
      verify(service).open()
    }

    it("should add the servlet request to the logging context") {
      listener.enterScope(null, request)
      verify(context).setInboundRequest(servletRequest)
      verify(context).setOutboundRequest(servletRequest)
    }

    it("should retain the native request arrival timestamp") {
      val timestamp = 1234567890L
      val requests = mockStatic(classOf[Request])
      try {
        requests.when[java.lang.Long](() => { Request.getTimeStamp(request); () }).thenReturn(java.lang.Long.valueOf(timestamp))
        listener.enterScope(null, request)
        verify(context).setTimeRequestReceived(Instant.ofEpochMilli(timestamp))
      } finally requests.close()
    }

    it("should retain the context and servlet response as request attributes") {
      listener.enterScope(null, request)
      verify(request).setAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT, context)
      verify(request).setAttribute(HttpLoggingServiceRequestLog.ServletResponseAttribute, servletResponse)
    }

    it("should not reopen an existing logging context on scope reentry") {
      when(request.getAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT)).thenReturn(context, Nil: _*)
      listener.enterScope(null, request)
      verifyNoInteractions(service)
    }

    it("should ignore lifecycle scope entries without a request") {
      listener.enterScope(null, null)
      verifyNoInteractions(service)
    }
  }
}
