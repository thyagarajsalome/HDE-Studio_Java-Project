using System;
using System.Text;
using System.Runtime.InteropServices;

public class Program {
    delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    static extern bool EnumWindows(EnumWindowsProc enumProc, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("dwmapi.dll")]
    static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    public static void Main(string[] args) {
        string search = args.Length > 0 ? args[0] : "HDE Studio";
        EnumWindows((hWnd, lParam) => {
            StringBuilder sb = new StringBuilder(256);
            GetWindowText(hWnd, sb, 256);
            string title = sb.ToString();
            if (title.Contains(search)) {
                int dark = 1;
                DwmSetWindowAttribute(hWnd, 20, ref dark, sizeof(int));
                DwmSetWindowAttribute(hWnd, 19, ref dark, sizeof(int));
            }
            return true;
        }, IntPtr.Zero);
    }
}
