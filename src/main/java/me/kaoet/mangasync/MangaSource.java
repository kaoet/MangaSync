package me.kaoet.mangasync;

import java.io.IOException;
import java.util.List;

public interface MangaSource {
    List<Book> search(String query) throws IOException;

    List<Chapter> chapters(String bookId) throws IOException;

    List<Page> pages(String bookId, String chapterId) throws IOException;
}
