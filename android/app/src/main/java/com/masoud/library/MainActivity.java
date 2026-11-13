
package com.masoud.library;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.ValueCallback;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private static final String WEBSITE_URL =
            "https://masoud73d.github.io/library/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF160A06);
        webView.setClipToPadding(true);
        setContentView(webView);

        // هماهنگی با نوارهای سیستمی اندروید
        webView.setOnApplyWindowInsetsListener((view, insets) -> {

            int left;
            int top;
            int right;
            int bottom;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                android.graphics.Insets systemInsets =
                        insets.getInsets(
                                WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                        );

                left = systemInsets.left;
                top = systemInsets.top;
                right = systemInsets.right;
                bottom = systemInsets.bottom;

            } else {

                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            view.setPadding(left, top, right, bottom);

            return insets;
        });

        WebSettings settings = webView.getSettings();

        // تنظیمات اصلی
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // تنظیم اندازه و مقیاس
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // دسترسی به فایل‌ها
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        // پاک کردن کش قبلی
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);
        webView.clearHistory();

        // تنظیم کوکی‌ها
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // مدیریت لینک‌ها
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return handleUrl(Uri.parse(url));
            }

            private boolean handleUrl(Uri uri) {

                String url = uri.toString();

                if (url.startsWith("http://")
                        || url.startsWith("https://")) {
                    return false;
                }

                try {
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW,
                            uri
                    );

                    startActivity(intent);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        });

        // انتخاب عکس و فایل
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {
                    Intent intent = params.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (ActivityNotFoundException e) {

                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;

                    return false;
                }
            }
        });

        webView.loadUrl(WEBSITE_URL);
    }

    // دریافت فایل انتخاب‌شده
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {

            if (filePathCallback != null) {

                Uri[] results =
                        WebChromeClient.FileChooserParams.parseResult(
                                resultCode,
                                data
                        );

                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    // مدیریت دکمه برگشت گوشی
    @Override
    public void onBackPressed() {

        if (webView == null) {
            super.onBackPressed();
            return;
        }

        webView.evaluateJavascript(
            "(function() {" +

            "var app = document.getElementById('appOverlay');" +
            "var list = document.getElementById('listOverlay');" +
            "var about = document.getElementById('aboutOverlay');" +

            "if (app && app.classList.contains('show')) {" +
            "if (typeof closeApp === 'function') closeApp();" +
            "return 'handled';" +
            "}" +

            "if (list && list.classList.contains('show')) {" +
            "if (typeof hideList === 'function') hideList();" +
            "return 'handled';" +
            "}" +

            "if (about && about.classList.contains('show')) {" +
            "if (typeof hideAbout === 'function') hideAbout();" +
            "return 'handled';" +
            "}" +

            "return 'none';" +
            "})();",

            value -> {

                if (!"\"none\"".equals(value)
                        && !"none".equals(value)) {
                    return;
                }

                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    MainActivity.super.onBackPressed();
                }
            }
        );
    }

    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
