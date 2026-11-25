
package com.masoud.library;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import org.json.JSONObject;
import org.json.JSONArray;
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
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    // Version-aware update banner release. Camera permission is bridged to Android runtime consent.

    private WebView webView;
    private WebView popupWebView;
    private boolean offlineFallbackLoaded = false;
    private volatile boolean trustedLocalPageLoaded = false;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> filePathCallback;
    private TextToSpeech textToSpeech;
    private volatile boolean textToSpeechReady = false;
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

        // Initialize Android's native Persian text-to-speech engine for offline tool pages.
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int languageStatus = textToSpeech.setLanguage(new Locale("fa", "IR"));
                if (languageStatus == TextToSpeech.LANG_MISSING_DATA
                        || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                    languageStatus = textToSpeech.setLanguage(new Locale("fa"));
                }
                textToSpeechReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                        && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
            } else {
                textToSpeechReady = false;
            }
        });

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

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (view == webView) trustedLocalPageLoaded = isTrustedLocalOrigin(url);
                super.onPageStarted(view, url, favicon);
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
                if (isLibraryWebsiteUrl(url)) offlineFallbackLoaded = false;
                if (view == webView) trustedLocalPageLoaded = isTrustedLocalOrigin(url);
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
                if (uri == null) return true;
                if (isTrustedLocalOrigin(uri.toString()) || isLibraryWebsiteUrl(uri.toString())) return false;
                openExternalUrl(uri);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            // Bridge WebView camera permission requests to Android's runtime permission dialog.
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    Uri origin = request.getOrigin();
                    boolean trustedOrigin = isTrustedLocalPermissionOrigin(origin == null ? null : origin.toString());
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
                if (!isTrustedLocalPermissionOrigin(origin)) {
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

    private boolean isTrustedLocalOrigin(String rawUrl) {
        if (rawUrl == null) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())
                    && path != null && path.startsWith("/assets/offline/");
        } catch (Exception ignored) { return false; }
    }

    private boolean isTrustedLocalPermissionOrigin(String rawOrigin) {
        if (rawOrigin == null) return false;
        try {
            Uri uri = Uri.parse(rawOrigin);
            String path = uri.getPath();
            boolean trustedAssetHost =
                    "https".equalsIgnoreCase(uri.getScheme())
                    && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost());
            // WebView permission origins can include the requesting iframe's asset path.
            // Accept only the library's packaged offline assets, never arbitrary remote origins.
            boolean trustedAssetPath = path == null || path.isEmpty() || "/".equals(path)
                    || path.startsWith("/assets/offline/");
            return trustedAssetHost && trustedAssetPath;
        } catch (Exception ignored) { return false; }
    }

    private boolean isLibraryWebsiteUrl(String rawUrl) {
        if (rawUrl == null) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "masoud73d.github.io".equalsIgnoreCase(uri.getHost())
                    && path != null && (path.equals("/library") || path.startsWith("/library/"));
        } catch (Exception ignored) { return false; }
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
        public String saveBase64File(String base64, String fileName, String mimeType) {
            if (!trustedLocalPageLoaded) return "ERROR:UNTRUSTED";
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "ERROR:UNSUPPORTED";
            try {
                if (base64 == null || base64.length() > 40_000_000) return "ERROR:INVALID_DATA";
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                String safeName = (fileName == null ? "Library-file" : fileName)
                        .replaceAll("[^A-Za-z0-9._-]", "_");
                if (safeName.isEmpty()) safeName = "Library-file";
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                        mimeType == null ? "application/octet-stream" : mimeType);
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Library");
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return "ERROR:CREATE";
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new java.io.IOException("Cannot open output");
                    output.write(bytes);
                    output.flush();
                }
                android.content.ContentValues done = new android.content.ContentValues();
                done.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
                return "OK";
            } catch (Exception error) {
                return "ERROR:" + error.getClass().getSimpleName();
            }
        }

        @JavascriptInterface
        public String getNativeDeviceInfo() {
            if (!trustedLocalPageLoaded) return "{}";
            try {
                JSONObject info = new JSONObject();

                info.put("nativeSource", "Android");

                // Enumerate actual hardware sensors through Android's SensorManager.
                JSONArray sensorArray = new JSONArray();
                android.hardware.SensorManager sensorManager =
                        (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
                android.hardware.Sensor lightSensor = null;
                android.hardware.Sensor proximitySensor = null;
                if (sensorManager != null) {
                    // Use Android's canonical sensor constants and query default sensors
                    // separately so OEM-specific implementations are not missed by UI mapping.
                    lightSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT);
                    proximitySensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY);
                    for (android.hardware.Sensor sensor :
                            sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)) {
                        JSONObject sensorInfo = new JSONObject();
                        sensorInfo.put("type", sensor.getType());
                        sensorInfo.put("name", sensor.getName());
                        sensorInfo.put("vendor", sensor.getVendor());
                        sensorInfo.put("version", sensor.getVersion());
                        sensorInfo.put("maxRange", sensor.getMaximumRange());
                        sensorInfo.put("resolution", sensor.getResolution());
                        sensorInfo.put("power", sensor.getPower());
                        sensorInfo.put("wakeUp", sensor.isWakeUpSensor());
                        sensorInfo.put("reportingMode", sensor.getReportingMode());
                        sensorArray.put(sensorInfo);
                    }
                }
                info.put("sensors", sensorArray);
                info.put("hasLightSensor", lightSensor != null);
                info.put("lightSensorName", lightSensor == null ? "" : lightSensor.getName());
                info.put("hasProximitySensor", proximitySensor != null);
                info.put("proximitySensorName", proximitySensor == null ? "" : proximitySensor.getName());
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
                // Read the design capacity exposed by common Android power-supply driver paths.
                // Many manufacturers restrict these sysfs values; -1 means unavailable.
                info.put("batteryCapacityMah", readBatteryDesignCapacityMah());

                return info.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        private int readBatteryDesignCapacityMah() {
            // First ask Android's framework power profile for manufacturer-provided design capacity.
            // design capacity. This can work on devices that restrict direct sysfs access.
            try {
                Class<?> profileClass = Class.forName("com.android.internal.os.PowerProfile");
                java.lang.reflect.Constructor<?> constructor =
                        profileClass.getConstructor(android.content.Context.class);
                Object profile = constructor.newInstance(MainActivity.this);
                Method getCapacity = profileClass.getMethod("getBatteryCapacity");
                Object rawCapacity = getCapacity.invoke(profile);
                if (rawCapacity instanceof Number) {
                    double value = ((Number) rawCapacity).doubleValue();
                    if (value >= 500 && value <= 30000) return (int) Math.round(value);
                }
            } catch (Exception ignored) { }

            String[] chargePaths = {
                    "/sys/class/power_supply/battery/charge_full_design",
                    "/sys/class/power_supply/Battery/charge_full_design",
                    "/sys/class/power_supply/bms/charge_full_design"
            };
            for (String path : chargePaths) {
                try (java.io.BufferedReader reader =
                             new java.io.BufferedReader(new java.io.FileReader(path))) {
                    String raw = reader.readLine();
                    if (raw == null) continue;
                    long value = Long.parseLong(raw.trim());
                    long mah = value > 100000 ? Math.round(value / 1000.0) : value;
                    if (mah >= 500 && mah <= 30000) return (int) mah;
                } catch (Exception ignored) { }
            }

            // Some kernels expose design energy (uWh) rather than charge (uAh).
            // Convert using the design voltage (uV); never substitute current
            // full-charge capacity because it is not the battery's design rating.
            String[] energyPaths = {
                    "/sys/class/power_supply/battery/energy_full_design",
                    "/sys/class/power_supply/Battery/energy_full_design",
                    "/sys/class/power_supply/bms/energy_full_design"
            };
            String[] voltagePaths = {
                    "/sys/class/power_supply/battery/voltage_max_design",
                    "/sys/class/power_supply/Battery/voltage_max_design",
                    "/sys/class/power_supply/bms/voltage_max_design"
            };
            for (String energyPath : energyPaths) {
                try {
                    long energyUwh;
                    try (java.io.BufferedReader reader =
                                 new java.io.BufferedReader(new java.io.FileReader(energyPath))) {
                        String raw = reader.readLine();
                        if (raw == null) continue;
                        energyUwh = Long.parseLong(raw.trim());
                    }
                    for (String voltagePath : voltagePaths) {
                        try (java.io.BufferedReader reader =
                                     new java.io.BufferedReader(new java.io.FileReader(voltagePath))) {
                            String raw = reader.readLine();
                            if (raw == null) continue;
                            double voltageV = Long.parseLong(raw.trim()) / 1000000.0;
                            if (voltageV < 2.0 || voltageV > 6.0) continue;
                            long mah = Math.round((energyUwh / 1000.0) / voltageV);
                            if (mah >= 500 && mah <= 30000) return (int) mah;
                        }
                    }
                } catch (Exception ignored) { }
            }
            return -1;
        }

        private String firstNonEmpty(String first, String second) {
            return first != null && !first.trim().isEmpty() ? first : (second == null ? "" : second);
        }

        @JavascriptInterface
        public boolean isNetworkAvailable() {
            return trustedLocalPageLoaded && MainActivity.this.isNetworkAvailable();
        }

        @JavascriptInterface
        public boolean speakText(String text) {
            if (!trustedLocalPageLoaded || text == null || text.length() > 10000
                    || !textToSpeechReady || textToSpeech == null) {
                return false;
            }
            runOnUiThread(() -> {
                if (textToSpeechReady && textToSpeech != null) {
                    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "library_read_text");
                }
            });
            return true;
        }

        @JavascriptInterface
        public String getAppVersion() {
            if (!trustedLocalPageLoaded) return "";
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
            if (trustedLocalPageLoaded) runOnUiThread(() -> finish());
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

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
            textToSpeechReady = false;
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
