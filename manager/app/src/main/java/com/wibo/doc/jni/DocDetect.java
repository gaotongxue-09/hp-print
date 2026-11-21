package com.wibo.doc.jni;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * 文档/证件检测 + 透视矫正的原生声明层。
 * 包名、类名、native 方法签名与 base.apk 反编译结果及 libdoc_sdk_v1.6.2.1.so
 * 导出的符号（Java_com_wibo_doc_jni_DocDetect_*）严格一致。
 *
 * 说明：原版 Java 还声明了 native Resize(Object,int,Object)，但 .so 未导出该符号、
 * 且原版代码中从未调用（callJniDocResize 为死代码），故此处不声明，避免 UnsatisfiedLinkError。
 */
public class DocDetect {
    public static final String TAG = "DocDetect";

    static {
        try {
            System.loadLibrary("doc_sdk_v1.6.2.1");
        } catch (Throwable unused) {
            Log.e(TAG, "load library fail!");
        }
    }

    /** 把 JPEG 字节解码为 Bitmap（原生 libjpeg），请求宽高用于预缩放。 */
    public static native Bitmap doJpeg2Bitmap(byte[] jpeg, int reqWidth, int reqHeight);

    /** 实时检测：入参为 NV21 帧数据，返回 DocPoint[]（4 点，传感器坐标）。 */
    public static native Object[] docDetectSync(byte[] nv21, int width, int height, int angle, boolean autoCapture, int runtime);

    /** 初始化检测模型：入参为模型字节与输出 .bin 路径。 */
    public static native boolean initDocDetectModel(byte[] model, int size, boolean flag, String predodetectPath, String docdetectPath, int runtime, byte[] detModel);

    /** 释放指定 runtime 槽位的模型。 */
    public static native void releaseDocModel(int runtime);

    /** 相册图片检测：入参 Bitmap + 参考点，返回 DocPoint[]。 */
    public static native Object[] runDocDetect(Object bitmap, Object[] points, int runtime);

    /** 透视矫正：入参 Bitmap + 四角 + Bitmap.Config，返回矫正后的 Bitmap。 */
    public static native Object runDocRectify(Object bitmap, Object[] points, Object config);

    /**
     * 把检测点夹取到图片范围内。
     * 与原版 base.apk 完全一致（经 smali 字节码核实）：
     *   `rem-int/lit16 p3,p3,0xb4`  →  angle % 180
     *   `move v5,p2; move p2,p1; move p1,v5`  →  交换 width/height
     * 即 angle 为 90°/270° 时交换宽高后夹取（jadx 误还原为 no-op，此处按真实字节码实现）。
     */
    public static DocPoint[] adjustPoints(DocPoint[] points, int width, int height, int angle) {
        if (points == null) {
            float w = width;
            float h = height;
            return new DocPoint[]{
                    new DocPoint(0.0f, 0.0f), new DocPoint(w, 0.0f),
                    new DocPoint(w, h), new DocPoint(0.0f, h)
            };
        }
        if (angle % 180 != 0) {
            int tmp = width;
            width = height;
            height = tmp;
        }
        float maxX = width;
        float maxY = height;
        for (DocPoint p : points) {
            if (p.getX() < 0.0f) p.setX(0.0f);
            if (p.getY() < 0.0f) p.setY(0.0f);
            if (p.getX() > maxX) p.setX(maxX);
            if (p.getY() > maxY) p.setY(maxY);
        }
        return points;
    }
}
