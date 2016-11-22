package com.webee.promise;

import java.util.concurrent.TimeUnit;

/**
 * Created by webee on 16/11/22.
 */
public class Transforms {
    public static <T> Transform<T, T> delay(final long time, final TimeUnit unit) {
        return new Transform<T, T>() {
            @Override
            public T run(T t) throws Throwable {
                unit.sleep(time);
                return t;
            }
        };
    }
}
