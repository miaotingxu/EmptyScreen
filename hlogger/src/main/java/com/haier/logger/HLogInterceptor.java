package com.haier.logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.GzipSource;

public class HLogInterceptor implements Interceptor {

    public enum Level { NONE, BASIC, HEADERS, BODY }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final String TOP_LEFT    = "┌";
    private static final String MIDDLE_LEFT = "├";
    private static final String BOTTOM_LEFT = "└";
    private static final String VERTICAL    = "│ ";
    private static final String DOUBLE_LINE =
            "────────────────────────────────────────────────────────────";

    private final Level level;
    private final String tag;
    private final Set<String> maskedHeaders;
    private final int maxBodySize;
    private final Set<String> excludeUrls;
    private final boolean onlyErrors;

    private HLogInterceptor(Builder builder) {
        this.level = builder.level;
        this.tag = builder.tag;
        this.maskedHeaders = builder.maskedHeaders;
        this.maxBodySize = builder.maxBodySize;
        this.excludeUrls = builder.excludeUrls;
        this.onlyErrors = builder.onlyErrors;
    }

    public HLogInterceptor() {
        this(new Builder());
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (level == Level.NONE) {
            return chain.proceed(chain.request());
        }

        Request request = chain.request();
        String url = request.url().toString();

        if (isExcluded(url)) {
            return chain.proceed(request);
        }

        long startNs = System.nanoTime();
        Response response;
        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            HLogger.e(tag, "HTTP FAILED: " + e.getMessage());
            throw e;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        if (onlyErrors && response.isSuccessful()) {
            return response;
        }

        logExchange(request, response, durationMs);
        return response;
    }

    private void logExchange(Request request, Response response, long durationMs) {
        StringBuilder sb = new StringBuilder();

        // top
        sb.append(TOP_LEFT).append(DOUBLE_LINE).append("\n");

        // identity line
        long pid = android.os.Process.myPid();
        sb.append(VERTICAL).append("PID: ").append(pid)
          .append(" | Thread: ").append(Thread.currentThread().getName())
          .append("\n");
        sb.append(MIDDLE_LEFT).append(DOUBLE_LINE).append("\n");

        // request section
        sb.append(VERTICAL).append("\n");
        sb.append(VERTICAL).append("--> ").append(request.method()).append(" ")
          .append(request.url()).append("\n");

        long reqBodyLen = -1;
        String reqBody = null;
        if (level == Level.BODY && request.body() != null) {
            reqBody = readRequestBody(request.body());
            if (reqBody != null) reqBodyLen = reqBody.length();
        }

        if (level.ordinal() >= Level.HEADERS.ordinal()) {
            appendHeaders(sb, request);
        }

        if (reqBody != null && !reqBody.isEmpty()) {
            sb.append(VERTICAL).append("\n");
            appendBodyLines(sb, truncate(formatBodyIfJson(reqBody)));
            sb.append(VERTICAL).append("--> END ").append(request.method())
              .append(" (").append(reqBodyLen).append("-byte body)\n");
        } else {
            sb.append(VERTICAL).append("--> END ").append(request.method()).append("\n");
        }

        // separator
        sb.append(MIDDLE_LEFT).append(DOUBLE_LINE).append("\n");

        // response section
        sb.append(VERTICAL).append("\n");
        sb.append(VERTICAL).append("<-- ").append(response.code()).append(" ")
          .append(response.message()).append(" ").append(request.url())
          .append(" (").append(durationMs).append("ms)\n");

        if (level.ordinal() >= Level.HEADERS.ordinal()) {
            appendHeaders(sb, response);
        }

        if (level == Level.BODY && response.body() != null) {
            try {
                String body = readResponseBody(response);
                sb.append(VERTICAL).append("\n");
                appendBodyLines(sb, truncate(formatBodyIfJson(body)));
                sb.append(VERTICAL).append("<-- END HTTP (")
                  .append(body.length()).append("-byte body)\n");
            } catch (IOException ignored) {
                sb.append(VERTICAL).append("<-- END HTTP\n");
            }
        } else {
            sb.append(VERTICAL).append("<-- END HTTP\n");
        }

        // bottom
        sb.append(BOTTOM_LEFT).append(DOUBLE_LINE);

        if (response.isSuccessful()) {
            HLogger.d(tag, sb.toString());
        } else {
            HLogger.e(tag, sb.toString());
        }
    }

