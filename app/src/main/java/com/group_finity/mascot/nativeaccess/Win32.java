package com.group_finity.mascot.nativeaccess;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Windows API (User32) へのアクセスを提供するJNAインターフェース。
 */
public interface Win32 extends StdCallLibrary {
    Win32 INSTANCE = Native.load("user32", Win32.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer arg);

    boolean IsWindow(HWND hWnd);

    boolean IsWindowVisible(HWND hWnd);

    boolean IsIconic(HWND hWnd);

    boolean IsZoomed(HWND hWnd);

    int GetWindowRect(HWND hWnd, RECT r);

    int GetWindowTextA(HWND hWnd, byte[] lpString, int nMaxCount);

    int GetWindowThreadProcessId(HWND hWnd, IntByReference lpdwProcessId);

    interface WNDENUMPROC extends StdCallCallback {
        boolean callback(HWND hWnd, Pointer arg);
    }
}
