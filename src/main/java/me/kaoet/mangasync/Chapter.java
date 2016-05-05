package me.kaoet.mangasync;

public class Chapter {
    public String id;
    public String name;

    public Chapter(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name + "[" + id + "]";
    }
}
