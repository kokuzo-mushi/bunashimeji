package com.group_finity.mascot.nativeaccess;

import java.awt.Point;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * Project Panama (Foreign Function & Memory API) を使用した
 * Windows Native API へのアクセスユーティリティ。
 * 
 * 主に DPI スケーリングの座標変換と、ウィンドウの透過処理に使用します。
 */
public class NativeWindowUtil {

    // --- Constants for Window Styles & Attributes ---
    public static final int GWL_EXSTYLE = -20;
    public static final long WS_EX_LAYERED = 0x80000L;
    public static final int LWA_ALPHA = 0x2;
    public static final int MONITOR_DEFAULTTONEAREST = 0x00000002;
    public static final int SWP_NOZORDER = 0x0004;
    public static final int SWP_NOACTIVATE = 0x0010;
    public static final int ULW_ALPHA = 0x00000002;
    public static final byte AC_SRC_OVER = 0x00;
    public static final byte AC_SRC_ALPHA = 0x01;

    // --- FFM API Setup ---
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());
    private static final SymbolLookup GDI32 = SymbolLookup.libraryLookup("gdi32", Arena.global());

    // --- Struct Layouts ---
    public static final StructLayout POINT_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("x"),
        ValueLayout.JAVA_INT.withName("y")
    );
    
    public static final StructLayout RECT_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("left"),
        ValueLayout.JAVA_INT.withName("top"),
        ValueLayout.JAVA_INT.withName("right"),
        ValueLayout.JAVA_INT.withName("bottom")
    );

    public static final StructLayout MONITORINFO_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("cbSize"),
        RECT_LAYOUT.withName("rcMonitor"),
        RECT_LAYOUT.withName("rcWork"),
        ValueLayout.JAVA_INT.withName("dwFlags")
    );

    public static final StructLayout SIZE_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("cx"),
        ValueLayout.JAVA_INT.withName("cy")
    );

    public static final StructLayout BLENDFUNCTION_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("BlendOp"),
        ValueLayout.JAVA_BYTE.withName("BlendFlags"),
        ValueLayout.JAVA_BYTE.withName("SourceConstantAlpha"),
        ValueLayout.JAVA_BYTE.withName("AlphaFormat")
    );

    // --- Method Handles ---
    private static final MethodHandle PhysicalToLogicalPointForPerMonitorDPI;
    private static final MethodHandle GetWindowLongPtrW;
    private static final MethodHandle SetWindowLongPtrW;
    private static final MethodHandle SetLayeredWindowAttributes;
    private static final MethodHandle MonitorFromWindow;
    private static final MethodHandle GetMonitorInfoW;
    private static final MethodHandle GetDpiForWindow;
    private static final MethodHandle SetWindowPos;
    private static final MethodHandle UpdateLayeredWindow;
    private static final MethodHandle CreateCompatibleDC;
    private static final MethodHandle CreateBitmap;
    private static final MethodHandle SelectObject;
    private static final MethodHandle DeleteObject;
    private static final MethodHandle DeleteDC;
    private static final MethodHandle GetDC;
    private static final MethodHandle ReleaseDC;

    static {
        // 1. PhysicalToLogicalPointForPerMonitorDPI
        // BOOL PhysicalToLogicalPointForPerMonitorDPI(HWND hWnd, LPPOINT lpPoint);
        PhysicalToLogicalPointForPerMonitorDPI = findFunction("PhysicalToLogicalPointForPerMonitorDPI",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // 2. GetWindowLongPtrW (Fallback to GetWindowLongW for 32-bit compatibility if needed, but we target 64-bit)
        // LONG_PTR GetWindowLongPtrW(HWND hWnd, int nIndex);
        MethodHandle getWindowLong = findFunctionOptional("GetWindowLongPtrW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        if (getWindowLong == null) {
            getWindowLong = findFunction("GetWindowLongW",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)); // Note: Returns 32-bit int extended to long
        }
        GetWindowLongPtrW = getWindowLong;

        // 3. SetWindowLongPtrW (Fallback to SetWindowLongW)
        // LONG_PTR SetWindowLongPtrW(HWND hWnd, int nIndex, LONG_PTR dwNewLong);
        MethodHandle setWindowLong = findFunctionOptional("SetWindowLongPtrW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        if (setWindowLong == null) {
            setWindowLong = findFunction("SetWindowLongW",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        }
        SetWindowLongPtrW = setWindowLong;

        // 4. SetLayeredWindowAttributes
        // BOOL SetLayeredWindowAttributes(HWND hwnd, COLORREF crKey, BYTE bAlpha, DWORD dwFlags);
        SetLayeredWindowAttributes = findFunction("SetLayeredWindowAttributes",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_BYTE, ValueLayout.JAVA_INT));
            
        // 5. MonitorFromWindow
        // HMONITOR MonitorFromWindow(HWND hwnd, DWORD dwFlags);
        MonitorFromWindow = findFunction("MonitorFromWindow",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // 6. GetMonitorInfoW
        // BOOL GetMonitorInfoW(HMONITOR hMonitor, LPMONITORINFO lpmi);
        GetMonitorInfoW = findFunction("GetMonitorInfoW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // 7. GetDpiForWindow (Windows 10 1607+)
        // UINT GetDpiForWindow(HWND hwnd);
        MethodHandle getDpi = findFunctionOptional("GetDpiForWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        if (getDpi == null) {
            // Fallback for older Windows if needed, though we target Win10+
            System.err.println("[NativeWindowUtil] GetDpiForWindow not found.");
        }
        GetDpiForWindow = getDpi;

        // 8. SetWindowPos
        // BOOL SetWindowPos(HWND hWnd, HWND hWndInsertAfter, int X, int Y, int cx, int cy, UINT uFlags);
        SetWindowPos = findFunction("SetWindowPos",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

        // 9. UpdateLayeredWindow
        // BOOL UpdateLayeredWindow(HWND hWnd, HDC hdcDst, POINT *pptDst, SIZE *psize, HDC hdcSrc, POINT *pptSrc, COLORREF crKey, BLENDFUNCTION *pblend, DWORD dwFlags);
        UpdateLayeredWindow = findFunction("UpdateLayeredWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // --- GDI32 Functions ---
        // HDC CreateCompatibleDC(HDC hdc);
        CreateCompatibleDC = findFunctionGdi("CreateCompatibleDC",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // HBITMAP CreateBitmap(int nWidth, int nHeight, UINT nPlanes, UINT nBitCount, const void *lpBits);
        CreateBitmap = findFunctionGdi("CreateBitmap",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // HGDIOBJ SelectObject(HDC hdc, HGDIOBJ h);
        SelectObject = findFunctionGdi("SelectObject",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // BOOL DeleteObject(HGDIOBJ ho);
        DeleteObject = findFunctionGdi("DeleteObject",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // BOOL DeleteDC(HDC hdc);
        DeleteDC = findFunctionGdi("DeleteDC",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // HDC GetDC(HWND hWnd);
        GetDC = findFunction("GetDC",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // int ReleaseDC(HWND hWnd, HDC hDC);
        ReleaseDC = findFunction("ReleaseDC",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    /**
     * ヘルパー: 関数シンボルを検索し、MethodHandle を取得する。見つからない場合は例外をスロー。
     */
    private static MethodHandle findFunction(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(
            USER32.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name)),
            descriptor
        );
    }

    /**
     * ヘルパー: GDI32から関数シンボルを検索
     */
    private static MethodHandle findFunctionGdi(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(
            GDI32.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found in GDI32: " + name)),
            descriptor
        );
    }

    /**
     * ヘルパー: 関数シンボルを検索し、MethodHandle を取得する。見つからない場合は null を返す。
     */
    private static MethodHandle findFunctionOptional(String name, FunctionDescriptor descriptor) {
        return USER32.find(name)
            .map(symbol -> LINKER.downcallHandle(symbol, descriptor))
            .orElse(null);
    }

    /**
     * 物理座標を論理座標に変換します (Per-Monitor DPI 対応)。
     * 
     * @param hwndSegment ウィンドウハンドル (HWND)
     * @param x 物理 X 座標
     * @param y 物理 Y 座標
     * @return 論理座標 (Point)。変換に失敗した場合は入力座標をそのまま返します。
     */
    public static Point convertPhysicalToLogical(MemorySegment hwndSegment, int x, int y) {
        try (Arena arena = Arena.ofConfined()) {
            // POINT 構造体の割り当て
            MemorySegment point = arena.allocate(POINT_LAYOUT);
            point.set(ValueLayout.JAVA_INT, 0, x);
            point.set(ValueLayout.JAVA_INT, 4, y); // offset 4 bytes for y

            // API 呼び出し
            int result = (int) PhysicalToLogicalPointForPerMonitorDPI.invokeExact(hwndSegment, point);

            if (result != 0) {
                // 成功: 構造体から変換後の値を取得
                int logicalX = point.get(ValueLayout.JAVA_INT, 0);
                int logicalY = point.get(ValueLayout.JAVA_INT, 4);
                return new Point(logicalX, logicalY);
            } else {
                // 失敗: 元の値を返す (ログ出力などを検討しても良い)
                return new Point(x, y);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return new Point(x, y);
        }
    }

    /**
     * ウィンドウの属性 (LongPtr) を取得します。
     */
    public static long getWindowLongPtr(MemorySegment hwnd, int index) {
        try {
            return (long) GetWindowLongPtrW.invokeExact(hwnd, index);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call GetWindowLongPtrW", t);
        }
    }

    /**
     * ウィンドウの属性 (LongPtr) を設定します。
     */
    public static long setWindowLongPtr(MemorySegment hwnd, int index, long newValue) {
        try {
            return (long) SetWindowLongPtrW.invokeExact(hwnd, index, newValue);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call SetWindowLongPtrW", t);
        }
    }

    /**
     * レイヤードウィンドウの属性 (透明度など) を設定します。
     */
    public static boolean setLayeredWindowAttributes(MemorySegment hwnd, int crKey, byte bAlpha, int dwFlags) {
        try {
            int result = (int) SetLayeredWindowAttributes.invokeExact(hwnd, crKey, bAlpha, dwFlags);
            return result != 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call SetLayeredWindowAttributes", t);
        }
    }

    /**
     * 指定されたウィンドウがあるモニタのワークエリア（タスクバーを除いた領域）を取得します。
     * 物理座標から論理座標への変換も行います。
     * 
     * @param hwndSegment ウィンドウハンドル
     * @return 論理座標ベースのワークエリア (Rectangle)
     */
    public static java.awt.Rectangle getWorkAreaForWindow(MemorySegment hwndSegment) {
        try (Arena arena = Arena.ofConfined()) {
            // 1. MonitorFromWindow でモニタハンドルを取得
            MemorySegment hMonitor = (MemorySegment) MonitorFromWindow.invokeExact(hwndSegment, MONITOR_DEFAULTTONEAREST);

            // 2. MONITORINFO 構造体を準備
            MemorySegment monitorInfo = arena.allocate(MONITORINFO_LAYOUT);
            monitorInfo.set(ValueLayout.JAVA_INT, 0, (int) MONITORINFO_LAYOUT.byteSize()); // cbSize

            // 3. GetMonitorInfoW を呼び出し
            int result = (int) GetMonitorInfoW.invokeExact(hMonitor, monitorInfo);
            if (result == 0) {
                // 失敗時はデフォルト（画面全体など）を返すべきだが、ここではnullを返して呼び出し元で対処
                return null;
            }

            // 4. rcWork (物理座標) を取得
            // rcWork は offset 20 (cbSize:4 + rcMonitor:16)
            long rcWorkOffset = 4 + 16; 
            int left = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset);
            int top = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset + 4);
            int right = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset + 8);
            int bottom = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset + 12);

            // 5. 物理座標 -> 論理座標 変換
            Point topLeft = convertPhysicalToLogical(hwndSegment, left, top);
            Point bottomRight = convertPhysicalToLogical(hwndSegment, right, bottom);

            return new java.awt.Rectangle(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get work area", t);
        }
    }

    /**
     * ウィンドウのDPIを取得します。
     * @param hwndSegment ウィンドウハンドル
     * @return DPI値 (取得できない場合はデフォルトの96を返す)
     */
    public static int getDpiForWindow(MemorySegment hwndSegment) {
        if (GetDpiForWindow == null) return 96;
        try {
            return (int) GetDpiForWindow.invokeExact(hwndSegment);
        } catch (Throwable t) {
            return 96;
        }
    }

    /**
     * 物理座標を指定してウィンドウの位置とサイズを設定します。
     */
    public static void setWindowPosPhysical(MemorySegment hwndSegment, int x, int y, int width, int height) {
        try {
            // HWND_TOP = 0 (Zオーダー変更なしフラグSWP_NOZORDERを指定するため第2引数は無視されるが0を渡す)
            int result = (int) SetWindowPos.invokeExact(hwndSegment, MemorySegment.NULL, x, y, width, height, SWP_NOZORDER | SWP_NOACTIVATE);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call SetWindowPos", t);
        }
    }

    /**
     * モニタの物理的なワークエリアを取得します。
     * 論理座標への変換は行いません。
     */
    public static java.awt.Rectangle getPhysicalWorkArea(MemorySegment hwndSegment) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment hMonitor = (MemorySegment) MonitorFromWindow.invokeExact(hwndSegment, MONITOR_DEFAULTTONEAREST);

            MemorySegment monitorInfo = arena.allocate(MONITORINFO_LAYOUT);
            monitorInfo.set(ValueLayout.JAVA_INT, 0, (int) MONITORINFO_LAYOUT.byteSize());

            int result = (int) GetMonitorInfoW.invokeExact(hMonitor, monitorInfo);
            if (result == 0) return null;

            long rcWorkOffset = 4 + 16; 
            int left = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset);
            int top = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset + 4);
            int right = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset + 8);
            int bottom = monitorInfo.get(ValueLayout.JAVA_INT, rcWorkOffset + 12);

            return new java.awt.Rectangle(left, top, right - left, bottom - top);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get physical work area", t);
        }
    }

    /**
     * UpdateLayeredWindow を使用して、透過画像を含むウィンドウを描画します。
     * 
     * @param hwndSegment ウィンドウハンドル
     * @param pBits 画像データ (ARGB) へのポインタ
     * @param width 画像幅
     * @param height 画像高さ
     */
    public static void updateLayeredWindow(MemorySegment hwndSegment, MemorySegment pBits, int width, int height) {
        try (Arena arena = Arena.ofConfined()) {
            // 1. スクリーンDCとメモリDCの作成
            MemorySegment screenDC = (MemorySegment) GetDC.invokeExact(MemorySegment.NULL);
            MemorySegment memDC = (MemorySegment) CreateCompatibleDC.invokeExact(screenDC);

            // 2. ビットマップの作成と選択
            // CreateBitmap(width, height, planes=1, bpp=32, bits)
            MemorySegment hBitmap = (MemorySegment) CreateBitmap.invokeExact(width, height, 1, 32, pBits);
            MemorySegment oldBitmap = (MemorySegment) SelectObject.invokeExact(memDC, hBitmap);

            // 3. 構造体の準備
            MemorySegment pptDst = MemorySegment.NULL; // 位置は変更しない
            
            MemorySegment psize = arena.allocate(SIZE_LAYOUT);
            psize.set(ValueLayout.JAVA_INT, 0, width);
            psize.set(ValueLayout.JAVA_INT, 4, height);

            MemorySegment pptSrc = arena.allocate(POINT_LAYOUT);
            pptSrc.set(ValueLayout.JAVA_INT, 0, 0);
            pptSrc.set(ValueLayout.JAVA_INT, 4, 0);

            MemorySegment pblend = arena.allocate(BLENDFUNCTION_LAYOUT);
            pblend.set(ValueLayout.JAVA_BYTE, 0, AC_SRC_OVER); // BlendOp
            pblend.set(ValueLayout.JAVA_BYTE, 1, (byte) 0);    // BlendFlags
            pblend.set(ValueLayout.JAVA_BYTE, 2, (byte) 255);  // SourceConstantAlpha (255 = per-pixel alpha uses image alpha)
            pblend.set(ValueLayout.JAVA_BYTE, 3, AC_SRC_ALPHA);// AlphaFormat

            // 4. UpdateLayeredWindow 呼び出し
            int result = (int) UpdateLayeredWindow.invokeExact(
                hwndSegment,
                screenDC,
                pptDst,
                psize,
                memDC,
                pptSrc,
                0,      // crKey (not used with ULW_ALPHA)
                pblend,
                ULW_ALPHA
            );

            // 5. クリーンアップ
            MemorySegment prevBitmap = (MemorySegment) SelectObject.invokeExact(memDC, oldBitmap); // 元のビットマップに戻す
            int r1 = (int) DeleteObject.invokeExact(hBitmap);
            int r2 = (int) DeleteDC.invokeExact(memDC);
            int r3 = (int) ReleaseDC.invokeExact(MemorySegment.NULL, screenDC);

        } catch (Throwable t) {
            // 頻繁に呼ばれるため、スタックトレースは抑制するか、重大なエラーのみログ出力
            // t.printStackTrace(); 
            System.err.println("ULW Failed: " + t.getMessage());
        }
    }
}