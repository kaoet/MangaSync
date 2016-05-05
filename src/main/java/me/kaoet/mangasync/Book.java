package me.kaoet.mangasync;

public class Book {
    public MangaSource mangaSource;
    public String id;
    public String name;

    public Book(MangaSource mangaSource, String id, String name) {
        this.mangaSource = mangaSource;
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name + "[" + id + "]";
    }
}
