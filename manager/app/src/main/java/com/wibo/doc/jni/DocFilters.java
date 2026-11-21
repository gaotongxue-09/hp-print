package com.wibo.doc.jni;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * 图像滤镜原生声明层。
 * 与 libdoc_sdk_v1.6.2.1.so 导出的 Java_com_wibo_doc_jni_DocFilters_* 符号一致。
 */
public class DocFilters {
    public static final String TAG = "DocFilters";

    static {
        try {
            System.loadLibrary("doc_sdk_v1.6.2.1");
        } catch (Throwable unused) {
            Log.e(TAG, "load library fail!");
        }
    }

    /** 提亮（滤镜 type=1）。就地修改 bitmap。 */
    public static native void EnhanceBrightness(Bitmap bitmap);

    /** 灰度（滤镜 type=3）。就地修改 bitmap。 */
    public static native void EnhanceGray(Bitmap bitmap);

    /** 增强/柔化（滤镜 type=2/4），f 为强度。就地修改 bitmap。 */
    public static native void EnhanceImg(Bitmap bitmap, float strength);
}
