package com.bill.concurrent;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Notification;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import io.reactivex.subjects.PublishSubject;

/**
 * 对于并发请求一个work的情况，保证work只执行一次
 *
 * @author Bill.WangBW
 */
public class ConcurrentObservable<T> {
    private static final String TAG = "ConcurrentObservable";
    /**
     * 实际的工作Observable
     */
    private Observable<T> mWorkObservable;
    /**
     * 工作Observable的锁，如果锁被置为true，表示此时已经工作Observable正在执行
     */
    private final AtomicBoolean mIsWorking;

    private PublishSubject<Notification<T>> publishSubject;

    public ConcurrentObservable(Observable<T> workObservable) {
        mWorkObservable = workObservable;
        mIsWorking = new AtomicBoolean(false);
        publishSubject = PublishSubject.create();
    }

    /**
     * 创建一个Observable，同时创建一个观察者注册到subject，并尝试跑work
     */
    public Observable<T> getConcurrentObservable() {
        return Observable.create((ObservableEmitter<T> emitter) -> {
            //注册观察者到subject
            ConcurrentObservable.this.subscribe(emitter);
            //跑work线程
            doWork();
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
                                publishSubject.onNext(Notification.createOnNext(t));
                                mIsWorking.set(false);
                            }
                        }
                        , throwable -> {
                            synchronized (mIsWorking) {
                                publishSubject.onNext(Notification.createOnError(throwable));
                                mIsWorking.set(false);
                            }
                        }
                        , () -> {
                            //doNothing
                        }
                        , disposable -> {
                            synchronized (mIsWorking) {
                                if (!mIsWorking.compareAndSet(false, true) && !DisposableHelper.isDisposed(disposable)) {
                                    disposable.dispose();
                                }
                            }
                        });
    }

    private void subscribe(ObservableEmitter<T> emitter) {
        subscribeEmitter(emitter);
    }

    private Disposable subscribeEmitter(ObservableEmitter<T> emitter) {
        return publishSubject
                .subscribe(t -> {
                    if (emitter == null) {
                        Log.e(TAG, "emitter is null");
                    } else if (!emitter.isDisposed()) {
                        if (t.isOnNext()) {
                            emitter.onNext(t.getValue());
                            emitter.onComplete();
                        } else if (t.isOnError()) {
                            emitter.onError(t.getError());
                        }
                    } else {
                        Log.e(TAG, "emitter was disposed");
                    }
                });
    }
}