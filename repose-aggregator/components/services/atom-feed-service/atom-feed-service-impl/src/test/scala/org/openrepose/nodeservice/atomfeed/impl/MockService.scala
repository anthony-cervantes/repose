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
package org.openrepose.nodeservice.atomfeed.impl

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.Executors

object MockService {
  case class Response(status: Int = 200, body: Array[Byte] = Array.emptyByteArray,
                      contentType: String = "text/plain;charset=UTF-8")
}

class MockService extends AutoCloseable {
  import MockService.Response

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val executor = Executors.newVirtualThreadPerTaskExecutor()
  server.setExecutor(executor)

  @volatile var requestHandler: HttpExchange => Response = request => {
    if (request.getRequestMethod == "GET")
      Response(body = "<html><body>Hello world!</body></html>".getBytes(UTF_8), contentType = "text/html;charset=UTF-8")
    else Response(404, "Unknown resource!".getBytes(UTF_8))
  }

  server.createContext("/", exchange => {
    try {
      val response = requestHandler(exchange)
      exchange.getResponseHeaders.set("Content-Type", response.contentType)
      exchange.sendResponseHeaders(response.status, if (response.body.isEmpty) -1L else response.body.length.toLong)
      if (response.body.nonEmpty) exchange.getResponseBody.write(response.body)
    } finally exchange.close()
  })

  def start(): Unit = server.start()
  def getUrl: String = s"http://localhost:${server.getAddress.getPort}"

  override def close(): Unit = {
    server.stop(0)
    executor.close()
  }
}
