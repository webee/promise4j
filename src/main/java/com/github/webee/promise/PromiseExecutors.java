package com.github.webee.promise;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by webee on 16/11/19.
 */

public class PromiseExecutors {
    private static final AtomicReference<PromiseExecutors> INSTANCE = new AtomicReference<>();

    private final Executor defaultExecutor;
    private final Executor syncExecutor;

    private static PromiseExecutors getInstance() {
        for (;;) {
            PromiseExecutors current = INSTANCE.get();
            if (current != null) {
                return current;
            }
            current = new PromiseExecutors();
            if (INSTANCE.compareAndSet(null, current)) {
                return current;
            }
        }
    }

    private PromiseExecutors() {
        defaultExecutor = Executors.newCachedThreadPool();
        syncExecutor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    public static Executor defaultExecutor() {
        return getInstance().defaultExecutor;
    }

    public static Executor syncExecutor() {
        return getInstance().syncExecutor;
    }
}
