package im.shimo.react.x5.webview;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebView;

import java.io.File;

public class X5WebViewModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private ReactApplicationContext mReactContext;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadCallbackAboveL;
    private static final int FILE_CHOOSER_PERMISSION_REQUEST = 1;
    private static final int FILE_DOWNLOAD_PERMISSION_REQUEST = 2;

    public void onNewIntent(Intent intent) {
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        // super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (null == mUploadMessage && null == mUploadCallbackAboveL) {
                return;
            }
            Uri result = data == null || resultCode != Activity.RESULT_OK ? null : data.getData();
            if (mUploadCallbackAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data);
            } else if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(result);
                mUploadMessage = null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
        if (requestCode != 1 || mUploadCallbackAboveL == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String dataString = data.getDataString();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }
                if (dataString != null)
                    results = new Uri[]{Uri.parse(dataString)};
            }
        }
        mUploadCallbackAboveL.onReceiveValue(results);
        mUploadCallbackAboveL = null;
    }

    X5WebViewModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        mReactContext = reactContext;
    }

    @NonNull
    @Override
    public String getName() {
        return "X5WebView";
    }

    @ReactMethod
    public void getX5CoreVersion(Callback callback) {
        callback.invoke(WebView.getTbsCoreVersion(mReactContext));
    }

    // method
    @ReactMethod
    public void getAndroidVersion(Promise p) {
        try {
            String version = android.os.Build.VERSION.RELEASE;
            p.resolve(version);
        } catch (Exception e) {
            p.reject(e.toString());
        }
    }

    @ReactMethod
    public void install(String apkFile) {

        String cmd = "chmod 777 " + apkFile;

        try {
            Runtime.getRuntime().exec(cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            // 赋予临时权限
            Uri contentUri = FileProvider.getUriForFile(mReactContext, getActivity().getApplication().getPackageName() + ".fileprovider",
                new File(apkFile));
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        } else {
            // 声明需要的零时权限
            intent.setDataAndType(Uri.parse("file://" + apkFile), "application/vnd.android.package-archive");
        }

        mReactContext.startActivity(intent);

        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private RNX5WebViewPackage aPackage;

    public void setPackage(RNX5WebViewPackage aPackage) {
        this.aPackage = aPackage;
    }

    public RNX5WebViewPackage getPackage() {
        return this.aPackage;
    }

    private Activity getActivity() {
        return getCurrentActivity();
    }

    public void setUploadMessage(ValueCallback<Uri> uploadMessage) {
        mUploadMessage = uploadMessage;
    }

    public void openFileChooserView() {
        try {
            Intent openableFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            openableFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            openableFileIntent.setType("*/*");

            final Intent chooserIntent = Intent.createChooser(openableFileIntent, "Choose File");
            getActivity().startActivityForResult(chooserIntent, 1);
        } catch (Exception e) {
            Log.d("customwebview", e.toString());
        }
    }

    public void setmUploadCallbackAboveL(ValueCallback<Uri[]> mUploadCallbackAboveL) {
        this.mUploadCallbackAboveL = mUploadCallbackAboveL;
    }

    public boolean grantFileChooserPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        boolean result = true;
        if (ContextCompat.checkSelfPermission(this.getActivity(),
            Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            result = false;
        }

        if (!result) {
            PermissionAwareActivity activity = getPermissionAwareActivity();
            activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                FILE_CHOOSER_PERMISSION_REQUEST, webviewFileChooserPermissionListener);
        }
        return result;
    }

    private PermissionListener webviewFileChooserPermissionListener = new PermissionListener() {
        @Override
        public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            switch (requestCode) {
                case FILE_CHOOSER_PERMISSION_REQUEST: {
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        if (mUploadCallbackAboveL != null) {
                            openFileChooserView();
                        }
                    } else {
                        // Toast.makeText(getActivity().getApplicationContext(),
                        // "Cannot upload files as permission was denied. Please provide permission to
                        // access storage, in order to upload files.",
                        // Toast.LENGTH_LONG).show();
                        Log.d("customwebview", "Cannot upload files as permission was denied");

                    }
                    return true;
                }
            }
            return false;
        }
    };

    private PermissionAwareActivity getPermissionAwareActivity() {
        Activity activity = getCurrentActivity();
        if (activity == null) {
            throw new IllegalStateException("Tried to use permissions API while not attached to an " + "Activity.");
        } else if (!(activity instanceof PermissionAwareActivity)) {
            throw new IllegalStateException("Tried to use permissions API but the host Activity doesn't"
                + " implement PermissionAwareActivity.");
        }
        return (PermissionAwareActivity) activity;
    }

}