package com.wibo.doc.jni;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * 证件后处理高层封装 —— 忠实复刻 base.apk 的 com.wibo.doc.jni.DocToolsApi。
 */
public class DocToolsApi {
    public static final String TAG = "DocToolsApi";

    public void callJniMakeCardCorner(Bitmap bitmap, double cornerMm, double cardWidthMm) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "[error]: bitmap is null");
            return;
        }
        try {
            DocTools.MakeCardCorner(bitmap, cornerMm, cardWidthMm);
        } catch (Exception e) {
            Log.e("JniExceptionHandler", String.valueOf(e));
        }
    }

    public void callJniMakeSign(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "[error]: bitmap is null");
            return;
        }
        try {
            DocTools.MakeSign(bitmap);
        } catch (Exception e) {
            Log.e("JniExceptionHandler", String.valueOf(e));
        }
    }
}
