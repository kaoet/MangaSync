package me.kaoet.mangasync;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Dmzj implements MangaSource {
    private static final String SEARCH_URL = "http://www.dmzj.com/dynamic/o_search/index/%s/%d";
    private static final String BOOK_URL = "http://manhua.dmzj.com/%s/";
    private static final String CHAPTER_URL = "http://manhua.dmzj.com/%s/%s.shtml";
    private static final Pattern REGEX_EVAL = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\)\\{.*");
    private static final String PAGE_EXTRACTION = ";eval(pages)";
    private static final String PAGE_URL = "http://images.dmzj.com/%s";
    private static final String REFERER = "http://manhua.dmzj.com/";
    private static final Pattern REGEX_BOOK_ID = Pattern.compile("http://manhua\\.dmzj\\.com/(.*)");

    private HttpClient http;

    public Dmzj(HttpClient http) {
        this.http = http;
    }

    @Override
    public List<Book> search(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, "utf-8");
        int pageN = searchPageN(http.get(String.format(SEARCH_URL, encodedQuery, 1)).asDocument().join());

        return IntStream.rangeClosed(1, pageN)
                .mapToObj(i -> http.get(String.format(SEARCH_URL, encodedQuery, i)).asDocument()
                        .thenApply(this::searchBooks))
                .collect(Collectors.toList())
                .stream()
                .flatMap(CompletableFuture::join)
                .collect(Collectors.toList());

    }

    private int searchPageN(Document doc) {
        int pageN = 0;
        for (Element a : doc.select(".bottom_page a")) {
            try {
                pageN = Math.max(pageN, Integer.parseInt(a.text()));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return pageN;
    }

    private Stream<Book> searchBooks(Document doc) {
        return doc.select("li > a")
                .stream()
                .map(a -> {
                    String url = a.attr("href");
                    Matcher matcher = REGEX_BOOK_ID.matcher(url);
                    if (matcher.find()) {
                        return new Book(Dmzj.this, matcher.group(1), a.attr("title"));
                    } else {
                        return null;
                    }
                })
                .filter(b -> b != null);
    }

    @Override
    public List<Chapter> chapters(String bookId) throws IOException {
        Document doc = http.get(String.format(BOOK_URL, bookId)).asDocument().join();
        return doc.select(".cartoon_online_border a,.cartoon_online_border_other a")
                .stream()
                .map(a -> {
                    String id = StringUtils.substringAfterLast(a.attr("href"), "/");
                    id = StringUtils.substringBefore(id, ".shtml");
                    return new Chapter(id, a.text());
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Page> pages(String bookId, String chapterId) throws IOException {
        String html = http.get(String.format(CHAPTER_URL, bookId, chapterId)).asString().join();
        Matcher matcher = REGEX_EVAL.matcher(html);
        matcher.find();
        String eval = matcher.group();

        List<Page> pages = new ArrayList<>();
        Context context = Context.enter();
        try {
            Scriptable scope = context.initSafeStandardObjects();
            String source = eval + PAGE_EXTRACTION;
            NativeArray urlArr = (NativeArray) context.evaluateString(scope, source, "<Dmzj>", 0, null);
            for (Object o : urlArr) {
                pages.add(new Page(String.format(PAGE_URL, o), REFERER));
            }
        } finally {
            Context.exit();
        }
        return pages;
    }
}
