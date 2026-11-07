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
package org.openrepose.valve.jetty;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.openrepose.commons.utils.io.stream.ServletInputStreamWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/** Servlet metadata for requests rejected before Jetty enters a servlet context. */
final class JettyServletLogAdapters {
    private JettyServletLogAdapters() {
    }

    static HttpServletRequest request(Request request) {
        HttpFields headers = request.getHeaders() == null ? HttpFields.EMPTY : request.getHeaders();
        HttpURI uri = request.getHttpURI();
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> request.getMethod();
                    case "getProtocol" -> request.getConnectionMetaData().getProtocol();
                    case "getRequestURI" -> uri == null ? null : uri.getPath();
                    case "getQueryString" -> uri == null ? null : uri.getQuery();
                    case "getRequestURL" -> requestUrl(request);
                    case "getScheme" -> scheme(request);
                    case "isSecure" -> request.isSecure();
                    case "getServerName" -> Request.getServerName(request);
                    case "getServerPort" -> Request.getServerPort(request);
                    case "getLocalAddr", "getLocalName" -> Request.getLocalAddr(request);
                    case "getLocalPort" -> Request.getLocalPort(request);
                    case "getRemoteAddr", "getRemoteHost" -> Request.getRemoteAddr(request);
                    case "getRemotePort" -> Request.getRemotePort(request);
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getHeaders" -> Collections.enumeration(headers.getValuesList((String) args[0]));
                    case "getHeaderNames" -> Collections.enumeration(headers.getFieldNamesCollection());
                    case "getAttribute" -> request.getAttribute((String) args[0]);
                    case "getAttributeNames" -> Collections.enumeration(request.getAttributeNameSet());
                    case "getContentType" -> headers.get(HttpHeader.CONTENT_TYPE);
                    case "getContentLengthLong" -> headers.getLongField(HttpHeader.CONTENT_LENGTH);
                    case "getContentLength" -> (int) headers.getLongField(HttpHeader.CONTENT_LENGTH);
                    case "getParameterMap" -> Map.of();
                    case "getParameterNames" -> Collections.emptyEnumeration();
                    case "getParameter", "getParameterValues", "getCookies", "getCharacterEncoding" -> null;
                    case "getContextPath", "getServletPath" -> "";
                    case "getLocale" -> Locale.getDefault();
                    case "getLocales" -> Collections.enumeration(Collections.singleton(Locale.getDefault()));
                    case "getInputStream" -> new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[0]));
                    case "getReader" -> new BufferedReader(new StringReader(""));
                    case "toString" -> request.toString();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new IllegalStateException("No servlet context is available for this failed request");
                });
    }

    static HttpServletResponse response(Response response) {
        HttpFields headers = response.getHeaders() == null ? HttpFields.EMPTY : response.getHeaders();
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> response.getStatus();
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getHeaders" -> headers.getValuesList((String) args[0]);
                    case "getHeaderNames" -> headers.getFieldNamesCollection();
                    case "containsHeader" -> headers.contains((String) args[0]);
                    case "getContentType" -> headers.get(HttpHeader.CONTENT_TYPE);
                    case "getCharacterEncoding" -> "ISO-8859-1";
                    case "getLocale" -> Locale.getDefault();
                    case "isCommitted" -> response.isCommitted();
                    case "toString" -> response.toString();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new IllegalStateException("No servlet context is available for this failed response");
                });
    }

    private static String scheme(Request request) {
        HttpURI uri = request.getHttpURI();
        return uri != null && uri.getScheme() != null ? uri.getScheme() : request.isSecure() ? "https" : "http";
    }

    private static StringBuffer requestUrl(Request request) {
        if (request.getHttpURI() == null) {
            return new StringBuffer();
        }
        String scheme = scheme(request);
        int port = Request.getServerPort(request);
        if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
            port = -1;
        }
        return new StringBuffer(HttpURI.build(request.getHttpURI()).scheme(scheme)
                .host(Request.getServerName(request)).port(port).query(null).asString());
    }
}
