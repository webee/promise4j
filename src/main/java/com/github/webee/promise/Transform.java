package com.github.webee.promise;

/**
 * Created by webee on 16/11/17.
 */

public interface Transform<T, V> {
    V run(T t) throws Throwable;
}
