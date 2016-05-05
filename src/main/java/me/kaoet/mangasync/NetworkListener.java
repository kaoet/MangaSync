package me.kaoet.mangasync;

public interface NetworkListener {
    void requestStart(int id, String method, String url);
    void progress(int id, String status);
    void requestFinish(int id);
}
