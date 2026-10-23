package com.restaurant.restaurantpos.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Request wrapper that buffers the HTTP request body so it can be read multiple times.
 * Used by LoginRateLimitingFilter to inspect the login email without exhausting the stream
 * for downstream Spring controllers.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    public static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024; // 64 KiB = 65,536 bytes

    public static class PayloadTooLargeException extends IOException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_BYTES);
    }

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodyBytes) {
            throw new PayloadTooLargeException("Request body exceeds maximum allowed size of " + maxBodyBytes + " bytes");
        }

        InputStream is = request.getInputStream();
        if (is != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min((int) (contentLength > 0 ? contentLength : 1024), maxBodyBytes));
            byte[] buffer = new byte[4096];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > maxBodyBytes) {
                    throw new PayloadTooLargeException("Request body exceeds maximum allowed size of " + maxBodyBytes + " bytes");
                }
                baos.write(buffer, 0, bytesRead);
            }
            this.cachedBody = baos.toByteArray();
        } else {
            this.cachedBody = new byte[0];
        }
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new CachedServletInputStream(bais);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private static class CachedServletInputStream extends ServletInputStream {
        private final InputStream sourceStream;

        public CachedServletInputStream(InputStream sourceStream) {
            this.sourceStream = sourceStream;
        }

        @Override
        public boolean isFinished() {
            try {
                return sourceStream.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // No-op for synchronous servlets
        }

        @Override
        public int read() throws IOException {
            return sourceStream.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return sourceStream.read(b, off, len);
        }
    }
}
