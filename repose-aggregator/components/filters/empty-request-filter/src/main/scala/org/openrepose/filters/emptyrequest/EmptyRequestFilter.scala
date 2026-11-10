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

import java.io.{ByteArrayInputStream, PushbackInputStream}
import java.nio.charset.StandardCharsets

import jakarta.servlet._
import jakarta.servlet.http.HttpServletRequest
import org.openrepose.commons.utils.io.stream.ServletInputStreamWrapper
import org.openrepose.commons.utils.servlet.http.HttpServletRequestWrapper

/** Opt-in replacement for the legacy RBA empty-request-filter. No configuration settings are required. */
class EmptyRequestFilter extends Filter {

  override def init(filterConfig: FilterConfig): Unit = {}

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val httpRequest = request.asInstanceOf[HttpServletRequest]
    if (httpRequest.getMethod == "POST" || httpRequest.getMethod == "PUT") {
      // Inspect the stream, since absent Content-Length also covers chunked requests.
      // Only one byte is needed, and it must be restored even when it is a NUL byte.
      val stream = new PushbackInputStream(httpRequest.getInputStream, 1)
      val firstByte = stream.read()
      if (firstByte == -1) {
        val body = "{}".getBytes(StandardCharsets.UTF_8)
        val replacement = new HttpServletRequestWrapper(httpRequest,
          new ServletInputStreamWrapper(new ByteArrayInputStream(body))) {
          override def getContentLength: Int = body.length
          override def getContentLengthLong: Long = body.length.toLong
          override def getContentType: String = "application/json"
          override def getCharacterEncoding: String = StandardCharsets.UTF_8.name()
        }
        replacement.replaceHeader("Content-Type", "application/json")
        replacement.replaceHeader("Content-Length", body.length.toString)
        replacement.removeHeader("Transfer-Encoding")
        replacement.removeHeader("Content-Encoding")
        chain.doFilter(replacement, response)
      } else {
        stream.unread(firstByte)
        chain.doFilter(new HttpServletRequestWrapper(httpRequest, new ServletInputStreamWrapper(stream)), response)
      }
    } else {
      chain.doFilter(request, response)
    }
  }

  override def destroy(): Unit = {}
}
