package com.github.webee.promise;

/**
 * Created by webee on 16/11/22.
 */

public class AwaitTimeoutException extends RuntimeException {
    public AwaitTimeoutException(Throwable cause) {
        super("await timeout", cause);
    }
}
