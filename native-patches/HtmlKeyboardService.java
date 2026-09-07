package com.keyboardkustom.app;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * InputMethodService yang menampilkan file HTML keyboard sebagai WebView,
 * dan menjembatani ketikan dari JavaScript ke InputConnection sistem Android
 * (kolom chat WhatsApp, Instagram, dsb).
 */
public class HtmlKeyboardService extends InputMethodService {

    private WebView webView;

    @Override
    public View onCreateInputView() {
        webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();

        // Mengaktifkan JavaScript agar logika tombol berfungsi
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        // Daftarkan jembatan: di JavaScript akan muncul sebagai window.AndroidKeyboard
        webView.addJavascriptInterface(new KeyboardBridge(), "AndroidKeyboard");

        // Memuat file HTML dari aset lokal Capacitor
        webView.loadUrl("file:///android_asset/public/index.html");

        webView.setWebViewClient(new WebViewClient());
        return webView;
    }

    /**
     * Dipanggil setiap kali kolom input baru mendapat fokus (mis. pindah dari
     * kolom pencarian ke kolom chat). Berguna kalau nanti ingin menyesuaikan
     * tampilan tombol Enter (Kirim/Cari/Enter biasa) sesuai imeOptions.
     */
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
    }

    /** Objek yang diekspos ke JavaScript lewat window.AndroidKeyboard.* */
    private class KeyboardBridge {

        @JavascriptInterface
        public void commitText(final String text) {
            runOnUiThreadSafe(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && text != null) {
                    ic.commitText(text, 1);
                }
            });
        }

        @JavascriptInterface
        public void deleteBackward() {
            runOnUiThreadSafe(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.deleteSurroundingText(1, 0);
                }
            });
        }

        @JavascriptInterface
        public void sendEnter() {
            runOnUiThreadSafe(() -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic == null) return;

                EditorInfo ei = getCurrentInputEditorInfo();
                int action = (ei != null)
                        ? (ei.imeOptions & EditorInfo.IME_MASK_ACTION)
                        : EditorInfo.IME_ACTION_UNSPECIFIED;

                boolean noEnterFlag = ei != null
                        && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;

                if (!noEnterFlag
                        && action != EditorInfo.IME_ACTION_NONE
                        && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    // Kolom ini punya tombol aksi (Kirim, Cari, Done, dst) -> picu itu
                    ic.performEditorAction(action);
                } else {
                    // Kolom multi-baris biasa -> ketik baris baru
                    ic.commitText("\n", 1);
                }
            });
        }

        @JavascriptInterface
        public void hideKeyboard() {
            runOnUiThreadSafe(() -> requestHideSelf(0));
        }

        /** Dipanggil kalau nanti ditambahkan tombol "globe" untuk ganti keyboard. */
        @JavascriptInterface
        public void switchToNextKeyboard() {
            runOnUiThreadSafe(() -> {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            });
        }
    }

    /** JavascriptInterface callback datang dari thread WebView, bukan UI thread. */
    private void runOnUiThreadSafe(Runnable r) {
        if (webView != null) {
            webView.post(r);
        } else {
            r.run();
        }
    }
}
