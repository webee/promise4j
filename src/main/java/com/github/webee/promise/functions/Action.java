package com.github.webee.promise.functions;

/**
 * Created by webee on 16/11/18.
 */

public interface Action<T> {
    void run(T t);
}
