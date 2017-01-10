package com.github.webee.promise;

import com.github.webee.promise.functions.Action;
import com.github.webee.promise.functions.Fulfillment;
import com.github.webee.promise.functions.ThenFulfillment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by webee on 16/11/17.
 */

public class Promise<T> {
    enum State {
        PENDING, FULFILLED, REJECTED
    }

    static class NullStatus {
        static NullStatus instance = new NullStatus();
        private NullStatus() {
        }

        @Override
        public String toString() {
            return "NullStatus{}";
        }
    }

    // promise state.
    private State state = State.PENDING;
    private boolean isWaiting = false;

    // promise data.
    private T value;
    private Object status = NullStatus.instance;
    private Throwable reason;

    // current handler/listener executor.
    private Executor executor;
    // current transformer executor.
    private Executor transformExecutor;
    // settled handler callbacks.
    private ConcurrentLinkedQueue<ExecutableRunnable> handlers = new ConcurrentLinkedQueue<>();
    // status listener callbacks.
    private ConcurrentLinkedQueue<ExecutableRunnable> listeners = new ConcurrentLinkedQueue<>();

    /**
     * 通过实现构造一个Promise
     *
     * @param s       初始状态
     * @param fulfill 实现
     */
    public Promise(Object s, Fulfillment<T> fulfill) {
        status = s;

        try {
            fulfill.run(new Transition<T>() {
                public void fulfill(T v) {
                    Promise.this.fulfill(v);
                }

                public void fulfill(Promise<T> p) {
                    Promise.this.fulfill(p);
                }

                public void reject(Throwable r) {
                    Promise.this._reject(r);
                }

                public void update(Object s) {
                    Promise.this.update(s);
                }
            });
        } catch (Throwable r) {
            _reject(r);
        }
    }

    public Promise(Fulfillment<T> fulfill) {
        this(NullStatus.instance, fulfill);
    }

    private void settled() {
        for (ExecutableRunnable h : handlers) {
            h.execute();
        }

        handlers.clear();
        listeners.clear();
    }

    private void updated() {
        for (ExecutableRunnable l : listeners) {
            l.execute();
        }
    }

    private synchronized <V> void listen(final Action<V> action, Executor executor) {
        executor = executor != null ? executor : PromiseExecutors.defaultExecutor();

        Runnable listener = new Runnable() {
            @Override
            public void run() {
                action.run((V) status);
            }
        };

        if (status != NullStatus.instance) {
            executor.execute(listener);
        }

        if (state == State.PENDING) {
            listeners.add(new ExecutableRunnable(listener, executor));
        }
    }

    private synchronized <V> void listen(Action<V> action) {
        listen(action, PromiseExecutors.defaultExecutor());
    }

    private synchronized void handle(Handler handler, Executor executor) {
        executor = executor != null ? executor : PromiseExecutors.defaultExecutor();

        if (state == State.PENDING) {
            handlers.add(new ExecutableRunnable(handler, executor));
        } else {
            executor.execute(handler);
        }
    }

    private synchronized void _reject(Throwable r) {
        if (state == State.PENDING) {
            reason = r;
            state = State.REJECTED;
            settled();
        }
    }

    /**
     * waiting状态的reject
     */
    private synchronized void _waiting_reject(Throwable r) {
        if (state == State.PENDING && isWaiting) {
            reason = r;
            state = State.REJECTED;
            isWaiting = false;
            settled();
        }
    }

    private synchronized void update(Object s) {
        if (state == State.PENDING) {
            if (s != status) {
                status = s;
                updated();
            }
        }
    }

    private synchronized void fulfill(T v) {
        if (state == State.PENDING) {
            value = v;
            state = State.FULFILLED;
            settled();
        }
    }

    /**
     * waiting状态的fulfill
     */
    private synchronized void waiting_fulfill(T v) {
        if (state == State.PENDING && isWaiting) {
            value = v;
            state = State.FULFILLED;
            isWaiting = false;
            settled();
        }
    }

