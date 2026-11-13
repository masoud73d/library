
package com.masoud.library;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {

    private WebView webView;

    private static final String WEBSITE_URL =
            "https://masoud73d.github.io/library/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        // تنظیمات اصلی نمایش صفحات
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // تنظیم مقیاس و اندازه صفحه
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // دریافت نسخه جدید صفحات
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);
        webView.clearHistory();

        // دسترسی فایل‌ها
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

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

                if (url.startsWith("http://") ||
                    url.startsWith("https://")) {
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

        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl(WEBSITE_URL);
    }

    @Override
    protected void onDestroy() {

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
