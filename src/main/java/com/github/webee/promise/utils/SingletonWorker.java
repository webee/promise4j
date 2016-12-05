package com.github.webee.promise.utils;

import com.github.webee.promise.Promise;
import com.github.webee.promise.PromiseExecutors;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
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

    public synchronized void start() {
        if (init.get()) {
            if (status.compareAndSet(INIT, RUNNING)) {
                PromiseExecutors.defaultExcutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        do {
                            try {
                                work.run().await();
                            } catch (Throwable throwable) {
                                throwable.printStackTrace();
                            }
                        } while (status.compareAndSet(RUNNING_WITH_PENDING, RUNNING));
                        if (!status.compareAndSet(RUNNING, INIT)) {
                            status.set(INIT);
                            start();
                        }
                    }
                });
            } else {
                status.compareAndSet(RUNNING, RUNNING_WITH_PENDING);
            }
        } else {
            pending.set(true);
        }
    }

    public interface Work {
        Promise run();
    }
}
