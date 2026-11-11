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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import org.eclipse.jetty.http.HostPortHttpField;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpException;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.internal.HttpConnection;

/** HTTP/1 compatibility for a legacy monitor, before Jetty validates HostPort. */
public final class LegacyMonitorHttpConnectionFactory extends HttpConnectionFactory {
    public static final String ENABLED_PROPERTY = "repose.legacyMonitorHost.enabled";

    private final boolean enabled = Boolean.getBoolean(ENABLED_PROPERTY);

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint) {
        if (!enabled) {
            return super.newConnection(connector, endPoint);
        }
        HttpConnection connection = new HttpConnection(getHttpConfiguration(), connector, endPoint) {
            private boolean monitorRequest;

            @Override
            protected HttpStreamOverHTTP1 newHttpStream(String method, String uri, HttpVersion version) {
                monitorRequest = "GET".equals(method) && "/".equals(uri) && version == HttpVersion.HTTP_1_1;
                return super.newHttpStream(method, uri, version);
            }

            @Override
            protected HttpParser newHttpParser(HttpCompliance compliance) {
                HttpParser original = super.newHttpParser(compliance);
                HttpParser parser = new MonitorParser((HttpParser.RequestHandler) original.getHandler(),
                        getHttpConfiguration().getRequestHeaderSize(), compliance, () -> monitorRequest);
                parser.setHeaderCacheSize(original.getHeaderCacheSize());
                parser.setHeaderCacheCaseSensitive(original.isHeaderCacheCaseSensitive());
                return parser;
            }
        };
        connection.setTransferEncodingChunkMaxLength(getTransferEncodingChunkMaxLength());
        return configure(connection, connector, endPoint);
    }

    /**
     * Only candidate requests need buffering. Jetty still parses the request line,
     * every header, framing, and body with the original compliance settings.
     * Buffer just the headers (bounded by the original header-size limit), leaving
     * body/pipelined bytes in the connection's original buffer.
     */
    static final class MonitorParser extends HttpParser {
        private final int maxHeaderBytes;
        private final BooleanSupplier candidate;
        private final ByteArrayOutputStream headers = new ByteArrayOutputStream();
        private int lineLength;
        private int previous;

        MonitorParser(RequestHandler handler, int maxHeaderBytes, HttpCompliance compliance, BooleanSupplier candidate) {
            super(handler, maxHeaderBytes, compliance);
            this.maxHeaderBytes = maxHeaderBytes;
            this.candidate = candidate;
        }

        @Override
        protected boolean parseFields(ByteBuffer buffer) {
            if (getState() != State.HEADER || !candidate.getAsBoolean()) {
                return super.parseFields(buffer);
            }
            while (buffer.hasRemaining()) {
                int next = buffer.get() & 0xff;
                headers.write(next);
                if (maxHeaderBytes > 0 && getHeaderLength() + headers.size() > maxHeaderBytes) {
                    throw new HttpException.RuntimeException(431, "Request Header Fields Too Large");
                }
                // Recognize a blank line even for invalid line endings, so Jetty
                // can reject them itself rather than waiting for another read.
                if (next == '\n') {
                    if (lineLength == 0 || (lineLength == 1 && previous == '\r')) {
                        byte[] fields = normalize(headers.toString(StandardCharsets.ISO_8859_1))
                                .getBytes(StandardCharsets.ISO_8859_1);
                        headers.reset();
                        return super.parseFields(ByteBuffer.wrap(fields));
                    }
                    lineLength = 0;
                } else {
                    lineLength++;
                }
                previous = next;
            }
            return false;
        }

        @Override
        public void reset() {
            super.reset();
            headers.reset();
            lineLength = 0;
            previous = 0;
        }
    }

    static String normalize(String headers) {
        // Do not repair line endings, duplicate headers, or folded Host values.
        String[] lines = headers.split("\r\n", -1);
        int hostLine = -1;
        for (int i = 0; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon >= 0 && "Host".equalsIgnoreCase(lines[i].substring(0, colon))) {
                if (hostLine >= 0) {
                    return headers;
                }
                hostLine = i;
            }
        }
        if (hostLine < 0 || (hostLine + 1 < lines.length &&
                (lines[hostLine + 1].startsWith(" ") || lines[hostLine + 1].startsWith("\t")))) {
            return headers;
        }
        String line = lines[hostLine];
        int start = line.indexOf(':') + 1;
        int end = line.length();
        while (start < end && (line.charAt(start) == ' ' || line.charAt(start) == '\t')) {
            start++;
        }
        while (end > start && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        String value = line.substring(start, end);
        String suffix = "/lb_monitor";
        if (!value.endsWith(suffix)) {
            return headers;
        }
        String authority = value.substring(0, value.length() - suffix.length());
        // The exact suffix is the sole exception. Validate the remaining raw
        // authority with Jetty's strict constructor, not HostPort.unsafe().
        HostPortHttpField validated = new HostPortHttpField(authority);
        if (validated.getHost().isEmpty()) {
            throw new HttpException.RuntimeException(400, "Blank Host");
        }
        lines[hostLine] = line.substring(0, start) + authority + line.substring(end);
        return String.join("\r\n", lines);
    }
}
