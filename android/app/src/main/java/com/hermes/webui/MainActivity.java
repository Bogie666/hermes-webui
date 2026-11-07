package com.hermes.webui;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Arrays;

/** Thin same-origin client. All agent execution and chat state remain on the server. */
public class MainActivity extends Activity {
    private static final int FILES = 10, AUDIO = 11;
    private WebView web;
    private LinearLayout root;
    private FrameLayout content;
    private String server;
    private ValueCallback<Uri[]> files;
    private PermissionRequest audio;
    private boolean choosingServer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,9,8));
        // Respect system bars and IME on Android 15's enforced edge-to-edge layout.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout bar = new LinearLayout(this);
        TextView title = new TextView(this);
        title.setText("Hermes"); title.setTextSize(18); title.setPadding(dp(16),0,0,0);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button menu = new Button(this); menu.setText("⋮"); menu.setContentDescription("Connection options");
        menu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, menu);
            popup.getMenu().add("Reload"); popup.getMenu().add("Change server");
            popup.getMenu().add("Clear login and local data");
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Reload": if (server != null) openServer(); break;
                    case "Change server": showSetup(); break;
                    default: new AlertDialog.Builder(this).setTitle("Clear login and local data?")
                        .setMessage("This clears this app's cookies and drafts. Server conversations remain available.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Clear", (d,w) -> clearData()).show();
                }
                return true;
            }); popup.show();
        });
        bar.addView(menu, new LinearLayout.LayoutParams(dp(56),dp(48)));
        root.addView(bar);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        server = getPreferences(MODE_PRIVATE).getString("server", null);
        if (server == null) showSetup(); else openServer();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void notice(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    private void cancelPending() {
        if (files != null) { files.onReceiveValue(null); files = null; }
        if (audio != null) { audio.deny(); audio = null; }
    }
    private void destroyWeb() {
        cancelPending();
        if (web != null) { content.removeView(web); web.stopLoading(); web.destroy(); web = null; }
    }
    private void showSetup() {
        destroyWeb(); choosingServer = true; content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(32), dp(24), dp(24));
        TextView heading = new TextView(this); heading.setText("Connect to Hermes"); heading.setTextSize(26);
        form.addView(heading);
        TextView help = new TextView(this);
        help.setText("Enter your Hermes WebUI HTTPS address. Hermes continues running on your computer or server. For private access, connect Tailscale on this phone first.");
        help.setPadding(0,dp(16),0,dp(24)); form.addView(help);
        EditText address = new EditText(this); address.setHint("https://hermes.example.com/");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setSingleLine(true); if (server != null) address.setText(server); form.addView(address);
        Button connect = new Button(this); connect.setText("Connect"); form.addView(connect);
        connect.setOnClickListener(v -> {
            try {
                String next = ServerUrl.normalize(address.getText().toString());
                if (server != null && !next.equals(server)) {
                    new AlertDialog.Builder(this).setTitle("Change server?")
                        .setMessage("Local login and drafts will be cleared before connecting to the new server.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Connect",(d,w) -> {
                            server = next; clearData();
                        }).show();
                } else { server = next; openServer(); }
            } catch (IllegalArgumentException e) { address.setError("Enter a valid HTTPS URL without login credentials, query or fragment."); }
        });
        scroll.addView(form); content.addView(scroll);
    }
    private void clearData() {
        destroyWeb(); WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(ok -> {
            CookieManager.getInstance().flush(); if (!isFinishing() && !isDestroyed()) {
                if (server == null) showSetup(); else openServer();
            }
        });
    }
    @SuppressWarnings("deprecation")
    private void openServer() {
        destroyWeb(); choosingServer = false; content.removeAllViews();
        getPreferences(MODE_PRIVATE).edit().putString("server",server).apply();
        web = new WebView(this);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setGeolocationEnabled(false); s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (ServerUrl.sameOrigin(server,r.getUrl().toString())) return false;
                if (r.isForMainFrame() && r.hasGesture()) external(r.getUrl());
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
                if (audio != null) { audio.deny(); audio = null; }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) connectionError();
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response) {
                if (r.isForMainFrame() && response.getStatusCode() >= 500) connectionError();
            }
            // Default SSL handling cancels invalid certificates. Never bypass TLS errors.
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params) {
                if (!ServerUrl.sameOrigin(server,v.getUrl())) return false;
                if (files != null) files.onReceiveValue(null);
                files = cb;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                String[] types = Arrays.stream(params.getAcceptTypes()).filter(t -> t.contains("/")).toArray(String[]::new);
                if (types.length > 0) pick.putExtra(Intent.EXTRA_MIME_TYPES,types);
                try { startActivityForResult(pick,FILES); }
                catch (ActivityNotFoundException e) { files.onReceiveValue(null); files = null; notice("No file picker available."); }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                if (!ServerUrl.sameOrigin(server,request.getOrigin().toString())
                        || !ServerUrl.sameOrigin(server,web.getUrl())
                        || !Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    request.deny(); return;
                }
                if (audio != null) audio.deny(); audio = request;
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) grantAudio();
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (audio == request) audio = null;
            }
        });
        web.setDownloadListener((url,ua,disposition,mime,length) -> {
            if (!ServerUrl.sameOrigin(server,url)) { notice("Only downloads from your Hermes server are supported."); return; }
            new AlertDialog.Builder(this).setTitle("Download file?").setMessage("Save this file to Downloads?")
                .setNegativeButton("Cancel",null).setPositiveButton("Download",(dialog,which) -> {
                    try {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (cookie != null) request.addRequestHeader("Cookie",cookie);
                        request.addRequestHeader("User-Agent",ua);
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                                URLUtil.guessFileName(url,disposition,mime));
                        ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                        notice("Download started.");
                    } catch (RuntimeException e) { notice("Download failed. Try opening the file in your browser."); }
                }).show();
        });
        content.addView(web,new FrameLayout.LayoutParams(-1,-1)); web.loadUrl(server);
    }
    private void grantAudio() {
        if (audio == null) return;
        PermissionRequest pending = audio; audio = null;
        if (web != null && ServerUrl.sameOrigin(server,web.getUrl())
                && ServerUrl.sameOrigin(server,pending.getOrigin().toString())
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            pending.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else pending.deny();
    }
    private void connectionError() {
        // Keep the WebView so retry is explicit and never replays a chat POST.
        if (isFinishing() || isDestroyed()) return;
        notice("Cannot reach Hermes. Check your server and Tailscale, then use Connection options → Reload.");
    }
    private void external(Uri uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return;
        new AlertDialog.Builder(this).setTitle("Open in browser?").setMessage(uri.getHost())
            .setNegativeButton("Cancel",null).setPositiveButton("Open",(d,w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
                catch (ActivityNotFoundException e) { notice("No browser available."); }
            }).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code == AUDIO) grantAudio();
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if (code != FILES || files == null) return;
        ArrayList<Uri> selected = new ArrayList<>();
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) for (int i=0;i<data.getClipData().getItemCount();i++)
                selected.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) selected.add(data.getData());
        }
        // The system document picker returns content URIs; reject file:// and other schemes.
        selected.removeIf(u -> !"content".equals(u.getScheme()));
        files.onReceiveValue(selected.isEmpty() ? null : selected.toArray(new Uri[0])); files = null;
    }
    @Override public void onBackPressed() {
        if (choosingServer && server != null) openServer();
        else if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onPause() {
        super.onPause(); if (web != null) web.onPause(); CookieManager.getInstance().flush();
        if (audio != null) { audio.deny(); audio = null; }
    }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { destroyWeb(); super.onDestroy(); }
}
