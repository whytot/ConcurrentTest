package com.bill.concurrent;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main";

    private ConcurrentObservable2<String> mConcurrentObservable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClick0(View view) {
        new Thread(() -> testDelaySecond()).start();
    }

    public void onClick1(View view) {
        new Thread(() -> testDelayMillisecond()).start();
    }

    public void onClick2(View view) {
        new Thread(() -> testDelayMicrosecond()).start();
    }

    private void testDelaySecond() {
        for (int i = 0; i < 10; i++) {
            if (i % 3 == 0) {
                sleep(TimeUnit.SECONDS, 1);
            }
            test(i, TimeUnit.SECONDS);
        }
    }

    private void testDelayMillisecond() {
        for (int i = 0; i < 10; i++) {
            if (i % 3 == 0) {
                sleep(TimeUnit.MILLISECONDS, 1);
            }
            test(i, TimeUnit.MILLISECONDS);
        }
    }

    private void testDelayMicrosecond() {
        for (int i = 0; i < 20; i++) {
            if (i % 3 == 0) {
                sleep(TimeUnit.MICROSECONDS, 1);
            }
            test(i, TimeUnit.MICROSECONDS);
        }
    }

    private void test(int i, TimeUnit timeUnit) {
        Log.e(TAG, "test: request - " + i);
        if (mConcurrentObservable == null) {
            mConcurrentObservable = new ConcurrentObservable2<>(Observable.just("aaa")
                    .delay(2, timeUnit)
                    .map(s -> {
                        Log.e(TAG, "test: map - " + s);
                        return s;
                    })
                    .doOnSubscribe(disposable -> Log.e(TAG, "work: subscribe"))
                    .doOnNext(s -> Log.e(TAG, "work: next - " + s))
                    .subscribeOn(Schedulers.io()));
        }
        mConcurrentObservable.getConcurrentObservable()
                .subscribe(s -> Log.e(TAG, "test: next - " + i + "[" + s + "]"),
                        throwable -> Log.e(TAG, "test: error", throwable),
                        () -> Log.e(TAG, "test: complete"),
                        disposable -> Log.e(TAG, "test: subscribe"));
    }

    private void sleep(TimeUnit timeUnit, long time) {
        try {
            Thread.sleep(timeUnit.toMillis(time));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
