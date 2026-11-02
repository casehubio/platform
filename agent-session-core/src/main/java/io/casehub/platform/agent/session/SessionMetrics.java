package io.casehub.platform.agent.session;

import java.util.concurrent.atomic.AtomicInteger;

public class SessionMetrics {

    private final String name;
    private final AtomicInteger sessionsOpened = new AtomicInteger();
    private final AtomicInteger sessionsClosed = new AtomicInteger();
    private final AtomicInteger peakConcurrent = new AtomicInteger();
    private final AtomicInteger currentActive = new AtomicInteger();
    private final AtomicInteger totalQueries = new AtomicInteger();
    private final AtomicInteger totalClears = new AtomicInteger();
    private final AtomicInteger lightweightClears = new AtomicInteger();
    private final AtomicInteger fallbackClears = new AtomicInteger();


    public SessionMetrics(String name) {
        this.name = name;
    }

    public void sessionOpened() {
        sessionsOpened.incrementAndGet();
        int active = currentActive.incrementAndGet();
        peakConcurrent.updateAndGet(peak -> Math.max(peak, active));
    }

    public void sessionClosed() {
        sessionsClosed.incrementAndGet();
        currentActive.decrementAndGet();
    }

    public void queryStarted() {
        totalQueries.incrementAndGet();
    }

    public void clearTriggered() {
        totalClears.incrementAndGet();
    }

    public void lightweightClearSucceeded() {
        lightweightClears.incrementAndGet();
        totalClears.incrementAndGet();
    }

    public void fallbackClearTriggered() {
        fallbackClears.incrementAndGet();
        totalClears.incrementAndGet();
    }

    public int totalLightweightClears() {return lightweightClears.get();}

    public int totalFallbackClears()    {return fallbackClears.get();}


    public String name() { return name; }
    public int sessionsOpened() { return sessionsOpened.get(); }
    public int sessionsClosed() { return sessionsClosed.get(); }
    public int peakConcurrent() { return peakConcurrent.get(); }
    public int currentActive() { return currentActive.get(); }
    public int totalQueries() { return totalQueries.get(); }
    public int totalClears() { return totalClears.get(); }

    public String summary() {
        return "sessions=" + sessionsOpened.get()
               + " peakConcurrent=" + peakConcurrent.get()
               + " queries=" + totalQueries.get()
               + " clears=" + totalClears.get()
               + " lightweightClears=" + lightweightClears.get()
               + " fallbackClears=" + fallbackClears.get();
    }
}
