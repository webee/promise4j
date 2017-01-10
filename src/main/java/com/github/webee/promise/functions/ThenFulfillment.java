package com.github.webee.promise.functions;

import com.github.webee.promise.Transition;

/**
 * Created by webee on 16/11/18.
 */

public interface ThenFulfillment<S, T> {
    void run(S val, Transition<T> transition);
}
