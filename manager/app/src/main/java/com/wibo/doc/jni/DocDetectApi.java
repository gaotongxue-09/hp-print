package com.wibo.doc.jni;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 文档/证件检测高层封装 —— 忠实复刻 base.apk 的 com.wibo.doc.jni.DocDetectApi。
 *
 * 模型加载流程（与原版一致）：
 *   1. 从 assets 读取 v5.vaimlite（检测模型）与 yolo_fp16.vaimlite（角点检测模型）
 *   2. 调用 initDocDetectModel 两次，分别初始化 runtime=0（实时预览）与 runtime=1（相册/拍照）
 *   3. 模型输出 .bin 写到 cacheDir（predodetect.bin / docdetect.bin）
 */
public class DocDetectApi {
    public static final String TAG = "DocDetectApi";

    private final String modelAssetsNamePreview = "v5.vaimlite";
    private final String modelAssetsNamePreviewDet = "yolo_fp16.vaimlite";

    private volatile boolean isInitSuccess = false;

    public boolean isInitSuccess() {
        return isInitSuccess;
    }

    private byte[] toByteArray(InputStream is) {
        if (is == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "toByteArray error", e);
            return null;
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 初始化模型（线程安全，与原版 initModel 语义一致）。 */
    public synchronized boolean initModel(Context context) {
        try {
            byte[] preview = toByteArray(context.getAssets().open(modelAssetsNamePreview));
            byte[] previewDet = toByteArray(context.getAssets().open(modelAssetsNamePreviewDet));
            if (preview == null || previewDet == null) {
                isInitSuccess = false;
                return false;
            }
            String cacheDir = context.getCacheDir().getAbsolutePath();
            String predoPath = cacheDir + "/predodetect.bin";
            String detPath = cacheDir + "/docdetect.bin";

            boolean r0 = DocDetect.initDocDetectModel(preview, 1, false, predoPath, detPath, 0, previewDet);
            boolean r1 = DocDetect.initDocDetectModel(preview, 1, false, predoPath, detPath, 1, previewDet);
            isInitSuccess = r0 && r1;
            return isInitSuccess;
        } catch (Throwable t) {
            Log.e(TAG, "initModel exception", t);
            isInitSuccess = false;
            return false;
        }
    }

    /** 实时检测（NV21 帧）。 */
    public synchronized DocPoint[] callJniDocDetectSync(byte[] nv21, int width, int height, int angle, boolean autoCapture) {
        Object[] ret = null;
        if (nv21 != null && isInitSuccess) {
            ret = DocDetect.docDetectSync(nv21, width, height, angle, autoCapture, 0);
        }
        return DocDetect.adjustPoints((DocPoint[]) ret, width, height, angle);
    }

    /** 相册图片检测。 */
    public synchronized DocPoint[] callJniDocDetectAlbum(Bitmap bitmap, DocPoint[] hints) {
        Object[] ret = null;
        if (bitmap != null && !bitmap.isRecycled() && isInitSuccess) {
            ret = DocDetect.runDocDetect(bitmap, hints, 1);
        }
        return DocDetect.adjustPoints((DocPoint[]) ret, bitmap.getWidth(), bitmap.getHeight(), 0);
    }

    /** 透视矫正。 */
    public Bitmap callJniDocRectifyAlbum(Bitmap bitmap, DocPoint[] points) {
        if (points == null || bitmap == null || bitmap.isRecycled() || points.length != 4) {
            return null;
        }
        Object out = DocDetect.runDocRectify(bitmap, points, Bitmap.Config.ARGB_8888);
        return out instanceof Bitmap ? (Bitmap) out : null;
    }

    /** JPEG 字节解码为 Bitmap。 */
    public Bitmap callJniJpeg2Bitmap(byte[] jpeg, int reqWidth, int reqHeight) {
        if (jpeg == null || !isInitSuccess) return null;
        return DocDetect.doJpeg2Bitmap(jpeg, reqWidth, reqHeight);
    }

    /** 释放模型（两个 runtime 槽位）。 */
    public boolean releaseDocModel() {
        DocDetect.releaseDocModel(0);
        DocDetect.releaseDocModel(1);
        return true;
    }
}
