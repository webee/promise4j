package com.webee.promise;

import java.util.concurrent.TimeUnit;

/**
 * Created by webee on 16/11/22.
 */
public class Transforms {
    public static Runnable delay(final long time, final TimeUnit unit) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    unit.sleep(time);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
