
package com.masoud.library;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Message;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.webkit.GeolocationPermissions;

public class MainActivity extends Activity {

    private WebView webView;
    private WebView popupWebView;
    private ValueCallback<Uri[]> filePathCallback;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private static final String WEBSITE_URL =
            "https://masoud73d.github.io/library/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
        }

        window.setStatusBarColor(0xFF160A06);
        window.setNavigationBarColor(0xFF160A06);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF160A06);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidApp");

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
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            // Allow the weather page to request Android location permission through WebView.
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (origin == null || !origin.startsWith("https://masoud73d.github.io")) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        || (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
                }
            }

            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean isDialog,
                    boolean isUserGesture,
                    Message resultMsg) {

                WebView popup = new WebView(MainActivity.this);
                popupWebView = popup;

                WebSettings popupSettings = popup.getSettings();
                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);

                popup.setWebViewClient(new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        openExternalUrl(request.getUrl());
                        closePopup(view);
                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url) {

                        openExternalUrl(Uri.parse(url));
                        closePopup(view);
                        return true;
                    }
                });

                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;

                transport.setWebView(popup);
                resultMsg.sendToTarget();

                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                closePopup(window);
            }

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

        webView.loadUrl(WEBSITE_URL + "?refresh=" + System.currentTimeMillis());
    }

    private void openExternalUrl(Uri uri) {
        if (uri == null) {
            return;
        }

        String scheme = uri.getScheme();

        if (scheme == null ||
                (!scheme.equals("http") && !scheme.equals("https"))) {
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closePopup(WebView popup) {
        if (popup != null) {
            popup.stopLoading();
            popup.setWebViewClient(null);
            popup.destroy();
        }

        if (popupWebView == popup) {
            popupWebView = null;
        }
    }

    private class AndroidBridge {

        @JavascriptInterface
        public String getAppVersion() {
            try {
                android.content.pm.PackageInfo packageInfo =
                        getPackageManager().getPackageInfo(getPackageName(), 0);
                return packageInfo.versionName != null ? packageInfo.versionName : "1.0.0";
            } catch (Exception e) {
                return "1.0.0";
            }
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> finish());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && pendingGeoCallback != null) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            }
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE
                && filePathCallback != null) {

            Uri[] results =
                    WebChromeClient.FileChooserParams.parseResult(
                            resultCode,
                            data
                    );

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView == null) {
            super.onBackPressed();
            return;
        }

        webView.evaluateJavascript(
            "(function() {" +

            "var app=document.getElementById('appOverlay');" +
            "var list=document.getElementById('listOverlay');" +
            "var about=document.getElementById('aboutOverlay');" +

            "if(app&&app.classList.contains('show')){" +
            "if(typeof closeApp==='function')closeApp();" +
            "return 'handled';}" +

            "if(list&&list.classList.contains('show')){" +
            "if(typeof hideList==='function')hideList();" +
            "return 'handled';}" +

            "if(about&&about.classList.contains('show')){" +
            "if(typeof hideAbout==='function')hideAbout();" +
            "return 'handled';}" +

            "return 'none';})();",

            value -> {

                if (!"\"none\"".equals(value)
                        && !"none".equals(value)) {
                    return;
                }

                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
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

        if (popupWebView != null) {
            popupWebView.stopLoading();
            popupWebView.destroy();
            popupWebView = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidApp");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
