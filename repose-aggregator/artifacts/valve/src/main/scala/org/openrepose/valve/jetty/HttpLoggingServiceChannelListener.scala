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
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.ee11.servlet.{ServletContextHandler, ServletContextRequest}
import org.openrepose.commons.utils.http.CommonRequestAttributes
import org.openrepose.core.services.httplogging.HttpLoggingService

/** Opens an HTTP logging context when Jetty enters the servlet request scope. */
@Named
class HttpLoggingServiceChannelListener @Inject()(httpLoggingService: HttpLoggingService)
  extends ServletContextHandler.ServletContextScopeListener with StrictLogging {

  override def enterScope(context: ServletContextHandler#ServletScopedContext, request: ServletContextRequest): Unit = {
    if (request != null && request.getAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT) == null) {
      openLoggingContext(request)
    }
  }

  override def exitScope(context: ServletContextHandler#ServletScopedContext, request: ServletContextRequest): Unit = {}

  private def openLoggingContext(request: ServletContextRequest): Unit = {
    val loggingContext = httpLoggingService.open()
    logger.trace("Opened an HTTP Logging Service context {} for {}", s"${loggingContext.hashCode()}", request)

    loggingContext.setInboundRequest(request.getServletApiRequest)
    loggingContext.setOutboundRequest(request.getServletApiRequest)
    logger.trace("Added the request {} to the HTTP Logging Service context {}", request, s"${loggingContext.hashCode()}")

    loggingContext.setTimeRequestReceived(Instant.ofEpochMilli(Request.getTimeStamp(request)))
    logger.trace("Added the time received to the HTTP Logging Service context {}", s"${loggingContext.hashCode()}")

    request.setAttribute(CommonRequestAttributes.HTTP_LOGGING_CONTEXT, loggingContext)
    request.setAttribute(HttpLoggingServiceRequestLog.ServletResponseAttribute, request.getServletApiResponse)
    logger.trace("Added the HTTP Logging Service context {} to the request as an attribute", s"${loggingContext.hashCode()}")
  }
}
