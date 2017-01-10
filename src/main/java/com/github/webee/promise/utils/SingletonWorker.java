package com.github.webee.promise.utils;

import com.github.webee.promise.Promise;
import com.github.webee.promise.PromiseExecutors;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A singleton worker is a worker that can be triggered by call .start() at
 * any time, if it's not running, it will run, else it's pending to run.
 * for example, it can be use to sync data and ui: data update notify the ui
 * to update async, we can make load data as a singleton worker.
 * Created by webee on 16/12/5.
 */
public class SingletonWorker {
    private final static int INIT = 0;
    private final static int RUNNING = 1;
    private final static int RUNNING_WITH_PENDING = 2;

    private final AtomicBoolean pending = new AtomicBoolean(false);
    private final AtomicBoolean init = new AtomicBoolean(true);
    private final AtomicInteger status = new AtomicInteger(INIT);
    private final Work work;

    public SingletonWorker(Work work) {
        this.work = work;
    }

    public SingletonWorker(Work work, boolean init) {
        this.work = work;
        this.init.set(init);
    }

    public synchronized void init() {
        init.set(true);
        if (pending.compareAndSet(true, false)) {
            start();
        }
    }

    private synchronized boolean tryRun() {
        if (init.get()) {
            if (status.compareAndSet(INIT, RUNNING)) {
                return true;
            }
            status.compareAndSet(RUNNING, RUNNING_WITH_PENDING);
        } else {
            pending.set(true);
        }
        return false;
    }

    private synchronized boolean shouldRun() {
        return status.compareAndSet(RUNNING_WITH_PENDING, RUNNING);
    }

    private synchronized void toStop() {
        if (!status.compareAndSet(RUNNING, INIT)) {
            status.set(INIT);
            start();
        }
    }

    public synchronized void start() {
        if (tryRun()) {
            PromiseExecutors.defaultExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    do {
                        try {
                            work.run().await();
                        } catch (Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    } while (shouldRun());
                    toStop();
                }
            });
        }
    }

    public interface Work {
        Promise run();
    }
}
