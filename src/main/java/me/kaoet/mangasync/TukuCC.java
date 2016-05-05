package me.kaoet.mangasync;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TukuCC implements MangaSource {
    private static final String SEARCH_URL = "http://www.tuku.cc/comic/search?w=%s&page=%d";
    private static final String BOOK_URL = "http://www.tuku.cc/comic/%s/";
    private static final String CHAPTER_URL = "http://www.tuku.cc/comic/%s/%s";
    private static final Pattern REGEX_EVAL = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\)\\{.*");
    private static final String SERVER_URL = "var serverUrl='http://tkpic.tukucc.com';";
    private static final String PAGE_EXTRACTION = ";var res=[];for(var fyj=1;fyj<=pages;fyj++)res.push(getImgUrl(fyj,0));res";

    private HttpClient http;

    public TukuCC(HttpClient http) {
        this.http = http;
    }

    @Override
    public List<Book> search(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, "utf-8");
        Document doc = http.get(String.format(SEARCH_URL, encodedQuery, 1)).asDocument().join();
        int pageN = Integer.parseInt(StringUtils.substringBetween(doc.select(".desc").first().text(), "，共", "页"));

        return IntStream.rangeClosed(1, pageN)
                .mapToObj(i -> http.get(String.format(SEARCH_URL, encodedQuery, i)).asDocument()
                        .thenApply(this::searchBooks))
                .collect(Collectors.toList())
                .stream()
                .flatMap(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private Stream<Book> searchBooks(Document doc) {
        return doc.select(".searchcontent .ttl a").stream().map(a -> {
            String id = StringUtils.substringBetween(a.attr("href"), "/comic/", "/");
            return new Book(TukuCC.this, id, a.text());
        });
    }

    @Override
    public List<Chapter> chapters(String bookId) throws IOException {
        Document doc = http.get(String.format(BOOK_URL, bookId)).asDocument().join();
        List<Chapter> chapters = doc.select("#charpter_content li").stream().map(li -> {
            Element a = li.select("a").first();
            return new Chapter(a.attr("href").split("/")[3], a.text().trim());
        }).collect(Collectors.toList());
        Collections.reverse(chapters);
        return chapters;
    }

    @Override
    public List<Page> pages(String bookId, String chapterId) throws IOException {
        Document doc = http.get(String.format(CHAPTER_URL, bookId, chapterId)).asDocument().join();
        String html = doc.toString();
        Matcher matcher = REGEX_EVAL.matcher(html);
        matcher.find();
        String eval = matcher.group();

        List<Page> pages = new ArrayList<>();
        Context context = Context.enter();
        try {
            Scriptable scope = context.initSafeStandardObjects();
            String source = SERVER_URL + eval + PAGE_EXTRACTION;
            NativeArray urlArr = (NativeArray) context.evaluateString(scope, source, "<TukuCC>", 0, null);
            for (Object o : urlArr) {
                pages.add(new Page(o.toString(), null));
            }
        } finally {
            Context.exit();
        }
        return pages;
    }
}
