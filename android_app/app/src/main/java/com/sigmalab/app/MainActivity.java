package com.sigmalab.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.onesignal.OneSignal;
import com.onesignal.Continue;
import com.onesignal.debug.LogLevel;

public class MainActivity extends AppCompatActivity {

    // ðŸŒŸ Ø±Ø§Ø¨Ø· Ù…ÙˆÙ‚Ø¹ Ù…Ø¹Ù…Ù„ Ø³ÙŠØ¬Ù…Ø§ Ø§Ù„Ù…Ø¨Ø§Ø´Ø± Ø¹Ù„Ù‰ Ø§Ù„Ø§Ø³ØªØ¶Ø§ÙØ©
    private static final String DEFAULT_URL = "https://sigma.great-site.net/";
    private static final String WHATSAPP_NUMBER = "201028005992";
    private static final String NOTIF_CHANNEL_ID = "sigma_labs_notifications";

    // ðŸŒŸ OneSignal Cloud Push Settings (Ø³Ø­Ø§Ø¨ÙŠ Ù„Ø¥ÙŠÙ‚Ø§Ø¸ Ø§Ù„Ù‡Ø§ØªÙ Ø­ØªÙ‰ Ù„Ùˆ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ Ù…ØºÙ„Ù‚ ØªÙ…Ø§Ù…Ø§Ù‹)
    private static final String ONESIGNAL_APP_ID = "4517bc46-7440-40ae-85cd-0ea006df919b";

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private RelativeLayout offlineContainer;
    private ImageButton btnBack;
    private ImageButton btnRefresh;
    private Button btnRetryOffline;
    private Button btnWhatsappOffline;

    private ValueCallback<Uri[]> fileUploadCallback;
    private final static int FILE_CHOOSER_REQUEST_CODE = 1001;

    private boolean isOfflineState = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize Views
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        offlineContainer = findViewById(R.id.offlineContainer);
        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnRetryOffline = findViewById(R.id.btnRetryOffline);
        btnWhatsappOffline = findViewById(R.id.btnWhatsappOffline);

