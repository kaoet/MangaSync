package me.kaoet.mangasync;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Dm5 implements MangaSource {
    private static final String SEARCH_URL = "http://www.dm5.com/search?title=%s&page=%d";
    private static final String BOOK_API_URL = "http://www.dm5.com/template-%s-t1-s1/";
    private static final String BOOK_URL = "http://www.dm5.com/%s/";
    private static final String CHAPTER_URL = "http://www.dm5.com/m%s/";
    private static final String CHAPTER_API_URL = "http://www.dm5.com/m%s/chapterfun.ashx?cid=%s&page=%d";
    private HttpClient http;

    public Dm5(HttpClient http) {
        this.http = http;
    }

    @Override
    public List<Book> search(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, "utf-8");
        int pageN = searchPageN(http.get(String.format(SEARCH_URL, encodedQuery, 1)).asDocument().join());

        return IntStream.rangeClosed(1, Math.min(10, pageN))
                .mapToObj(i -> http.get(String.format(SEARCH_URL, encodedQuery, i)).asDocument()
                        .thenApply(this::searchBooks))
                .collect(Collectors.toList())
                .stream()
                .flatMap(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private int searchPageN(Document doc) {
        int maxPage = 0;
        for (Element a : doc.select("#search_fy a")) {
            try {
                maxPage = Math.max(maxPage, Integer.parseInt(a.text()));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return maxPage;
    }

    private Stream<Book> searchBooks(Document doc) {
        return doc.select(".ssnr_bt a").stream()
                .map(a -> new Book(Dm5.this, StringUtils.substringBetween(a.attr("href"), "/"), a.text()));
    }

    @Override
    public List<Chapter> chapters(String bookId) throws IOException {
        String html = http.get(String.format(BOOK_URL, bookId)).asString().join();
        String mid = StringUtils.substringBetween(html, "var DM5_COMIC_MID=", ";");
        Document doc = http.get(String.format(BOOK_API_URL, mid)).asDocument().join();
        return doc.select("#chapter_1 .tg")
                .stream()
                .map(a -> {
                    String id = StringUtils.substringBetween(a.attr("href"), "/");
                    String name = StringUtils.substringAfter(a.text(), "-").trim();
                    return new Chapter(id, name);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Page> pages(String bookId, String chapterId) throws IOException {
        String referer = String.format(CHAPTER_URL, chapterId);
        List<Page> pages = new ArrayList<>();
        for (; ; ) {
            String eval = http.get(String.format(CHAPTER_API_URL, chapterId, chapterId, pages.size() + 1))
                    .referer(referer)
                    .asString().join();
            List<String> urls = runEval(eval);
            if (urls.isEmpty()) break;
            if (!pages.isEmpty() && pages.get(pages.size() - 1).url.equals(urls.get(0))) break;
            pages.addAll(urls.stream().map(url -> new Page(url, referer)).collect(Collectors.toList()));
        }
        return pages;
    }

    private List<String> runEval(String eval) {
        Context context = Context.enter();
        try {
            Scriptable scope = context.initSafeStandardObjects();
            NativeArray arr = (NativeArray) context.evaluateString(scope, eval, "<Dm5>", 0, null);
            List<String> result = new ArrayList<>();
            for (Object elem : arr) {
                result.add(elem.toString());
            }
            return result;
        } finally {
            Context.exit();
        }
    }
}