    private void appendHeaders(StringBuilder sb, Request request) {
        for (int i = 0; i < request.headers().size(); i++) {
            String name = request.headers().name(i);
            String value = maskedHeaders.contains(name) ? "****" : request.headers().value(i);
            sb.append(VERTICAL).append(name).append(": ").append(value).append("\n");
        }
        RequestBody body = request.body();
        if (body != null) {
            if (body.contentType() != null && request.header("Content-Type") == null) {
                sb.append(VERTICAL).append("Content-Type: ").append(body.contentType()).append("\n");
            }
            try {
                long len = body.contentLength();
                if (len != -1 && request.header("Content-Length") == null) {
                    sb.append(VERTICAL).append("Content-Length: ").append(len).append("\n");
                }
            } catch (IOException ignored) {}
        }
    }

    private void appendHeaders(StringBuilder sb, Response response) {
        for (int i = 0; i < response.headers().size(); i++) {
            String name = response.headers().name(i);
            String value = response.headers().value(i);
            sb.append(VERTICAL).append(name).append(": ").append(value).append("\n");
        }
    }

    private void appendBodyLines(StringBuilder sb, String body) {
        if (body == null || body.isEmpty()) return;
        for (String line : body.split("\n", -1)) {
            sb.append(VERTICAL).append(line).append("\n");
        }
    }

    private String readResponseBody(Response response) throws IOException {
        ResponseBody peekBody = response.peekBody(maxBodySize);
        BufferedSource source = peekBody.source();
        source.request(Long.MAX_VALUE);
        Buffer buffer = source.buffer();

        String encoding = response.header("Content-Encoding");
        if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
            Buffer decompressed = new Buffer();
            GzipSource gzipSource = new GzipSource(buffer.clone());
            try {
                decompressed.writeAll(gzipSource);
            } finally {
                gzipSource.close();
            }
            buffer = decompressed;
        }

        Charset charset = UTF8;
        MediaType contentType = peekBody.contentType();
        if (contentType != null) {
            charset = contentType.charset(UTF8);
        }
        return buffer.readString(charset);
    }

    private String readRequestBody(RequestBody body) {
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            MediaType contentType = body.contentType();
            Charset charset = UTF8;
            if (contentType != null) {
                charset = contentType.charset(UTF8);
            }
            return buffer.readString(charset);
        } catch (IOException e) {
            return null;
        }
    }

    private String formatBodyIfJson(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return LogFormatter.formatJson(body);
        }
        return body;
    }

    private String truncate(String s) {
        if (s.length() <= maxBodySize) return s;
        return s.substring(0, maxBodySize) + "... (truncated)";
    }

    private boolean isExcluded(String url) {
        for (String pattern : excludeUrls) {
            String regex = pattern.replace("*", ".*");
            if (url.matches(regex)) return true;
        }
        return false;
    }

    public static class Builder {
        private Level level = Level.BODY;
        private String tag = "HTTP3.STACK";
        private Set<String> maskedHeaders = new HashSet<>();
        private int maxBodySize = 10 * 1024;
        private Set<String> excludeUrls = new HashSet<>();
        private boolean onlyErrors = false;

        public Builder logLevel(Level level) { this.level = level; return this; }
        public Builder tag(String tag) { this.tag = tag; return this; }
        public Builder maskHeaders(String... headers) { maskedHeaders.addAll(Arrays.asList(headers)); return this; }
        public Builder maxBodySize(int size) { this.maxBodySize = size; return this; }
        public Builder excludeUrls(String... urls) { excludeUrls.addAll(Arrays.asList(urls)); return this; }
        public Builder onlyErrors(boolean only) { this.onlyErrors = only; return this; }
        public HLogInterceptor build() { return new HLogInterceptor(this); }
    }
}