        // 2. Setup Listeners
        btnBack.setOnClickListener(v -> handleBackAction());
        btnRefresh.setOnClickListener(v -> reloadCurrentPage());
        btnRetryOffline.setOnClickListener(v -> reloadCurrentPage());
        btnWhatsappOffline.setOnClickListener(v -> openWhatsApp());

        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent);
        swipeRefresh.setOnRefreshListener(this::reloadCurrentPage);

        // 3. Create Notification Channel
        createNotificationChannel();

        // 4. Configure WebView & Cache & Cookies
        setupWebView();

        // 5. Initial Load (or load target url if launched from notification)
        String initialUrl = DEFAULT_URL;
        if (getIntent() != null && getIntent().hasExtra("target_url")) {
            String passedUrl = getIntent().getStringExtra("target_url");
            if (passedUrl != null && !passedUrl.isEmpty()) {
                initialUrl = passedUrl;
            }
        }
        loadTargetUrl(initialUrl);

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 102);
            }
        }

        // 6. Start Periodic Background Notification Sync (ÙŠØ¹Ù…Ù„ Ø­ØªÙ‰ Ù„Ùˆ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ Ù…ØºÙ„Ù‚ ÙƒØ¨Ø¯ÙŠÙ„ Ù…Ø­Ù„ÙŠ)
        NotificationSyncReceiver.schedulePeriodicSync(this);

        // 7. Request OneSignal Push Permission & Setup Click Listener
        try {
            OneSignal.getNotifications().requestPermission(true, Continue.none());
            OneSignal.getNotifications().addClickListener(event -> {
                try {
                    if (event != null && event.getNotification() != null) {
                        String launchUrl = event.getNotification().getLaunchURL();
                        if (launchUrl != null && !launchUrl.isEmpty()) {
                            runOnUiThread(() -> loadTargetUrl(launchUrl));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra("target_url")) {
            String url = intent.getStringExtra("target_url");
            if (url != null && !url.isEmpty()) {
                loadTargetUrl(url);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Ø¥Ø´Ø¹Ø§Ø±Ø§Øª Ù…Ø¹Ø§Ù…Ù„ Ø³ÙŠØ¬Ù…Ø§ Ù„Ù„ØªØ­Ø§Ù„ÙŠÙ„ Ø§Ù„Ø·Ø¨ÙŠØ©";
            String description = "Ù†ØªØ§Ø¦Ø¬ Ø§Ù„ØªØ­Ø§Ù„ÙŠÙ„ ÙˆØ§Ù„Ø¹Ø±ÙˆØ¶ ÙˆØªÙ†Ø¨ÙŠÙ‡Ø§Øª Ø§Ù„Ø­Ø¬ÙˆØ²Ø§Øª Ø§Ù„Ø·Ø¨ÙŠØ©";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NOTIF_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        // Allow mixed content & ensure images always load properly
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Clean User-Agent to avoid host bot-check blocking images
        String userAgent = settings.getUserAgentString();
        if (userAgent != null && userAgent.contains("; wv")) {
            settings.setUserAgentString(userAgent.replace("; wv", ""));
        }

        // Session & Cookie Persistence (Ø­ÙØ¸ Ø¬Ù„Ø³Ø© ÙˆÙ„ÙŠ Ø§Ù„Ø£Ù…Ø± ÙˆØ§Ù„Ù…Ø¹Ù„Ù…Ø§Øª)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Cache Configuration
        if (isNetworkAvailable()) {
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        } else {
            settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }

        // Custom WebViewClient with Maintenance & Offline Interception
        webView.setWebViewClient(new CustomWebViewClient());

        // Custom WebChromeClient for Progress & File Uploads (Camera / Photos)
        webView.setWebChromeClient(new CustomWebChromeClient());

        // Native Notification Bridge (Ø±Ø¨Ø· Ø¥Ø´Ø¹Ø§Ø±Ø§Øª ÙˆØ®Ø¯Ù…Ø§Øª Ø§Ù„Ù…ÙˆÙ‚Ø¹ Ø¨Ù†Ø¸Ø§Ù… Ø§Ù„Ø£Ù†Ø¯Ø±ÙˆÙŠØ¯)
        NotificationBridge bridge = new NotificationBridge();
        webView.addJavascriptInterface(bridge, "AndroidNotificationBridge");
        webView.addJavascriptInterface(bridge, "Android");
    }

    // Native Bridge class called from JavaScript
    public class NotificationBridge {
        @android.webkit.JavascriptInterface
        public void reloadApp() {
            runOnUiThread(() -> reloadCurrentPage());
        }

        @android.webkit.JavascriptInterface
        public void openWhatsApp() {
            runOnUiThread(() -> MainActivity.this.openWhatsApp());
        }

        @android.webkit.JavascriptInterface
        public void callHotline(String number) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + (number != null && !number.isEmpty() ? number : "19736")));
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void showNotification(String title, String message, String targetUrl) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                    if (targetUrl != null && !targetUrl.isEmpty()) {
                        String fullUrl = targetUrl.startsWith("http") ? targetUrl : DEFAULT_URL + targetUrl;
                        intent.putExtra("target_url", fullUrl);
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            MainActivity.this,
                            (int) System.currentTimeMillis(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );

                    NotificationCompat.Builder builder =
                            new NotificationCompat.Builder(MainActivity.this, NOTIF_CHANNEL_ID)
                                    .setSmallIcon(R.drawable.logo_sigma)
                                    .setContentTitle(title)
                                    .setContentText(message)
                                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setAutoCancel(true)
                                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                                    .setContentIntent(pendingIntent);

                    NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(MainActivity.this);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                        }
                    } else {
                        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void syncUserSession(String cookies, String userType, String accountId) {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("sigma_labs_prefs", Context.MODE_PRIVATE);
                prefs.edit()
                        .putString("user_cookie", cookies != null ? cookies : "")
                        .putString("user_type", userType != null ? userType : "")
                        .putString("account_id", accountId != null ? accountId : "")
                        .apply();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @android.webkit.JavascriptInterface
        public void syncOneSignal(String appId, String externalId) {
            runOnUiThread(() -> {
                try {
                    if (externalId != null && !externalId.trim().isEmpty()) {
                        OneSignal.login(externalId.trim());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void logoutOneSignal() {
            runOnUiThread(() -> {
                try {
                    OneSignal.logout();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void loadTargetUrl(String url) {
        if (!isNetworkAvailable()) {
            showOfflineScreen();
            return;
        }

        hideOfflineScreen();
        webView.loadUrl(url);
    }

    private void reloadCurrentPage() {
        if (!isNetworkAvailable()) {
            showOfflineScreen();
            swipeRefresh.setRefreshing(false);
            return;
        }

        hideOfflineScreen();
        webView.loadUrl(DEFAULT_URL);
    }

    private void showOfflineScreen() {
        isOfflineState = true;
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        // ØªØ­Ù…ÙŠÙ„ ØµÙØ­Ø© Ø§Ù„ØµÙŠØ§Ù†Ø© Ø§Ù„ÙØ§Ø®Ø±Ø© Ø§Ù„Ù…Ø­ÙÙˆØ¸Ø© Ù…Ø­Ù„ÙŠØ§Ù‹ Ø¯Ø§Ø®Ù„ Ø§Ù„Ù€ APK Ø¨Ø¯ÙˆÙ† Ø§Ù„Ø­Ø§Ø¬Ø© Ù„Ù„Ø¥Ù†ØªØ±Ù†Øª
        webView.loadUrl("file:///android_asset/offline.html");
        webView.setVisibility(View.VISIBLE);
        offlineContainer.setVisibility(View.GONE);
    }

    private void hideOfflineScreen() {
        isOfflineState = false;
        offlineContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void handleBackAction() {
        if (isOfflineState) {
            reloadCurrentPage();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    private void openWhatsApp() {
        try {
            String url = "https://api.whatsapp.com/send?phone=" + WHATSAPP_NUMBER +
                    "&text=" + Uri.encode("Ø§Ù„Ø³Ù„Ø§Ù… Ø¹Ù„ÙŠÙƒÙ….. Ø£Ø³ØªÙØ³Ø± Ø¨Ø®ØµÙˆØµ Ø®Ø¯Ù…Ø§Øª ÙˆÙ…Ø¹Ø§Ù…Ù„ Ø³ÙŠØ¬Ù…Ø§ Ù„Ù„ØªØ­Ø§Ù„ÙŠÙ„ Ø§Ù„Ø·Ø¨ÙŠØ©");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "ØªØ·Ø¨ÙŠÙ‚ ÙˆØ§ØªØ³Ø§Ø¨ ØºÙŠØ± Ù…Ø«Ø¨Øª Ø¹Ù„Ù‰ Ø¬Ù‡Ø§Ø²Ùƒ", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Custom WebViewClient
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private class CustomWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(10);
            hideOfflineScreen();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
        }

        // Catch connection errors on modern Android (API 23+)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                showOfflineScreen();
            }
        }

        // Legacy error handler
        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            showOfflineScreen();
        }

        // Catch HTTP 500, 502, 503, 504 server errors (ØªØ­Øª Ø§Ù„ØµÙŠØ§Ù†Ø©)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame() && errorResponse != null) {
                int status = errorResponse.getStatusCode();
                if (status >= 500) {
                    showOfflineScreen();
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            return handleExternalUrls(url);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleExternalUrls(url);
        }

        private boolean handleExternalUrls(String url) {
            // Handle tel:, mailto:, whatsapp: links in external apps
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.startsWith("https://wa.me/")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            return false;
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Custom WebChromeClient
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private class CustomWebChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
            if (newProgress >= 100) {
                progressBar.setVisibility(View.GONE);
            }
        }

        // Support for File upload / Camera photos
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = filePathCallback;

            Intent intent = fileChooserParams.createIntent();
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
            } catch (ActivityNotFoundException e) {
                fileUploadCallback = null;
                Toast.makeText(MainActivity.this, "ØªØ¹Ø°Ø± ÙØªØ­ Ø§Ù„Ù…Ø¹Ø±Ø¶ Ø£Ùˆ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§", Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }
}
