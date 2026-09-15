package com.planet.importexport.importapi.support;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

/**
 * Names worker threads {@code import-worker-N} and logs any uncaught
 * exception rather than letting it terminate the thread silently.
 */
final class ImportThreadFactory implements ThreadFactory {
    private static final Logger LOG = Logger.getLogger(ImportThreadFactory.class);

    /**
     * Source of the increasing suffix in each created thread's name.
     */
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    /**
     * Creates a new named, non-daemon worker thread for the given task.
     *
     * @param runnable the task the new thread will run
     *
     * @return a new, non-daemon, named worker thread
     */
    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread =
                new Thread(runnable,
                           "import-worker-" +
                           threadNumber.getAndIncrement());
        thread.setDaemon(false);

        thread.setUncaughtExceptionHandler((t, e) ->
                LOG.errorf(e,
                           "Uncaught exception in import worker thread %s",
                           t.getName()));

        return thread;
    }
}
