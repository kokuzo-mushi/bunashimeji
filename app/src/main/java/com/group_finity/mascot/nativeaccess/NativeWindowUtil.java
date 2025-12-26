package com.group_finity.mascot.nativeaccess;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import static java.lang.foreign.ValueLayout.*;

/**
 * Project Panama (Foreign Function & Memory API) を使用したネイティブウィンドウ操作ユーティリティ。
 * JNAを使用せず、直接ネイティブ関数を呼び出すことで高速な動作を実現する。
 * <p>
 * Java 21 Preview機能を使用しているため、実行時には --enable-preview が必要。
 * </p>
 */
public class NativeWindowUtil {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("User32.dll", Arena.global());

    // MethodHandles
    private static final MethodHandle GET_WINDOW_LONG_PTR;
    private static final MethodHandle SET_WINDOW_LONG_PTR;
    private static final MethodHandle SET_LAYERED_WINDOW_ATTRIBUTES;
    private static final MethodHandle MOVE_WINDOW;
    private static final MethodHandle GET_DESKTOP_WINDOW;

    // Constants
    public static final int GWL_EXSTYLE = -20;
    public static final int WS_EX_LAYERED = 0x80000;
    public static final int LWA_ALPHA = 0x2;

    static {
        try {
            // Windows APIの関数ポインタを取得
            GET_WINDOW_LONG_PTR = find("GetWindowLongPtrW", JAVA_LONG, ADDRESS, JAVA_INT);
            SET_WINDOW_LONG_PTR = find("SetWindowLongPtrW", JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG);
            SET_LAYERED_WINDOW_ATTRIBUTES = find("SetLayeredWindowAttributes", JAVA_INT, ADDRESS, JAVA_INT, JAVA_BYTE, JAVA_INT);
            MOVE_WINDOW = find("MoveWindow", JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_BOOLEAN);
            GET_DESKTOP_WINDOW = find("GetDesktopWindow", ADDRESS);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle find(String name, MemoryLayout resLayout, MemoryLayout... argLayouts) {
        return LINKER.downcallHandle(
            USER32.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)),
            FunctionDescriptor.of(resLayout, argLayouts)
        );
    }

    /**
     * ウィンドウの属性を取得します。
     */
    public static long getWindowLongPtr(MemorySegment hWnd, int nIndex) {
        try {
            return (long) GET_WINDOW_LONG_PTR.invokeExact(hWnd, nIndex);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to call GetWindowLongPtrW", e);
        }
    }

    /**
     * ウィンドウの属性を設定します。
     */
    public static long setWindowLongPtr(MemorySegment hWnd, int nIndex, long dwNewLong) {
        try {
            return (long) SET_WINDOW_LONG_PTR.invokeExact(hWnd, nIndex, dwNewLong);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to call SetWindowLongPtrW", e);
        }
    }

    /**
     * レイヤードウィンドウの属性（透過度など）を設定します。
     */
    public static boolean setLayeredWindowAttributes(MemorySegment hWnd, int crKey, byte bAlpha, int dwFlags) {
        try {
            int result = (int) SET_LAYERED_WINDOW_ATTRIBUTES.invokeExact(hWnd, crKey, bAlpha, dwFlags);
            return result != 0;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to call SetLayeredWindowAttributes", e);
        }
    }

    /**
     * ウィンドウの位置とサイズを変更します。
     */
    public static boolean moveWindow(MemorySegment hWnd, int x, int y, int width, int height, boolean repaint) {
        try {
            int result = (int) MOVE_WINDOW.invokeExact(hWnd, x, y, width, height, repaint);
            return result != 0;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to call MoveWindow", e);
        }
    }

    /**
     * デスクトップウィンドウのハンドルを取得します。
     */
    public static MemorySegment getDesktopWindow() {
        try {
            return (MemorySegment) GET_DESKTOP_WINDOW.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to call GetDesktopWindow", e);
        }
    }
}