package com.github.webee.promise;

/**
 * Created by webee on 16/11/18.
 */

public interface Transition<T> {
    void fulfill(T t);
    void fulfill(Promise<T> p);
    void reject(Throwable t);
    void update(Object o);
}
