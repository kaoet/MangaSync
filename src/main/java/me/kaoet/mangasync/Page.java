package me.kaoet.mangasync;

public class Page {
    public String url;
    public String referer;

    public Page(String url, String referer) {
        this.url = url;
        this.referer = referer;
    }

    @Override
    public String toString() {
        return url;
    }
}
