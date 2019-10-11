package com.bill.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.Disposable;

/**
 * 对于并发请求一个work的情况，保证work只执行一次
 *
 * @author Bill.WangBW
 */
public class ConcurrentObservable2<T> {
    private static final String TAG = "ConcurrentObservable";
    /**
     * 实际的工作Observable
     */
    private Observable<T> mWorkObservable;
    /**
     * 工作Observable的锁，如果锁被置为true，表示此时已经工作Observable正在执行
     */
    private final AtomicBoolean mIsWorking;

    private List<Emitter<T>> emitters;

    public ConcurrentObservable2(Observable<T> workObservable) {
        mWorkObservable = workObservable;
        mIsWorking = new AtomicBoolean(false);
        emitters = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * 创建一个Observable，同时创建一个观察者注册到subject，并尝试跑work
     */
    public Observable<T> getConcurrentObservable() {
        return Observable.create((ObservableEmitter<T> emitter) -> {
            //注册观察者到subject
            ConcurrentObservable2.this.subscribe(emitter);
            //跑work线程
            if (mIsWorking.compareAndSet(false, true)) {
                doWork();
            }
        });
    }

    private Observable<T> getWorkObservable() {
        return mWorkObservable;
    }

    /**
     * 跑work
     * <p>
     * 第一个请求会将work锁住，直到锁被重置，其他请求看到锁不会等待直接取消跑work的尝试
     * <p>
     * 当work订阅时，检查是否已上锁，如果上锁就取消work的执行，否则继续跑work并上锁
     */
    private Disposable doWork() {
        return getWorkObservable()
                .subscribe(t -> {
                            synchronized (mIsWorking) {
                                Iterator<Emitter<T>> i = emitters.listIterator();
                                do {
                                    Emitter<T> e = i.next();
                                    e.onNext(t);
                                } while (i.hasNext());
                            }
                        }
                        , throwable -> {
                            synchronized (mIsWorking) {
                                Iterator<Emitter<T>> i = emitters.listIterator();
                                do {
                                    Emitter<T> e = i.next();
                                    e.onError(throwable);
                                    i.remove();
                                } while (i.hasNext());
                                mIsWorking.set(false);
                            }
                        }
                        , () -> {
                            synchronized (mIsWorking) {
                                Iterator<Emitter<T>> i = emitters.listIterator();
                                do {
                                    Emitter<T> e = i.next();
                                    e.onComplete();
                                    i.remove();
                                } while (i.hasNext());
                                mIsWorking.set(false);
                            }
                        });
    }

    private void subscribe(ObservableEmitter<T> emitter) {
        synchronized (mIsWorking) {
            emitters.add(emitter);
        }
    }

}