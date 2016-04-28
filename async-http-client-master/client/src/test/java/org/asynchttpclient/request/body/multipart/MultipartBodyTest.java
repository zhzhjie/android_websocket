/*
 * Copyright (c) 2016 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.asynchttpclient.request.body.multipart;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.asynchttpclient.request.body.Body.BodyState;
import org.testng.annotations.Test;

public class MultipartBodyTest {

    @Test
    public void transferWithCopy() throws Exception {
        try (MultipartBody multipartBody = buildMultipart()) {
            long tranferred = transferWithCopy(multipartBody);
            assertEquals(tranferred, multipartBody.getContentLength());
        }
    }

    @Test
    public void transferZeroCopy() throws Exception {
        try (MultipartBody multipartBody = buildMultipart()) {
            long tranferred = transferZeroCopy(multipartBody);
            assertEquals(tranferred, multipartBody.getContentLength());
        }
    }

    private File getTestfile() throws URISyntaxException {
        final ClassLoader cl = MultipartBodyTest.class.getClassLoader();
        final URL url = cl.getResource("textfile.txt");
        assertNotNull(url);
        return new File(url.toURI());
    }

    private MultipartBody buildMultipart() throws URISyntaxException {
        List<Part> parts = new ArrayList<>();
        parts.add(new FilePart("filePart", getTestfile()));
        parts.add(new ByteArrayPart("baPart", "testMultiPart".getBytes(UTF_8), "application/test", UTF_8, "fileName"));
        parts.add(new StringPart("stringPart", "testString"));
        return MultipartUtils.newMultipartBody(parts, HttpHeaders.EMPTY_HEADERS);
    }

    private long transferWithCopy(MultipartBody multipartBody) throws IOException {
        final ByteBuf buffer = Unpooled.buffer(8192);
        while (multipartBody.transferTo(buffer) != BodyState.STOP) {
        }
        return buffer.readableBytes();
    }

    private static long transferZeroCopy(MultipartBody multipartBody) throws IOException {

        final ByteBuffer buffer = ByteBuffer.allocate(8192);
        final AtomicLong transferred = new AtomicLong();

        WritableByteChannel mockChannel = new WritableByteChannel() {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public void close() throws IOException {
            }

            @Override
            public int write(ByteBuffer src) throws IOException {
                int written = src.remaining();
                transferred.set(transferred.get() + written);
                src.position(src.limit());
                return written;
            }
        };

        while (transferred.get() < multipartBody.getContentLength()) {
            multipartBody.transferTo(mockChannel);
            buffer.clear();
        }
        return transferred.get();
    }
}
