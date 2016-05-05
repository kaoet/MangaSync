package me.kaoet.mangasync;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ChuiXue implements MangaSource {
    private static final String SEARCH_URL = "http://www.chuixue.com/search.asp?key=%s&page=%d";
    private static final String BOOK_URL = "http://www.chuixue.com/manhua/%s/";
    private static final String CHAPTER_URL = "http://www.chuixue.com/manhua/%s/%s.html";
    private static final Pattern REGEX_URL_SPLIT = Pattern.compile("\\$qingtiandy\\$");
    private static final Charset GBK = Charset.forName("gbk");

    private HttpClient http;

    public ChuiXue(HttpClient http) {
        this.http = http;
    }

    @Override
    public List<Book> search(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, "gbk");
        int pageN = searchPageN(http.get(String.format(SEARCH_URL, encodedQuery, 1)).asDocument(GBK).join());

        return IntStream.rangeClosed(1, pageN)
                .mapToObj(i -> http.get(String.format(SEARCH_URL, encodedQuery, i)).asDocument(GBK)
                        .thenApply(this::searchBooks))
                .collect(Collectors.toList())
                .stream()
                .flatMap(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private int searchPageN(Document doc) {
        if (!doc.select(".noresult").isEmpty()) {
            return 0;
        }
        Elements last = doc.select("a.last");
        if (last.isEmpty()) {
            return 1;
        }
        return Integer.parseInt(StringUtils.substringBetween(last.attr("href"), "page=", "&"));
    }

    private Stream<Book> searchBooks(Document doc) {
        return doc.select("dt > a").stream().map(a -> {
            String id = StringUtils.substringBetween(a.attr("href"), "/manhua/", "/");
            return new Book(ChuiXue.this, id, a.text());
        });
    }

    @Override
    public List<Chapter> chapters(String bookId) throws IOException {
        Document doc = http.get(String.format(BOOK_URL, bookId)).asDocument(GBK).join();
        List<Chapter> chapters = doc.select("#play_0 a")
                .stream()
                .map(a -> {
                    String id = StringUtils.substringAfterLast(a.attr("href"), "/");
                    id = StringUtils.substringBefore(id, ".html");
                    return new Chapter(id, a.attr("title"));
                })
                .collect(Collectors.toList());
        Collections.reverse(chapters);
        return chapters;
    }

    @Override
    public List<Page> pages(String bookId, String chapterId) throws IOException {
        String html = http.get(String.format(CHAPTER_URL, bookId, chapterId)).asString(GBK).join();
        String base64 = StringUtils.substringBetween(html, "var qTcms_S_m_murl_e=\"", "\"");
        String urls = new String(Base64.getDecoder().decode(base64));
        return REGEX_URL_SPLIT.splitAsStream(urls)
                .map(url -> new Page(url, null))
                .collect(Collectors.toList());
    }
}
