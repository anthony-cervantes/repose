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
package org.openrepose.commons.utils.io.stream;

import org.junit.Before;
import org.junit.Test;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServletInputStreamWrapperTest {

    private InputStream stream;
    private ServletInputStreamWrapper wrapper;

    @Before
    public void setUp() {
        stream = mock(InputStream.class);
        wrapper = new ServletInputStreamWrapper(stream);
    }

    @Test
    public void shouldReadFromInputStream() throws IOException {
        wrapper.read();
        verify(stream).read();
    }

    @Test
    public void shouldReadBytesFromInputStream() throws IOException {
        byte[] bArr = new byte[10];
        wrapper.read(bArr);
        verify(stream).read(bArr);
    }

    @Test
    public void shouldReadConstrainedBytesFromInputStream() throws IOException {
        byte[] bArr = new byte[10];
        wrapper.read(bArr, 2, 2);
        verify(stream).read(bArr, 2, 2);
    }

    @Test
    public void shouldDelegateSkip() throws IOException {
        wrapper.skip(10L);
        verify(stream).skip(10L);
    }

    @Test
    public void shouldDelegateForAvailablity() throws Exception {
        wrapper.available();
        verify(stream).available();
    }

    @Test
    public void shouldDelegateForClose() throws Exception {
        wrapper.close();
        verify(stream).close();
    }

    @Test
    public void shouldDelegateMark() throws IOException {
        wrapper.mark(10);
        verify(stream).mark(10);
    }

    @Test
    public void shouldDelegateReset() throws IOException {
        wrapper.reset();
        verify(stream).reset();
    }

    @Test
    public void shouldDelegateMarkSupported() throws IOException {
        wrapper.markSupported();
        verify(stream).markSupported();
    }

    @Test
    public void shouldReportBufferedEofAndReset() throws IOException {
        wrapper = new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[] {1}));
        assertFalse(wrapper.isFinished());
        wrapper.read();
        assertTrue(wrapper.isFinished());
        wrapper.reset();
        assertFalse(wrapper.isFinished());
    }

    @Test
    public void shouldKeepEofAfterZeroLengthRead() throws IOException {
        when(stream.read()).thenReturn(-1);
        wrapper.read();
        wrapper.read(new byte[0]);
        assertTrue(wrapper.isFinished());
    }

    @Test
    public void shouldDelegateServletReadinessAndListener() {
        ServletInputStream servletStream = mock(ServletInputStream.class);
        ReadListener listener = mock(ReadListener.class);
        wrapper = new ServletInputStreamWrapper(servletStream);
        when(servletStream.isReady()).thenReturn(true);
        when(servletStream.isFinished()).thenReturn(true);
        assertTrue(wrapper.isReady());
        assertTrue(wrapper.isFinished());
        wrapper.setReadListener(listener);
        verify(servletStream).setReadListener(listener);
    }
}
