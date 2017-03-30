/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

/**
 * TODO: DB005
 */
public final class Blob {
    private String contentType;
    private byte[] content;
    private InputStream contentStream;

    public Blob(String contentType, byte[] content) {
        this.contentType = contentType;
        this.content = content;
    }

    public Blob(String contentType, InputStream contentStream) {
        this.contentType = contentType;
        this.contentStream = contentStream;
    }

    public Blob(String contentType, URL url) throws IOException {
        this.contentType = contentType;
        this.contentStream = url.openStream();
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public InputStream getContentStream() {
        return contentStream;
    }

    public long length() {
        return 0L;
    }

    public String digest() {
        return null;
    }

    public Map<String, Object> getProperties() {
        return null;
    }
}
