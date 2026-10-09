package android.app;

import android.content.Context;

/** 仅供编译，运行时由 Android boot classpath 提供。 */
public final class ActivityThread {
    public static ActivityThread currentActivityThread() { throw new UnsupportedOperationException("compile-only stub"); }
    public Context getSystemContext() { throw new UnsupportedOperationException("compile-only stub"); }
}
