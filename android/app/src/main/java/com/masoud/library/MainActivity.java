
package com.masoud.library;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import org.json.JSONObject;
import java.lang.reflect.Method;
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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    // Version-aware update banner release. Camera permission is bridged to Android runtime consent.

    private WebView webView;
    private WebView popupWebView;
    private boolean offlineFallbackLoaded = false;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private PermissionRequest pendingCameraPermissionRequest;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1003;
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

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

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
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()
                        && request.getUrl() != null
                        && (request.getUrl().getScheme().equals("http")
                            || request.getUrl().getScheme().equals("https"))
                        && !offlineFallbackLoaded) {
                    offlineFallbackLoaded = true;
                    view.loadUrl("https://appassets.androidplatform.net/assets/offline/index.html");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith(WEBSITE_URL)) {
                    offlineFallbackLoaded = false;
                }
            }

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
                        || url.startsWith("https://")
                        || url.startsWith("file:///android_asset/")) {
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

            // Bridge WebView camera permission requests to Android's runtime permission dialog.
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    Uri origin = request.getOrigin();
                    String host = origin == null ? "" : origin.getHost();
                    boolean trustedOrigin = "appassets.androidplatform.net".equals(host)
                            || "masoud73d.github.io".equals(host);
                    boolean asksForCamera = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            asksForCamera = true;
                            break;
                        }
                    }

                    if (!trustedOrigin || !asksForCamera) {
                        request.deny();
                        return;
                    }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                            || checkSelfPermission(android.Manifest.permission.CAMERA)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        if (pendingCameraPermissionRequest != null) {
                            pendingCameraPermissionRequest.deny();
                        }
                        pendingCameraPermissionRequest = request;
                        requestPermissions(
                                new String[]{android.Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_REQUEST_CODE
                        );
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingCameraPermissionRequest == request) {
                    pendingCameraPermissionRequest = null;
                }
            }

            // Allow the weather page to request Android location permission through WebView.
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (origin == null || !(origin.startsWith("https://masoud73d.github.io")
                        || origin.startsWith("https://appassets.androidplatform.net"))) {
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

        offlineFallbackLoaded = true;
        webView.loadUrl("https://appassets.androidplatform.net/assets/offline/index.html");
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network active = cm.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            return false;
        }
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

    // Read-only Android device information bridge for the system information tool.
    private String readSystemProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private class AndroidBridge {

        @JavascriptInterface
        public String getCameraHardwareInfo() {
            org.json.JSONArray result = new org.json.JSONArray();
            try {
                CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
                if (manager == null) return result.toString();
                for (String logicalId : manager.getCameraIdList()) {
                    CameraCharacteristics logical = manager.getCameraCharacteristics(logicalId);

                    // Always retain the logical camera (including the front camera).
                    result.put(cameraInfo(logical, logicalId, logicalId, false));

                    // Physical IDs are supplementary sensor records, not replacements.
                    if (Build.VERSION.SDK_INT >= physicalIdsApiLevel()) {
                        java.util.Set<String> physicalIds = logical.getPhysicalCameraIds();
                        if (physicalIds != null) {
                            for (String physicalId : physicalIds) {
                                if (physicalId == null || physicalId.equals(logicalId)) continue;
                                try {
                                    result.put(cameraInfo(
                                            manager.getCameraCharacteristics(physicalId),
                                            physicalId, logicalId, true
                                    ));
                                } catch (Exception ignored) { }
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }
            return result.toString();
        }

        private int physicalIdsApiLevel() { return Build.VERSION_CODES.P; }

        private JSONObject cameraInfo(CameraCharacteristics c, String id, String parent, boolean physical) throws Exception {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("logicalCameraId", parent);
            item.put("physical", physical);
            item.put("cameraType", physical ? "physical" : "logical");
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            item.put("facing", facing == null ? -1 : facing);
            Size pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            if (pixels != null) {
                item.put("width", pixels.getWidth());
                item.put("height", pixels.getHeight());
                item.put("megapixels", Math.round((pixels.getWidth() * (double)pixels.getHeight()) / 100000.0) / 10.0);
            }
            float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            org.json.JSONArray focalArray = new org.json.JSONArray();
            if (focal != null) for (float value : focal) focalArray.put(Math.round(value * 100.0) / 100.0);
            item.put("focalLengthsMm", focalArray);
            android.util.SizeF sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (sensor != null) {
                item.put("sensorWidthMm", Math.round(sensor.getWidth() * 100.0) / 100.0);
                item.put("sensorHeightMm", Math.round(sensor.getHeight() * 100.0) / 100.0);
            }
            return item;
        }

        @JavascriptInterface
        public String getNativeDeviceInfo() {
            try {
                JSONObject info = new JSONObject();

                info.put("nativeSource", "Android");
                info.put("manufacturer", Build.MANUFACTURER);
                info.put("brand", Build.BRAND);
                info.put("model", Build.MODEL);
                info.put("device", Build.DEVICE);
                info.put("product", Build.PRODUCT);
                info.put("hardware", Build.HARDWARE);
                info.put("display", Build.DISPLAY);
                info.put("buildNumber", Build.VERSION.INCREMENTAL);
                info.put("androidVersion", Build.VERSION.RELEASE);
                info.put("apiLevel", Build.VERSION.SDK_INT);
                info.put("securityPatch", Build.VERSION.SECURITY_PATCH);
                info.put("romVersion", firstNonEmpty(
                        readSystemProperty("ro.mi.os.version.name"),
                        readSystemProperty("ro.miui.ui.version.name")
                ));
                info.put("supportedAbis", android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS));
                info.put("cpuCores", Runtime.getRuntime().availableProcessors());

                ActivityManager activityManager =
                        (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                if (activityManager != null) {
                    activityManager.getMemoryInfo(memoryInfo);
                    info.put("totalRamBytes", memoryInfo.totalMem);
                    info.put("availableRamBytes", memoryInfo.availMem);
                }

                StatFs storage = new StatFs(Environment.getDataDirectory().getAbsolutePath());
                info.put("totalStorageBytes", storage.getTotalBytes());
                info.put("availableStorageBytes", storage.getAvailableBytes());

                Intent batteryIntent = registerReceiver(
                        null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                );
                if (batteryIntent != null) {
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

                    int percent = (level >= 0 && scale > 0)
                            ? Math.round(level * 100f / scale) : -1;

                    String batteryStatus = "unknown";
                    if (status == BatteryManager.BATTERY_STATUS_CHARGING) batteryStatus = "charging";
                    else if (status == BatteryManager.BATTERY_STATUS_FULL) batteryStatus = "full";
                    else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) batteryStatus = "discharging";
                    else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) batteryStatus = "not_charging";

                    String pluggedType = "none";
                    if (plugged == BatteryManager.BATTERY_PLUGGED_AC) pluggedType = "ac";
                    else if (plugged == BatteryManager.BATTERY_PLUGGED_USB) pluggedType = "usb";
                    else if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) pluggedType = "wireless";

                    info.put("batteryPercent", percent);
                    info.put("batteryStatus", batteryStatus);
                    info.put("batteryPlugged", pluggedType);
                    info.put("batteryTemperatureC",
                            batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0);
                    info.put("batteryVoltageMv",
                            batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                    info.put("batteryTechnology",
                            batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                }

                BatteryManager batteryManager =
                        (BatteryManager) getSystemService(BATTERY_SERVICE);
                if (batteryManager != null) {
                    int chargeCounter = batteryManager.getIntProperty(
                            BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER
                    );
                    info.put("batteryChargeCounterUah",
                            chargeCounter == Integer.MIN_VALUE ? -1 : chargeCounter);
                }
                // Read the design capacity exposed by the Android power-supply driver.
                // Many manufacturers restrict this sysfs value; -1 means unavailable.
                info.put("batteryCapacityMah", readBatteryDesignCapacityMah());

                return info.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        private int readBatteryDesignCapacityMah() {
            String[] paths = {
                    "/sys/class/power_supply/battery/charge_full_design",
                    "/sys/class/power_supply/Battery/charge_full_design",
                    "/sys/class/power_supply/bms/charge_full_design",
                    "/sys/class/power_supply/battery/charge_full"
            };
            for (String path : paths) {
                try (java.io.BufferedReader reader =
                             new java.io.BufferedReader(new java.io.FileReader(path))) {
                    String raw = reader.readLine();
                    if (raw == null) continue;
                    long value = Long.parseLong(raw.trim());
                    long mah = value > 100000 ? Math.round(value / 1000.0) : value;
                    if (mah >= 500 && mah <= 30000) return (int) mah;
                } catch (Exception ignored) { }
            }
            return -1;
        }

        private String firstNonEmpty(String first, String second) {
            return first != null && !first.trim().isEmpty() ? first : (second == null ? "" : second);
        }

        @JavascriptInterface
        public boolean isNetworkAvailable() {
            return MainActivity.this.isNetworkAvailable();
        }

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
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            PermissionRequest cameraRequest = pendingCameraPermissionRequest;
            pendingCameraPermissionRequest = null;
            boolean granted = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (cameraRequest != null) {
                if (granted) {
                    cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    cameraRequest.deny();
                }
            }
            return;
        }

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
