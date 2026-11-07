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
package org.openrepose.commons.utils.test.mocks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MocksServletIntegrationTest {
    @Test
    public void servesGetAndPostThroughJakartaServlets() throws Exception {
        Server server = new Server(0);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        ResourceConfig resources = new ResourceConfig().packages("org.openrepose.commons.utils.test.mocks");
        context.addServlet(new ServletHolder(new ServletContainer(resources)), "/*");
        server.setHandler(context);
        try {
            server.start();
            int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                URI uri = URI.create("http://localhost:" + port + "/echo?example=value");
                HttpResponse<String> get = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, get.statusCode());
                assertEquals("somevalue", get.headers().firstValue("x-request-id").orElse(""));
                assertTrue(get.body().contains("GET"));
                assertTrue(get.body().contains("example"));
                HttpRequest post = HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString("hello")).build();
                HttpResponse<String> response = client.send(post, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("POST"));
                assertTrue(response.body().contains("hello"));
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }
}
