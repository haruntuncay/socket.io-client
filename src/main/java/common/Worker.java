package common;

import exceptions.SocketIOException;
import java.util.concurrent.*;

/**
 * Exposes static methods that are used to execute background jobs and scheduled jobs.
 * This class delegates the given jobs to two underlying {@link ExecutorService}s.
 * One is a single threaded {@link ExecutorService} that is meant for the background jobs
 *  like encoding outgoing messages, parsing incoming ones and running poll/send jobs.
 * Other is a single threaded {@link ScheduledExecutorService} that is meant to execute
 *  scheduled jobs like ping/pong messages and connection retries.
 */
public class Worker {

    private static ExecutorService executor;
    private static ScheduledExecutorService scheduler;

    /*
        Init executor if necessary.
        This method will be called right before the executor is needed.
        This helps to avoid having an executor that is open even when it's not needed yet.
     */
    private static void initExecutor() {
        if(executor == null)
            executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Worker-Executor"));
    }

    /*
        Init scheduler if necessary.
        This method will be called right before the scheduler is needed.
        This helps to avoid having a scheduler that is open even when it's not needed yet.
     */
    private static void initScheduler() {
        if(scheduler == null)
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Worker-Scheduler"));
    }

    /**
     * Submit a {@link Runnable} to be executed by a single threaded {@code ExecutorService}.
     */
    public static void execute(Runnable runnable) {
        initExecutor();
        if(!executor.isShutdown())
            executor.execute(runnable);
    }

    /**
     * Submit a {@link Runnable} to be executed by a single threaded {@code ExecutorService} and get back a {@link Future}
     *  object that can be used to cancel it.
     *
     * @param runnable Runnable instance that represents the job to run.
     * @return Future instance.
     */
    public static Future<?> submit(Runnable runnable) {
        initExecutor();
        return executor.isShutdown() ? null : executor.submit(runnable);
    }

    /**
     * Submit a {@link Callable} to be executed by a single threaded {@code ExecutorService} and get back a {@link Future}
     *  that can be used to get the result of the operation or to cancel the operation.
     *
     * @param callable Callable instance that represents the job to run.
     * @param <T> Type of Callable's return value.
     * @return Result of Callable operation.
     */
    public static <T> Future<T> submit(Callable<T> callable) {
        initExecutor();
        return executor.isShutdown() ? null : executor.submit(callable);
    }

    /**
     * Schedule a Runnable that will be run after {@code delay} milliseconds.
     *
     * @param runnable The Runnable to run after delay.
     * @param delay Amount of time, in milliseconds, to wait before executing the Runnable.
     * @return ScheduledFuture instance that can be used to cancel the job.
     */
    public static ScheduledFuture<?> schedule(Runnable runnable, long delay) {
        initScheduler();
        return scheduler.isShutdown() ? null : scheduler.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }

    /**
     *  Shuts down the executor and scheduler threads, and waits 1 second for them to terminate.
     *  <p>
     *  <b>Note:</b> Call this method only when you are completely done with the Socket.IO Client API as a way to clean up
     *               any worker threads that might keep the JVM from shutting down.
     */
    public static void shutdown() {
       shutdown(1000);
    }

    /**
     *  Shuts down the executor and scheduler threads, and waits for the given amount in millis for them to terminate.
     *  <p>
     *  <b>Note:</b> Call this method only when you are completely done with the Socket.IO Client API as a way to clean up
     *               any worker threads that might keep the JVM from shutting down.
     */
    public static void shutdown(int timeOutInMillis) {
        // todo, maybe figure out a better way to close ?
        try {
            if(executor != null) {
                executor.shutdown();
                executor.awaitTermination(timeOutInMillis, TimeUnit.MILLISECONDS);
            }

            if(scheduler != null) {
                scheduler.shutdown();
                scheduler.awaitTermination(timeOutInMillis, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            throw new SocketIOException("Interrupted while closing worker threads.", e);
        }
    }
}
