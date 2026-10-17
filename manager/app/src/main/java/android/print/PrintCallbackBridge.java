package android.print;

/**
 * 桥接类：SDK stub 中 PrintDocumentAdapter 的 LayoutResultCallback /
 * WriteResultCallback 构造为包私有（运行时 framework 中实为 public），
 * 应用层无法直接继承。本类置于 android.print 包内，编译期同包可访问，
 * 以匿名子类创建回调，再通过 [LayoutSink] / [WriteSink] 把结果交回应用代码。
 */
public final class PrintCallbackBridge {

    public interface LayoutSink {
        void onFinished(PrintDocumentInfo info, boolean changed);
        void onFailed(CharSequence error);
    }

    public interface WriteSink {
        void onFinished(PageRange[] pages);
        void onFailed(CharSequence error);
    }

    public static PrintDocumentAdapter.LayoutResultCallback newLayoutCallback(
            final LayoutSink sink) {
        return new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                sink.onFinished(info, changed);
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                sink.onFailed(error);
            }
        };
    }

    public static PrintDocumentAdapter.WriteResultCallback newWriteCallback(
            final WriteSink sink) {
        return new PrintDocumentAdapter.WriteResultCallback() {
            @Override
            public void onWriteFinished(PageRange[] pages) {
                sink.onFinished(pages);
            }

            @Override
            public void onWriteFailed(CharSequence error) {
                sink.onFailed(error);
            }
        };
    }

    private PrintCallbackBridge() {}
}
