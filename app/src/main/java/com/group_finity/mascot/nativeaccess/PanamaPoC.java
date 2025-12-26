package com.group_finity.mascot.nativeaccess;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import static java.lang.foreign.ValueLayout.*;

/**
 * Project Panama (Foreign Function & Memory API) の概念実証コード。
 * JNA と Panama のパフォーマンス比較、および Panama を用いたウィンドウ透過処理の実装例を示します。
 * 
 * <p>実行には JVM オプション --enable-preview --enable-native-access=ALL-UNNAMED が必要です。</p>
 */
public class PanamaPoC {

    // Windows API Constants
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_LAYERED = 0x80000;
    private static final int LWA_ALPHA = 0x2;

    public static void main(String[] args) throws Throwable {
        System.out.println("=== Project Panama PoC: Window Transparency & Benchmark ===");

        // ---------------------------------------------------------
        // 1. Panamaのセットアップ (MethodHandleの取得)
        // ---------------------------------------------------------
        Linker linker = Linker.nativeLinker();
        SymbolLookup user32 = SymbolLookup.libraryLookup("User32.dll", Arena.global());

        // GetWindowLongPtrW (64bit環境を想定)
        // LONG_PTR GetWindowLongPtrW(HWND hWnd, int nIndex);
        MethodHandle getWindowLongPtr = linker.downcallHandle(
            user32.find("GetWindowLongPtrW").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT)
        );

        // SetWindowLongPtrW
        // LONG_PTR SetWindowLongPtrW(HWND hWnd, int nIndex, LONG_PTR dwNewLong);
        MethodHandle setWindowLongPtr = linker.downcallHandle(
            user32.find("SetWindowLongPtrW").orElseThrow(),
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG)
        );

        // SetLayeredWindowAttributes
        // BOOL SetLayeredWindowAttributes(HWND hwnd, COLORREF crKey, BYTE bAlpha, DWORD dwFlags);
        MethodHandle setLayeredWindowAttributes = linker.downcallHandle(
            user32.find("SetLayeredWindowAttributes").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_BYTE, JAVA_INT)
        );

        // GetDesktopWindow (テスト用ハンドル取得)
        MethodHandle getDesktopWindow = linker.downcallHandle(
            user32.find("GetDesktopWindow").orElseThrow(),
            FunctionDescriptor.of(ADDRESS)
        );

        // ---------------------------------------------------------
        // 2. ベンチマーク: API呼び出しオーバーヘッドの比較
        // ---------------------------------------------------------
        System.out.println("\n--- Benchmark: GetWindowLongPtr (1,000,000 calls) ---");

        // テスト対象のウィンドウハンドル (デスクトップ)
        MemorySegment hwndSegment = (MemorySegment) getDesktopWindow.invoke();
        HWND hwndJna = User32.INSTANCE.GetDesktopWindow();

        // JNA Warmup
        for(int i=0; i<10000; i++) User32.INSTANCE.GetWindowLong(hwndJna, GWL_EXSTYLE);

        // JNA Benchmark
        long startJna = System.nanoTime();
        for(int i=0; i<1_000_000; i++) {
            User32.INSTANCE.GetWindowLong(hwndJna, GWL_EXSTYLE);
        }
        long endJna = System.nanoTime();
        double jnaMs = (endJna - startJna) / 1_000_000.0;
        System.out.printf("JNA    : %.2f ms%n", jnaMs);

        // Panama Warmup
        for(int i=0; i<10000; i++) getWindowLongPtr.invoke(hwndSegment, GWL_EXSTYLE);

        // Panama Benchmark
        long startPanama = System.nanoTime();
        for(int i=0; i<1_000_000; i++) {
            long style = (long) getWindowLongPtr.invoke(hwndSegment, GWL_EXSTYLE);
        }
        long endPanama = System.nanoTime();
        double panamaMs = (endPanama - startPanama) / 1_000_000.0;
        System.out.printf("Panama : %.2f ms%n", panamaMs);

        System.out.printf("Result : Panama is %.2fx faster than JNA%n", jnaMs / panamaMs);

        // ---------------------------------------------------------
        // 3. 透過処理の実装例 (シミュレーション)
        // ---------------------------------------------------------
        System.out.println("\n--- Transparency Implementation Example (Panama) ---");
        applyTransparencyPanama(getWindowLongPtr, setWindowLongPtr, setLayeredWindowAttributes, hwndSegment, 128);
    }

    private static void applyTransparencyPanama(
            MethodHandle get, MethodHandle set, MethodHandle setLayered,
            MemorySegment hwnd, int alpha) throws Throwable {

        // 1. 現在のスタイルを取得
        long oldStyle = (long) get.invoke(hwnd, GWL_EXSTYLE);
        // 2. WS_EX_LAYERED を追加
        long newStyle = oldStyle | WS_EX_LAYERED;
        set.invoke(hwnd, GWL_EXSTYLE, newStyle);
        // 3. アルファ値を設定 (LWA_ALPHA = 2)
        setLayered.invoke(hwnd, 0, (byte)alpha, LWA_ALPHA);

        System.out.println("Transparency logic executed via Panama handles.");
    }
}