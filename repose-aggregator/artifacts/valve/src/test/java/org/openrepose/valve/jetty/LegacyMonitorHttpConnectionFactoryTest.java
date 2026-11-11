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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpParser;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LegacyMonitorHttpConnectionFactoryTest {
    private static ByteBuffer bytes(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static HttpParser parser(HttpParser.RequestHandler handler, int limit, boolean candidate) {
        return new LegacyMonitorHttpConnectionFactory.MonitorParser(
                handler, limit, HttpCompliance.RFC7230_LEGACY, () -> candidate);
    }

    @Test
    public void handlesEveryPossibleReadBoundaryBeforeStrictHostValidation() {
        String request = "GET / HTTP/1.1\r\nHost: example.test:8080/lb_monitor\r\nConnection: Close\r\n\r\n";
        for (int split = 1; split < request.length(); split++) {
            HttpParser.RequestHandler handler = mock(HttpParser.RequestHandler.class);
            HttpParser parser = parser(handler, 8192, true);
            parser.parseNext(bytes(request.substring(0, split)));
            parser.parseNext(bytes(request.substring(split)));
            verify(handler, never()).badMessage(any());
            verify(handler).headerComplete();
            ArgumentCaptor<HttpField> fields = ArgumentCaptor.forClass(HttpField.class);
            verify(handler, times(2)).parsedHeader(fields.capture());
            assertEquals("example.test:8080", fields.getAllValues().get(0).getValue());
        }
    }

    @Test
    public void leavesBodyAndPipelinedBytesInTheOriginalBufferAndResets() {
        HttpParser.RequestHandler handler = mock(HttpParser.RequestHandler.class);
        when(handler.headerComplete()).thenReturn(true);
        HttpParser parser = parser(handler, 8192, true);
        ByteBuffer input = bytes("GET / HTTP/1.1\r\nHost: example.test/lb_monitor\r\nContent-Length: 4\r\n\r\nbodyNEXT");
        assertTrue(parser.parseNext(input));
        assertEquals("bodyNEXT", StandardCharsets.ISO_8859_1.decode(input).toString());
        parser.reset();
        parser.parseNext(bytes("GET / HTTP/1.1\r\nHost: second.test/lb_monitor\r\n\r\n"));
        verify(handler, times(2)).headerComplete();
        verify(handler, never()).badMessage(any());
    }

    @Test
    public void retainsTheOriginalHeaderSizeLimitBeforeShortening() {
        HttpParser.RequestHandler handler = mock(HttpParser.RequestHandler.class);
        parser(handler, 64, true).parseNext(bytes("GET / HTTP/1.1\r\nHost: example.test/lb_monitor\r\nX-Large: " + "a".repeat(50) + "\r\n\r\n"));
        ArgumentCaptor<HttpException> error = ArgumentCaptor.forClass(HttpException.class);
        verify(handler).badMessage(error.capture());
        assertEquals(431, error.getValue().getCode());
        verify(handler, never()).headerComplete();
    }

    @Test
    public void doesNotRepairDuplicateOrFoldedHostValues() {
        for (String fields : new String[] {
                "Host: example.test/lb_monitor\r\nHost: other.test\r\n\r\n",
                "Host: example.test/lb_monitor\r\n continuation\r\n\r\n"}) {
            assertEquals(fields, LegacyMonitorHttpConnectionFactory.normalize(fields));
        }
    }

    @Test
    public void keepsStrictHostAndLineEndingValidation() {
        for (String host : new String[] {"example.test:", "example.test:bad", "example.test:65536",
                "user@example.test", "example.test/other", "[::1", "bad host/lb_monitor",
                "example.test:/lb_monitor", "/lb_monitor", "example.test/lb_monitor?query"}) {
            HttpParser.RequestHandler handler = mock(HttpParser.RequestHandler.class);
            parser(handler, 8192, true).parseNext(bytes("GET / HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
            verify(handler).badMessage(any());
            verify(handler, never()).headerComplete();
        }
        HttpParser.RequestHandler handler = mock(HttpParser.RequestHandler.class);
        parser(handler, 8192, true).parseNext(bytes("GET / HTTP/1.1\r\nHost: example.test/lb_monitor\n\n"));
        verify(handler).badMessage(any());
    }

    @Test
    public void nonCandidatesUseTheUnmodifiedParser() {
        HttpParser.RequestHandler handler = mock(HttpParser.RequestHandler.class);
        parser(handler, 8192, false).parseNext(bytes("GET / HTTP/1.1\r\nHost: example.test/lb_monitor\r\n\r\n"));
        verify(handler).badMessage(any());
        verify(handler, never()).headerComplete();
    }
}
