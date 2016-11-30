package com.github.webee.promise;

/**
 * Created by webee on 16/11/22.
 */

public class PromiseCanceledException extends RuntimeException {
    public PromiseCanceledException() {
        super("Promise has been canceled");
    }
}
