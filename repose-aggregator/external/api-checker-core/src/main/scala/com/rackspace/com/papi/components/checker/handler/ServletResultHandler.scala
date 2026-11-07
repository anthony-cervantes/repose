/***
 *   Copyright 2014 Rackspace US, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.rackspace.com.papi.components.checker.handler

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse

import com.rackspace.com.papi.components.checker._
import com.rackspace.com.papi.components.checker.servlet._
import com.rackspace.com.papi.components.checker.step.results.{ErrorResult, Result}
import org.w3c.dom.Document

import scala.jdk.CollectionConverters._

class ServletResultHandler extends ResultHandler {
  def init (validator : Validator, checker : Option[Document]) : Unit = {}

  def handle (req : CheckerServletRequest, resp : CheckerServletResponse, chain : FilterChain, result : Result)  : Unit = {
    if (!result.valid) {
      result match {
        case errorResult : ErrorResult =>
          sendError(errorResult, resp)
      }
    }
  }

  /** Write validation diagnostics in a protocol-independent UTF-8 response body. */
  @throws[IOException]
  def sendError(er: ErrorResult, resp: HttpServletResponse): Unit = {
    // resetBuffer also rejects an already committed response, as sendError did.
    resp.resetBuffer()
    er.headers.asScala.foreach { case (name, value) => resp.addHeader(name, value) }
    val body = Option(er.message).getOrElse("").getBytes(UTF_8)
    resp.setStatus(er.code)
    resp.setContentType("text/plain;charset=UTF-8")
    resp.setContentLength(body.length)
    resp.setHeader("Cache-Control", "no-store")
    resp.getOutputStream.write(body)
  }
}
