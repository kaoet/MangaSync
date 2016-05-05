package me.kaoet.mangasync;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class KuKuDm implements MangaSource {

    private static final String SEARCH_URL = "http://so.kukudm.com/search.asp?kw=%s&page=%d";
    private static final String BOOK_URL = "http://www.kukudm.com/comiclist/%s/index.htm";
    private static final String CHAPTER_URL = "http://www.kukudm.com/comiclist/%s/%s/%d.htm";
    private static final String PAGE_ROOT = "http://n.kukudm.com/";
    private static final Pattern REGEX_PAGE_URL = Pattern.compile("document\\.write\\(\"<img src='\"\\+[a-z0-9]+\\+\"(.*?)'", Pattern.CASE_INSENSITIVE);
    private static final Charset GBK = Charset.forName("gbk");

    private HttpClient http;

    public KuKuDm(HttpClient http) {
        this.http = http;
    }

    @Override
    public List<Book> search(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, "gbk");
        int pageN = searchPageN(http.get(String.format(SEARCH_URL, encodedQuery, 1)).asString(GBK).join());

        return IntStream.rangeClosed(1, pageN)
                .mapToObj(i -> http.get(String.format(SEARCH_URL, encodedQuery, i)).asDocument(GBK)
                        .thenApply(this::searchBooks))
                .collect(Collectors.toList())
                .stream()
                .flatMap(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private int searchPageN(String html) {
        String pageS = StringUtils.substringBetween(html, "分 ", " 页");
        return Integer.parseInt(pageS);
    }

    private Stream<Book> searchBooks(Document doc) {
        return doc.select("#comicmain dd a:last-child")
                .stream()
                .map(a -> {
                    String id = StringUtils.substringBetween(a.attr("href"), "/comiclist/", "/");
                    String name = StringUtils.substringBefore(a.text(), "漫画");
                    return new Book(KuKuDm.this, id, name);
                });
    }

    @Override
    public List<Chapter> chapters(String bookId) throws IOException {
        Document doc = http.get(String.format(BOOK_URL, bookId)).asDocument(GBK).join();
        String bookName = StringUtils.substringBefore(doc.select("title").text(), "漫画");
        return doc.select("#comiclistn dd a:first-child")
                .stream()
                .map(a -> {
                    String id = a.attr("href").split("/")[3];
                    String name = StringUtils.removeStart(a.text(), bookName).trim();
                    return new Chapter(id, name);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Page> pages(String bookId, String chapterId) throws IOException {
        String html = http.get(String.format(CHAPTER_URL, bookId, chapterId, 1)).asString(GBK).join();
        int pageN = Integer.parseInt(StringUtils.substringBetween(html, "共", "页"));

        return IntStream.rangeClosed(1, pageN)
                .mapToObj(i ->
                        http.get(String.format(CHAPTER_URL, bookId, chapterId, i)).asString(GBK)
                                .thenApply(this::getPage))
                .collect(Collectors.toList())
                .stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private Page getPage(String html) {
        Matcher matcher = REGEX_PAGE_URL.matcher(html);
        matcher.find();
        return new Page(PAGE_ROOT + matcher.group(1), null);
    }
}
