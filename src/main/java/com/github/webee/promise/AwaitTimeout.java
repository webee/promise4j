package com.github.webee.promise;

/**
 * Created by webee on 16/11/22.
 */

public class AwaitTimeout extends RuntimeException {
    public AwaitTimeout(Throwable cause) {
        super(cause);
    }
}
