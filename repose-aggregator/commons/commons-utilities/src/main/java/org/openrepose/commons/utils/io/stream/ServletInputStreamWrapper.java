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

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author zinic
 */
public class ServletInputStreamWrapper extends ServletInputStream {

    private final InputStream is;
    private boolean finished;

    public ServletInputStreamWrapper(InputStream is) {
        this.is = is;
    }

    @Override
    public int read() throws IOException {
        int result = is.read();
        finished = result == -1;
        return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int result = is.read(b);
        if (result != 0) {
            finished = result == -1;
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = is.read(b, off, len);
        if (result != 0) {
            finished = result == -1;
        }
        return result;
    }

    @Override
    public long skip(long n) throws IOException {
        return is.skip(n);
    }

    @Override
    public int available() throws IOException {
        return is.available();
    }

    @Override
    public void close() throws IOException {
        is.close();
        finished = true;
    }

    @Override
    public void mark(int readlimit) {
        is.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        is.reset();
        finished = false;
    }

    @Override
    public boolean markSupported() {
        return is.markSupported();
    }

    @Override
    public boolean isFinished() {
        if (finished) {
            return true;
        }
        if (is instanceof ServletInputStream) {
            return ((ServletInputStream) is).isFinished();
        }
        return is instanceof ByteArrayInputStream && ((ByteArrayInputStream) is).available() == 0;
    }

    @Override
    public boolean isReady() {
        return !(is instanceof ServletInputStream) || ((ServletInputStream) is).isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
        if (!(is instanceof ServletInputStream)) {
            throw new IllegalStateException("Non-blocking reads require a servlet input stream");
        }
        ((ServletInputStream) is).setReadListener(listener);
    }

}