    private synchronized void fulfill(Promise<T> p) {
        if (state == State.PENDING && !isWaiting) {
            final Promise<T> hp = this;
            // 进入等待状态, 防止其它的fulfill或者_reject
            isWaiting = true;
            try {
                p.status(PromiseExecutors.syncExecutor(), new Action<Object>() {
                    @Override
                    public void run(Object o) {
                        hp.update(o);
                    }
                }).fulfilled(new Action<T>() {
                    public void run(T v) {
                        hp.waiting_fulfill(v);
                    }
                }).rejected(new Action<Throwable>() {
                    public void run(Throwable v) {
                        hp._waiting_reject(v);
                    }
                });
            } catch (Throwable r) {
                _waiting_reject(r);
            }
        }
    }

    /**
     * 阻塞获取值, 或者抛出reason(Throwable), 或者阻塞被中断(InterruptedException)抛出AwaitTimeout
     *
     * @return Promise的值
     * @throws Throwable rejected原因,或者超时(AwaitTimeout)
     */
    private T await(boolean withTimeout, long timeout, TimeUnit unit) throws Throwable {
        if (state == State.PENDING) {
            final CountDownLatch latch = new CountDownLatch(1);
            settled(new Runnable() {
                public void run() {
                    latch.countDown();
                }
            });
            boolean done = true;
            try {
                if (withTimeout) {
                    done = latch.await(timeout, unit);
                } else {
                    latch.await();
                }
            } catch (InterruptedException e) {
                throw new AwaitTimeout(e);
            }
            if (!done) {
                throw new AwaitTimeout(null);
            }
        }
        if (state == State.FULFILLED) {
            return value;
        }
        throw reason;
    }

    public T await(long timeout, TimeUnit unit) throws Throwable {
        return await(true, timeout, unit);
    }

    public T await() throws Throwable {
        return await(false, 0, null);
    }

    /**
     * 取消, 目前仅仅是reject为PromiseCanceledException, TODO: 尝试将执行线程取消
     *
     * @return 是否真的取消了
     */
    public boolean cancel() {
        Throwable r = new PromiseCanceledException();
        _reject(r);
        return r == reason;
    }

    /**
     * 指定处理执行器
     *
     * @param executor 执行器
     * @return 当前Promise
     */
    public Promise<T> handleOn(Executor executor) {
        this.executor = executor;
        return this;
    }

    /**
     * 处理计算状态更新
     *
     * @param executor 执行器
     * @param onUpdate 状态更新回调
     * @param <V>      状态值类型
     * @return 当前Promise
     */
    public <V> Promise<T> status(Executor executor, final Action<V> onUpdate) {
        listen(onUpdate, executor);
        return this;
    }

    public <V> Promise<T> status(Action<V> onUpdate) {
        return status(executor, onUpdate);
    }

    /**
     * 处理计算成功
     *
     * @param executor    执行器
     * @param onFulfilled 成功回调
     * @return 当前Promise
     */
    public Promise<T> fulfilled(Executor executor, final Action<T> onFulfilled) {
        handle(new Handler() {
            @Override
            public void onFulfilled(T v) {
                onFulfilled.run(v);
            }
        }, executor);
        return this;
    }

    public Promise<T> fulfilled(Action<T> onFulfilled) {
        return fulfilled(executor, onFulfilled);
    }


    /**
     * 处理计算失败
     *
     * @param executor   执行器
     * @param onRejected 失败回调
     * @return 当前Promise
     */
    public Promise<T> rejected(Executor executor, final Action<Throwable> onRejected) {
        handle(new Handler() {
            @Override
            void onRejected(Throwable r) {
                onRejected.run(r);
            }
        }, executor);
        return this;
    }

    public Promise<T> rejected(Action<Throwable> onRejected) {
        return rejected(executor, onRejected);
    }

    /**
     * 处理计算结束
     *
     * @param executor  执行器
     * @param onSettled 结束回调
     * @return 当前Promise
     */
    public Promise<T> settled(Executor executor, final Runnable onSettled) {
        handle(new Handler() {
            @Override
            void onSettled() {
                onSettled.run();
            }
        }, executor);
        return this;
    }

    public Promise<T> settled(Runnable onSettled) {
        return settled(executor, onSettled);
    }


    private static <T> void doFulfill(Transition<T> transition, T v) {
        if (v instanceof Promise) {
            transition.fulfill((Promise<T>) v);
        } else {
            transition.fulfill(v);
        }
    }
    /**
     * 指定转换执行器
     *
     * @param executor 执行器
     * @return 当前Promise
     */
    public Promise<T> transformOn(Executor executor) {
        this.transformExecutor = executor;
        return this;
    }

