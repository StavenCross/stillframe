package dev.stillframe;

/** Choose the first image for this visit to a card and keep its source stable. */
final class PosterPresentation {
    private static final long GRACE_MS = 240;
    enum Source { WAITING, PORTRAIT, PUBLISHER }

    private Source source = Source.WAITING;
    private long deadline;

    void reset(long now) {
        source = Source.WAITING;
        deadline = now + GRACE_MS;
    }

    Source select(long now, boolean portraitReady, boolean publisherReady, boolean pending) {
        if (source != Source.WAITING) return source;
        if (portraitReady) source = Source.PORTRAIT;
        else if (publisherReady && (!pending || now >= deadline)) source = Source.PUBLISHER;
        return source;
    }

    long remaining(long now) { return Math.max(0, deadline - now); }
}
