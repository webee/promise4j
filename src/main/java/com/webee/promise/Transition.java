package com.webee.promise;

/**
 * Created by webee on 16/11/18.
 */

public interface Transition<T> {
    void fulfill(T v);
    void fulfill(Promise<T> p);
    void reject(Throwable r);
    void update(Object s);
}
