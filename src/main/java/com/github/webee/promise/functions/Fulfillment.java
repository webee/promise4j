package com.github.webee.promise.functions;

import com.github.webee.promise.Transition;

/**
 * Created by webee on 16/11/18.
 */

public interface Fulfillment<T> {
    void run(Transition<T> transition);
}
