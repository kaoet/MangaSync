package me.kaoet.mangasync;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            HttpClient http = new HttpClient(executor, new NetworkListener() {
                @Override
                public void requestStart(int id, String method, String url) {

                }

                @Override
                public void progress(int id, String status) {

                }

                @Override
                public void requestFinish(int id) {

                }
            });
            MangaSource source = new ChuiXue(http);
            List<Book> books = source.search("我老婆是学生");
            System.out.println(books);
//            String bookId = books.get(0).id;
//            List<Chapter> chapters = source.chapters(bookId);
//            System.out.println(chapters);
//            System.out.println(source.pages(null, "226602"));
        } finally {
            executor.shutdown();
        }
    }
}
