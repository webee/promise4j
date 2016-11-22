package com.webee.promise.functions;

import com.webee.promise.Transition;

/**
 * Created by webee on 16/11/18.
 */

public interface Fulfillment<T> {
    void run(Transition<T> transition);
}
