package com.ravi.jfrmcp.jfr;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility functions for safely extracting and formatting data from JFR events.
 */
public final class JfrEventUtils {

    private JfrEventUtils() {
    }

    public static String formatMethod(RecordedMethod method) {
        if (method == null) {
            return "unknown.method()";
        }
        RecordedClass type = method.getType();
        String className = type != null ? type.getName() : "UnknownClass";
        String methodName = method.getName() != null ? method.getName() : "unknownMethod";
        return className + "." + methodName + "()";
    }

    public static String formatFrame(RecordedFrame frame) {
        if (frame == null) {
            return "unknown";
        }
        RecordedMethod method = frame.getMethod();
        String methodStr = formatMethod(method);
        int lineNumber = frame.getLineNumber();
        if (lineNumber > 0) {
            return methodStr + ":" + lineNumber;
        }
        return methodStr;
    }

    public static List<String> extractStackTrace(RecordedEvent event, int maxFrames) {
        if (event == null) {
            return Collections.emptyList();
        }
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null || stackTrace.getFrames() == null) {
            return Collections.emptyList();
        }

        List<RecordedFrame> frames = stackTrace.getFrames();
        int count = Math.min(frames.size(), maxFrames > 0 ? maxFrames : frames.size());
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(formatFrame(frames.get(i)));
        }
        return result;
    }

    public static String getThreadName(RecordedEvent event) {
        if (event == null) {
            return "unknown";
        }
        RecordedThread thread = event.getThread();
        if (thread != null && thread.getJavaName() != null) {
            return thread.getJavaName();
        }
        if (event.hasField("eventThread")) {
            Object val = event.getValue("eventThread");
            if (val instanceof RecordedThread rt && rt.getJavaName() != null) {
                return rt.getJavaName();
            }
        }
        return "unknown";
    }

    public static String getString(RecordedEvent event, String fieldName, String defaultValue) {
        if (event != null && event.hasField(fieldName)) {
            try {
                String val = event.getString(fieldName);
                return val != null ? val : defaultValue;
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public static int getInt(RecordedEvent event, String fieldName, int defaultValue) {
        if (event != null && event.hasField(fieldName)) {
            try {
                Object val = event.getValue(fieldName);
                if (val instanceof Number n) {
                    return n.intValue();
                }
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public static long getLong(RecordedEvent event, String fieldName, long defaultValue) {
        if (event != null && event.hasField(fieldName)) {
            try {
                Object val = event.getValue(fieldName);
                if (val instanceof Number n) {
                    return n.longValue();
                }
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public static double getDouble(RecordedEvent event, String fieldName, double defaultValue) {
        if (event != null && event.hasField(fieldName)) {
            try {
                Object val = event.getValue(fieldName);
                if (val instanceof Number n) {
                    return n.doubleValue();
                }
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    public static double getDurationMs(RecordedEvent event) {
        if (event == null) {
            return 0.0;
        }
        Duration duration = event.getDuration();
        if (duration != null) {
            return duration.toNanos() / 1_000_000.0;
        }
        return 0.0;
    }
}
