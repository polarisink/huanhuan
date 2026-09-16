package cn.huanhuan.core;

@FunctionalInterface
public interface ProgressListener {
    void update(double progress, String message);
    ProgressListener NONE = (progress, message) -> {};
}
