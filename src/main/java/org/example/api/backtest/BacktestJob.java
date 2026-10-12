package org.example.api.backtest;

import org.example.backtest.EventBacktestResult;

/**
 * One backtest running in the background, as the user interface sees it.
 *
 * <p>The backtest thread writes the fields and request threads read them, so they are volatile.
 */
public class BacktestJob {

    /** Where a job is in its life. */
    public enum Status {
        /** Waiting for a free worker. */
        QUEUED,
        /** Replaying the market. */
        RUNNING,
        /** Finished; the result is ready. */
        DONE,
        /** Stopped because of an error; see the error message. */
        FAILED
    }

    private final String id;
    private volatile Status status = Status.QUEUED;
    private volatile double progress;
    private volatile EventBacktestResult result;
    private volatile String error;

    /**
     * Creates a queued job.
     *
     * @param id the identifier the user interface asks about
     */
    public BacktestJob(String id) {
        this.id = id;
    }

    /** Returns the identifier of the job. */
    public String getId() {
        return id;
    }

    /** Returns where the job is in its life. */
    public Status getStatus() {
        return status;
    }

    /** Returns the finished part of the backtest, from 0 to 1. */
    public double getProgress() {
        return progress;
    }

    /** Returns the outcome, or null while the job is not done. */
    public EventBacktestResult getResult() {
        return result;
    }

    /** Returns what went wrong, or null if nothing did. */
    public String getError() {
        return error;
    }

    void started() {
        status = Status.RUNNING;
    }

    void progressed(double finished) {
        progress = finished;
    }

    void finished(EventBacktestResult outcome) {
        result = outcome;
        progress = 1;
        status = Status.DONE;
    }

    void failed(String message) {
        error = message;
        status = Status.FAILED;
    }
}
