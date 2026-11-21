package com.wibo.doc.jni;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * 滤镜高层封装 —— 忠实复刻 base.apk 的 com.wibo.doc.jni.DocFiltersApi。
 */
public class DocFiltersApi {
    public static final String TAG = "DocFiltersApi";

    public void callJniEnhanceBrightness(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "[error]: bitmap is null");
            return;
        }
        try {
            DocFilters.EnhanceBrightness(bitmap);
        } catch (Exception e) {
            Log.e("JniExceptionHandler", String.valueOf(e));
        }
    }

    public void callJniEnhanceGray(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "[error]: bitmap is null");
            return;
        }
        try {
            DocFilters.EnhanceGray(bitmap);
        } catch (Exception e) {
            Log.e("JniExceptionHandler", String.valueOf(e));
        }
    }

    public void callJniEnhanceImg(Bitmap bitmap) {
        callJniEnhanceSoft(bitmap, 0.0f);
    }

    public void callJniEnhanceSoft(Bitmap bitmap, float strength) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "[error]: bitmap is null");
            return;
        }
        try {
            DocFilters.EnhanceImg(bitmap, strength);
        } catch (Exception e) {
            Log.e("JniExceptionHandler", String.valueOf(e));
        }
    }
}
