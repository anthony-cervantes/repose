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

import com.typesafe.scalalogging.StrictLogging
import jakarta.inject.{Inject, Named}
import org.eclipse.jetty.server.{Request, RequestLog, Response}
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.{HttpFields, HttpHeader, HttpStatus}
import org.openrepose.commons.utils.http.CommonRequestAttributes
import org.openrepose.core.services.httplogging.HttpLoggingContext
import org.openrepose.core.services.httplogging.HttpLoggingService

/** Closes HTTP logging contexts when Jetty completes an HTTP interaction. */
@Named
class HttpLoggingServiceRequestLog @Inject()(httpLoggingService: HttpLoggingService)
  extends RequestLog with StrictLogging {

  override def log(request: Request, response: Response): Unit = {
    val loggingContext = Option(request.getAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT))
      .map(_.asInstanceOf[HttpLoggingContext]).getOrElse {
        val context = httpLoggingService.open()
        val servletRequest = JettyServletLogAdapters.request(request)
        context.setInboundRequest(servletRequest)
        context.setOutboundRequest(servletRequest)
        context.setTimeRequestReceived(Instant.ofEpochMilli(Request.getTimeStamp(request)))
        request.setAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT, context)
        context
      }

    val servletResponse = Option(request.getAttribute(HttpLoggingServiceRequestLog.ServletResponseAttribute))
      .map(_.asInstanceOf[HttpServletResponse]).getOrElse(JettyServletLogAdapters.response(response))
    loggingContext.setTimeRequestCompleted(Instant.now)
    loggingContext.setOutboundResponse(servletResponse)
    loggingContext.setOutboundResponseStatusCode(response.getStatus)
    loggingContext.setOutboundResponseReasonPhrase(HttpStatus.getMessage(response.getStatus))
    loggingContext.setOutboundResponseBytesWritten(Response.getContentBytesWritten(response))

    val headers = Option(response.getHeaders).getOrElse(HttpFields.EMPTY)
    val responseContentLength = headers.getLongField(HttpHeader.CONTENT_LENGTH)
    if (responseContentLength != Long.MinValue && responseContentLength != -1) {
      loggingContext.setOutboundResponseContentLength(responseContentLength)
    }

    logger.trace("Added the outbound response {} to the HTTP Logging Service context {}", response, s"${loggingContext.hashCode()}")
    httpLoggingService.close(loggingContext)
    logger.trace("Closed the HTTP Logging Service context {} for {}", s"${loggingContext.hashCode()}", request)
  }
}

object HttpLoggingServiceRequestLog {
  private[jetty] val ServletResponseAttribute = classOf[HttpLoggingServiceRequestLog].getName + ".servletResponse"
}
