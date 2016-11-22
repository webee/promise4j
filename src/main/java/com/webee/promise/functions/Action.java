package com.webee.promise.functions;

/**
 * Created by webee on 16/11/18.
 */

public interface Action<T> {
    void run(T v);
}
