package net.yizuz.playerogrammod.ai;

@FunctionalInterface
public interface DownloadProgress {
    DownloadProgress NONE = (done, total) -> {
    };

    void update(long done, long total);
}