    /**
     * 上一个计算流程的异常处理, 使之成为一个可以进一步处理的Promise
     *
     * @param executor       执行器
     * @param catchTransform 异常变换回调
     * @return 变换后Promise
     */
    public Promise<T> thenCatch(final Executor executor, final CatchTransform<T> catchTransform) {
        return new Promise<>(new Fulfillment<T>() {
            @Override
            public void run(final Transition<T> transition) {
                handle(new Handler() {
                    @Override
                    public void onFulfilled(T v) {
                        doFulfill(transition, v);
                    }

                    @Override
                    public void onRejected(Throwable r) {
                        try {
                            T v = catchTransform.run(r);
                            doFulfill(transition, v);
                        } catch (Throwable e) {
                            transition.reject(e);
                        }
                    }
                }, executor);
            }
        });
    }

    public Promise<T> thenCatch(final CatchTransform<T> catchTransform) {
        return thenCatch(transformExecutor, catchTransform);
    }

    public Promise<T> thenCatch(Executor executor, CatchPromiseTransform<T> catchTransform) {
        return thenCatch(executor, (CatchTransform<T>) catchTransform);
    }

    public Promise<T> thenCatch(CatchPromiseTransform<T> catchTransform) {
        return thenCatch(transformExecutor, catchTransform);
    }

    /**
     * 进入下一个计算流程
     *
     * @param executor        执行器
     * @param s               初始状态
     * @param thenFulfillment 下一步的实现
     * @param <V>             变换目标类型
     * @return 变换后Promise
     */
    public <V> Promise<V> then(final Executor executor, Object s, final ThenFulfillment<T, V>thenFulfillment) {
        return new Promise<>(s, new Fulfillment<V>() {
            @Override
            public void run(final Transition<V> transition) {
                handle(new Handler() {
                    @Override
                    public void onFulfilled(T v) {
                        try {
                            thenFulfillment.run(v, transition);
                        } catch (Throwable e) {
                            transition.reject(e);
                        }
                    }

                    @Override
                    public void onRejected(Throwable r) {
                        transition.reject(r);
                    }
                }, executor);
            }
        });
    }

    public <V> Promise<V> then(Object s, final ThenFulfillment<T, V>thenFulfillment) {
        return then(transformExecutor, s, thenFulfillment);
    }

    public <V> Promise<V> then(final Executor executor, final ThenFulfillment<T, V>thenFulfillment) {
        return then(executor, NullStatus.instance, thenFulfillment);
    }

    public <V> Promise<V> then(final ThenFulfillment<T, V>thenFulfillment) {
        return then(NullStatus.instance, thenFulfillment);
    }

    /**
     * 进入下一个计算流程
     *
     * @param executor  执行器
     * @param transform 变换回调
     * @param <V>       变换目标类型
     * @return 变换后Promise
     */
    public <V> Promise<V> then(final Executor executor, final Transform<T, V> transform) {
        return then(executor, new ThenFulfillment<T, V>() {
            @Override
            public void run(T v, Transition<V> transition) {
                try {
                    doFulfill(transition, transform.run(v));
                } catch (Throwable e) {
                    transition.reject(e);
                }
            }
        });
    }

    public <V> Promise<V> then(final Transform<T, V> transform) {
        return then(transformExecutor, transform);
    }

    /**
     * 使用返回Promise的变换
     *
     * @param executor  执行器
     * @param transform 变换回调
     * @param <V>       变换目标值类型
     * @return 变换后Promise
     */
    public <V> Promise<V> then(Executor executor, final PromiseTransform<T, V> transform) {
        return then(executor, (Transform<T, V>) transform);
    }

    public <V> Promise<V> then(PromiseTransform<T, V> transform) {
        return then(transformExecutor, transform);
    }

    /**
     * 闭包变换, 值类型不变
     *
     * @param executor  执行器
     * @param transform 变换回调
     * @return 变换后Promise
     */
    public Promise<T> then(Executor executor, ClosureTransform<T> transform) {
        return then(executor, (Transform<T, T>) transform);
    }

    public Promise<T> then(ClosureTransform<T> transform) {
        return then(transformExecutor, transform);
    }

