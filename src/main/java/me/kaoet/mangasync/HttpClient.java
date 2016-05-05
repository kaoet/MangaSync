package me.kaoet.mangasync;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class HttpClient {
    private ExecutorService executor;
    private NetworkListener listener;

    public HttpClient(ExecutorService executor, NetworkListener listener) {
        this.executor = executor;
        this.listener = listener;
    }

    public HttpRequest get(String url) {
        return new HttpRequest(url);
    }

    public class HttpRequest {
        private static final int MAX_RETRY = 5;
        private static final int RETRY_INTERVAL = 200;

        private URL url;
        private String referer;
        private int timeout = 3000;

        HttpRequest(String url) {
            try {
                this.url = new URL(url);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        public HttpRequest referer(String referer) {
            this.referer = referer;
            return this;
        }

        public CompletableFuture<byte[]> asBytes() {
            return CompletableFuture.supplyAsync(() -> {
                int retried = 0;
                for (; ; ) {
                    try {
                        System.out.println("GET  " + url);
                        listener.requestStart(hashCode(), "GET", url.toString());

                        URLConnection connection = url.openConnection();
                        connection.setConnectTimeout(3000);
                        connection.setReadTimeout(timeout);
                        if (referer != null) {
                            connection.setRequestProperty("Referer", referer);
                        }
                        byte[] bytes = IOUtils.toByteArray(url.openStream());

                        listener.requestFinish(hashCode());

                        return bytes;
                    } catch (FileNotFoundException e) {
                        listener.progress(hashCode(), e.getMessage());
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        System.err.println(e);
                        listener.progress(hashCode(), e.getMessage());

                        retried++;
                        if (retried >= MAX_RETRY) {
                            throw new RuntimeException(e);
                        }
                        try {
                            Thread.sleep(RETRY_INTERVAL);
                        } catch (InterruptedException e1) {
                            listener.progress(hashCode(), e1.getMessage());
                            throw new RuntimeException(e1);
                        }
                    }
                }
            }, executor);
        }

        public CompletableFuture<Void> toPath(Path path) {
            return asBytes().thenApply(bytes -> {
                try {
                    FileUtils.writeByteArrayToFile(path.toFile(), bytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }

        public CompletableFuture<String> asString(Charset charset) {
            return asBytes().thenApply(bytes -> new String(bytes, charset));
        }

        public CompletableFuture<String> asString() {
            return asString(StandardCharsets.UTF_8);
        }

        public CompletableFuture<Document> asDocument(Charset charset) {
            return asString(charset).thenApply(Jsoup::parse);
        }

        public CompletableFuture<Document> asDocument() {
            return asDocument(StandardCharsets.UTF_8);
        }
    }

}
