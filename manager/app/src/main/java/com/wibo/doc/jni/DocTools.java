package com.wibo.doc.jni;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * 证件后处理原生声明层。
 * 与 libdoc_sdk_v1.6.2.1.so 导出的 Java_com_wibo_doc_jni_DocTools_* 符号一致。
 */
public class DocTools {
    public static final String TAG = "DocTools";

    static {
        try {
            System.loadLibrary("doc_sdk_v1.6.2.1");
        } catch (Throwable unused) {
            Log.e(TAG, "load library fail!");
        }
    }

    /** 证件圆角：cornerMm=圆角半径(mm)，cardWidthMm=卡宽(mm)。就地修改 bitmap。 */
    public static native void MakeCardCorner(Bitmap bitmap, double cornerMm, double cardWidthMm);

    /** 签名增强/去印章。就地修改 bitmap。 */
    public static native void MakeSign(Bitmap bitmap);
}