    /**
     * 等值变换
     *
     * @param executor 执行器
     * @param action
     * @return
     */
    public Promise<T> then(Executor executor, final Runnable action) {
        return then(executor, new ClosureTransform<T>() {
            @Override
            public T run(T t) throws Throwable {
                action.run();
                return t;
            }
        });
    }

    public Promise<T> then(final Runnable action) {
        return then(transformExecutor, action);
    }

    abstract class Handler implements Runnable {
        void onFulfilled(T v) {
        }

        void onRejected(Throwable r) {
        }

        void onSettled() {
        }

        @Override
        public void run() {
            if (state == State.FULFILLED) {
                onFulfilled(value);
            } else if (state == State.REJECTED) {
                onRejected(reason);
            }
            onSettled();
        }
    }

    private class ExecutableRunnable {
        private Executor executor;
        private Runnable runnable;

        ExecutableRunnable(Runnable runnable, Executor executor) {
            this.executor = executor;
            this.runnable = runnable;
        }

        void execute() {
            executor.execute(runnable);
        }
    }

    /**
     * 生成一个fulfilled值为v的Promise
     *
     * @param v   值
     * @param <V> 值类型
     * @return 生成的Promise
     */
    public static <V> Promise<V> resolve(final V v) {
        return new Promise<>(new Fulfillment<V>() {
            @Override
            public void run(Transition<V> transition) {
                doFulfill(transition, v);
            }
        });
    }

    /**
     * 使用Promise fulfill一个Promise
     *
     * @param p   源Promise
     * @param <V> 值类型
     * @return 生成的Promise
     */
    public static <V> Promise<V> resolve(Promise<V> p) {
        return resolve((V) p);
    }

    /**
     * 生成一上rejected reason为r的Promise
     *
     * @param r   reason
     * @param <V> 值类型
     * @return 生成的Promise
     */
    public static <V> Promise<V> reject(final Throwable r) {
        return new Promise<>(new Fulfillment<V>() {
            @Override
            public void run(Transition<V> transition) {
                transition.reject(r);
            }
        });
    }

    /**
     * 利用所有Promise的值生成一个集合值类型的Promise
     *
     * @param promises 源Promises
     * @param <T>      值类型
     * @return 生成的Promise
     */
    public static <T> Promise<Iterable<T>> all(final Iterable<Promise<T>> promises) {
        return new Promise<>(new Fulfillment<Iterable<T>>() {
            @Override
            public void run(final Transition<Iterable<T>> transition) {
                PromiseExecutors.defaultExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        int count = 0;
                        for (Promise<T> p : promises) {
                            count++;
                        }

                        final CountDownLatch latch = new CountDownLatch(count);
                        final List<T> res = new ArrayList<>(count);
                        int idx = 0;
                        for (Promise<T> promise : promises) {
                            final int index = idx++;
                            res.add(null);
                            promise.fulfilled(new Action<T>() {
                                @Override
                                public void run(T v) {
                                    res.set(index, v);
                                    latch.countDown();
                                }
                            }).rejected(new Action<Throwable>() {
                                @Override
                                public void run(Throwable r) {
                                    transition.reject(r);
                                    latch.countDown();
                                }
                            });
                        }

                        try {
                            latch.await();
                            transition.fulfill(res);
                        } catch (InterruptedException e) {
                            transition.reject(e);
                        }
                    }
                });
            }
        });
    }

    public static <T> Promise<Iterable<T>> all(Promise<T>... promises) {
        return all(Arrays.asList(promises));
    }

    /**
     * 以最快结束的Promise fulfill一个Promise
     *
     * @param promises 源Promises
     * @param <T>      值类型
     * @return 生成的Promise
     */
    public static <T> Promise<T> race(final Iterable<Promise<T>> promises) {
        return new Promise<>(new Fulfillment<T>() {
            @Override
            public void run(final Transition<T> transition) {
                for (Promise<T> promise : promises) {
                    promise.fulfilled(new Action<T>() {
                        @Override
                        public void run(T v) {
                            transition.fulfill(v);
                        }
                    }).rejected(new Action<Throwable>() {
                        @Override
                        public void run(Throwable r) {
                            transition.reject(r);
                        }
                    });
                }
            }
        });
    }

    public static <T> Promise<T> race(Promise<T>... promises) {
        return race(Arrays.asList(promises));
    }
}
