package com.wibo.doc.jni;

/**
 * 文档/证件四角坐标点。
 * 与 base.apk 反编译出的 com.wibo.doc.jni.DocPoint 完全一致（字段/方法名与语义）。
 */
public class DocPoint {
    private float x;
    private float y;

    public DocPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "DocPoint(" + this.x + ", " + this.y + ")";
    }
}
