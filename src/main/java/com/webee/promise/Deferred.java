package com.webee.promise;

import com.webee.promise.functions.Fulfillment;

/**
 * Created by webee on 16/11/19.
 */

public class Deferred<T> implements Transition<T> {
    public final Promise<T> promise;
    private Transition<T> transition;

    public Deferred() {
        this(null);
    }

    public Deferred(Object s) {
        promise = new Promise<>(s, new Fulfillment<T>() {
            @Override
            public void run(Transition<T> transition) {
                Deferred.this.transition = transition;
            }
        });
    }

    @Override
    public void fulfill(T v) {
        transition.fulfill(v);
    }

    @Override
    public void fulfill(Promise<T> p) {
        transition.fulfill(p);
    }

    @Override
    public void reject(Throwable r) {
        transition.reject(r);
    }

    @Override
    public void update(Object s) {
        transition.update(s);
    }
}
