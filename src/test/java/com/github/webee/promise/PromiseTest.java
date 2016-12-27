package com.github.webee.promise;

import com.github.webee.promise.functions.Action;
import com.github.webee.promise.functions.Fulfillment;
import org.junit.Test;
import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * Created by webee on 16/11/22.
 */
public class PromiseTest {
    @Test
    public void a() throws Throwable {
        System.out.println("start: " + System.currentTimeMillis());

        final Promise<String> p0 = new Promise<>("init", new Fulfillment<String>() {
            @Override
            public void run(final Transition<String> transition) {
                System.out.println("p0 inner promise");
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000);
                            transition.update("A");
                            Thread.sleep(1000);
                            transition.update("B");
                            Thread.sleep(1000);
                            transition.update("C");
                            Thread.sleep(1000);
                            transition.update("D");
                            Thread.sleep(1000);
                            transition.update("E");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        transition.fulfill("webee.yw");
                    }
                });
            }
        });
        p0.status(new Action<String>() {
            @Override
            public void run(String v) {
                System.out.println(v);
            }
        });
        Promise<String> p1 = new Promise<>(new Fulfillment<String>() {
            @Override
            public void run(Transition<String> transition) {
                System.out.println("p1 inner promise");
                transition.fulfill(p0.then(new Transform<String, String>() {
                    @Override
                    public String run(String v) {
                        return "#" + v + "#";
                    }
                }));
            }
        });
        p1.fulfilled(new Action<String>() {
            @Override
            public void run(String v) {
                System.out.println(v);
            }
        });
        System.out.println("outer promise");

        p1.await();

        System.out.println("  end: " + System.currentTimeMillis());
    }

    @Test
    public void testCatch() throws Throwable {
        Promise<Integer> p0 = Promise.reject(new Exception("xxx"));
        Promise<Integer> p1 = p0.thenCatch(new CatchTransform<Integer>() {
            @Override
            public Integer run(Throwable throwable) throws Throwable {
                if (throwable.getMessage().equals("xxx")) {
                    return 123;
                }
                throw throwable;
            }
        });
        System.out.println("p1: " + p1.await());
    }

    @Test
    public void deferred() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Deferred<String> deferred = new Deferred<>("init");
        System.out.println("p0 inner promise");
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    deferred.update("A");
                    Thread.sleep(1000);
                    deferred.update("B");
                    Thread.sleep(1000);
                    deferred.update("C");
                    Thread.sleep(1000);
                    deferred.update("D");
                    Thread.sleep(1000);
                    deferred.update("E");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                deferred.fulfill("webee.yw");
            }
        });
        final Promise<String> p0 = deferred.promise;
        p0.status(new Action<String>() {
            @Override
            public void run(String v) {
                System.out.println(v);
            }
        });
        Promise<String> p1 = new Promise<>(new Fulfillment<String>() {
            @Override
            public void run(Transition<String> transition) {
                System.out.println("p1 inner promise");
                transition.fulfill(p0.then(new Transform<String, String>() {
                    @Override
                    public String run(String v) {
                        return "#" + v + "#";
                    }
                }));
            }
        });
        p1.then(new Transform<String, Void>() {
            @Override
            public Void run(String v) {
                System.out.println(v);
                return null;
            }
        }).fulfilled(new Action<Void>() {
            @Override
            public void run(Void v) {
                latch.countDown();
            }
        });
        System.out.println("outer promise");
        latch.await();
    }

    @Test
    public void resolve() throws Throwable {
        Promise<String> p = Promise.resolve(Promise.resolve("abc"));
        System.out.println(p.await());
    }

    @Test
    public void all() {
        System.out.println("start: " + System.currentTimeMillis());
//        Promise<String> r = Promise.reject(new Exception("XXX"));
        Promise<String> r = Promise.resolve("xxx");
        Promise<Iterable<String>> p = Promise.all(Promise.resolve("a"), Promise.resolve("b"),
                new Promise<String>(new Fulfillment<String>() {
                    @Override
                    public void run(final Transition<String> transition) {
                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                transition.fulfill("c");
                            }
                        });
                    }
                }), r);
        try {
            System.out.println(p.await());
            System.out.println("end: " + System.currentTimeMillis());
        } catch (Throwable e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("err: " + System.currentTimeMillis());
        }
    }

    @Test
    public void race() {
        System.out.println("start: " + System.currentTimeMillis());
        Promise<String> r = Promise.reject(new Exception("XXX"));
        Promise<String> p = Promise.race(Promise.resolve("a"), Promise.resolve("b"),
                new Promise<String>(new Fulfillment<String>() {
                    @Override
                    public void run(final Transition<String> transition) {
                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                transition.fulfill("c");
                            }
                        });
                    }
                }), r);
        try {
            System.out.println(p.await());
            System.out.println("end: " + System.currentTimeMillis());
        } catch (Throwable e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("err: " + System.currentTimeMillis());
        }
    }

    @Test
    public void sleep() throws Throwable {
        Promise<String> p = Promise.resolve("v");
        p.then(Transforms.delay(3, TimeUnit.SECONDS))
                .fulfilled(new Action<String>() {
                    @Override
                    public void run(String s) {
                        System.out.println(s);
                    }
                })
        .await();
    }

    @Test
    public void cancel() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        Promise<String> p = Promise.resolve("v");
        Promise<String> p1;
            p1 = p.then(Transforms.delay(3, TimeUnit.SECONDS))
                    .fulfilled(new Action<String>() {
                        @Override
                        public void run(String s) {
                            System.out.println(s);
                            latch.countDown();
                        }
                    })
                    .rejected(new Action<Throwable>() {
                        @Override
                        public void run(Throwable throwable) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println("rejected: " + throwable.getMessage());
                            latch.countDown();
                        }
                    });
        try {
            p1.await(1, TimeUnit.SECONDS);
        } catch (AwaitTimeout e) {
            boolean c = p1.cancel();
            System.out.println("canceled: " + c);
        } catch (PromiseCanceledException e) {
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        latch.await();
    }

    @Test
    public void testPromiseTransform() {
        Promise<Integer> p0 = Promise.reject(new Exception("xxx"));
        Promise<Integer> p1 = Promise.resolve(p0);
        try {
            p1.await();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Test
    public void z() throws Throwable {
        int r = Promise.resolve(1)
                .then(new Transform<Integer, Integer>() {
                    @Override
                    public Integer run(Integer v) {
                        System.out.println(v);
                        return v + 1;
                    }
                }).then(new PromiseTransform<Integer, Integer>() {
                    @Override
                    public Promise<Integer> run(final Integer v) {
                        System.out.println(v);
                        return new Promise<Integer>(new Fulfillment<Integer>() {
                            @Override
                            public void run(Transition<Integer> transition) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                transition.fulfill(v + 2);
                            }
                        });
                    }
                }).then(new Transform<Integer, Integer>() {
                    @Override
                    public Integer run(Integer v) {
                        System.out.println(v);
                        return v * 2;
                    }
                }).await();
        Assert.assertEquals(r, 8);
    }
}
