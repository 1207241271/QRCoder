package com.xunce.qrcoder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.common.StringUtils;
import com.google.zxing.qrcode.encoder.QRCode;
import com.xunce.qrcoder.tool.zxing.android.BeepManager;
import com.xunce.qrcoder.tool.zxing.android.CaptureActivityHandler;
import com.xunce.qrcoder.tool.zxing.android.FinishListener;
import com.xunce.qrcoder.tool.zxing.android.InactivityTimer;
import com.xunce.qrcoder.tool.zxing.android.IntentSource;
import com.xunce.qrcoder.tool.zxing.camera.CameraManager;
import com.xunce.qrcoder.tool.zxing.view.ViewfinderView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private HashMap<String ,String> IMEIMap;
    private TextView txt_SN;

    private static final String TAG = MainActivity.class.getSimpleName();

    // 相机控制
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private IntentSource source;
    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType, ?> decodeHints;
    private String characterSet;
    // 电量控制
    private InactivityTimer inactivityTimer;
    // 声音、震动控制
    private BeepManager beepManager;

    private ImageButton imageButton_back;

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    /**
     * OnCreate中初始化一些辅助类，如InactivityTimer（休眠）、Beep（声音）以及AmbientLight（闪光灯）
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main);
        Button btn_Choose_file = (Button)findViewById(R.id.btn_choose_file);
        btn_Choose_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser();
            }
        });

        txt_SN = (TextView) findViewById(R.id.txt_SN);
        checkPermission();
        IMEIMap = new HashMap<>();
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hasSurface = false;

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        initcamera();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // CameraManager必须在这里初始化，而不是在onCreate()中。
        // 这是必须的，因为当我们第一次进入时需要显示帮助页，我们并不想打开Camera,测量屏幕大小
        // 当扫描框的尺寸不正确时会出现bug
        cameraManager = new CameraManager(getApplication());

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // activity在paused时但不会stopped,因此surface仍旧存在；
            // surfaceCreated()不会调用，因此在这里初始化camera
            initCamera(surfaceHolder);
        } else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder.addCallback(this);
        }

        beepManager.updatePrefs();
        inactivityTimer.onResume();

        source = IntentSource.NONE;
        decodeFormats = null;
        characterSet = null;
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    /**
     * 扫描成功，处理反馈信息
     *
     * @param rawResult
     * @param barcode
     * @param scaleFactor
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();

        boolean fromLiveScan = barcode != null;
        //这里处理解码完成后的结果，此处将参数回传到Activity处理
        if (fromLiveScan) {
            beepManager.playBeepSoundAndVibrate();
            //Toast.makeText(this, rawResult.getText(), Toast.LENGTH_SHORT).show();
            String IMEI = null;
            try{
                JSONObject jsonObject = new JSONObject(rawResult.getText());
                if (jsonObject.has("IMEI")){
                    IMEI = jsonObject.getString("IMEI");
                    Log.e("IMEI", IMEI);
                }
            }catch (JSONException e){
                e.printStackTrace();
            }
            if (null!=IMEI){
                dealresult(IMEI);
            }
        }

    }

    /**
     * 初始化Camera
     *
     * @param surfaceHolder
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            return;
        }
        try {
            // 打开Camera硬件设备
            cameraManager.openDriver(surfaceHolder);
            // 创建一个handler来打开预览，并抛出一个运行时异常
            if (handler == null) {
                handler = new CaptureActivityHandler(MainActivity.this, decodeFormats,
                        decodeHints, characterSet, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * 显示底层错误信息并退出应用
     */
    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage("Sorry, the Android camera encountered a problem. You may need to restart the device.");
        builder.setPositiveButton("OK", new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    public void dealresult(String IMEI) {
        if (!IMEI.isEmpty()){
            if (IMEIMap.containsKey(IMEI)){
                endinit();
                vibrate();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("SN号");
                builder.setMessage(IMEIMap.get(IMEI));
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create().show();
                txt_SN.setText(IMEIMap.get(IMEI));
                initcamera();
            }
        }
    }

    protected void initcamera() {

        // CameraManager必须在这里初始化，而不是在onCreate()中。
        // 这是必须的，因为当我们第一次进入时需要显示帮助页，我们并不想打开Camera,测量屏幕大小
        // 当扫描框的尺寸不正确时会出现bug
        cameraManager = new CameraManager(getApplication());

        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // activity在paused时但不会stopped,因此surface仍旧存在；
            // surfaceCreated()不会调用，因此在这里初始化camera
            initCamera(surfaceHolder);
        } else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder.addCallback(this);
        }

        beepManager.updatePrefs();
        inactivityTimer.onResume();

        source = IntentSource.NONE;
        decodeFormats = null;
        characterSet = null;
    }

    protected void endinit() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(200);
    }

    private void checkPermission() {
        int permisson = PermissionChecker.checkSelfPermission(this,Manifest.permission.CAMERA);

        if (permisson != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }

        permisson = PermissionChecker.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permisson != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }
        permisson = PermissionChecker.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permisson != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }
    }


    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(intent,0);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.",  Toast.LENGTH_SHORT).show();
        }
    }

    private void getData(){
        String fileName = "100.txt";
        String res = "";
        try{
            InputStreamReader inputReader = new InputStreamReader( getResources().getAssets().open(fileName) );
            BufferedReader bufferedReader = new BufferedReader(inputReader);
            String lineTxt="";
            while ((lineTxt = bufferedReader.readLine()) != null) {//按行读取
                if (!"".equals(lineTxt)) {
                    String [] strArray = lineTxt.split("\\t");//对行的内容进行分析处理后再放入map里。
                    if(strArray.length == 2) {
                        IMEIMap.put(strArray[0], strArray[1]);
                    }
                }
            }
            inputReader.close();//关闭InputStreamReader
            bufferedReader.close();//关闭BufferedReader
        }catch (Exception e){
            e.printStackTrace();
        }
    }

   @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
       if (resultCode == Activity.RESULT_OK&&requestCode==0){
           Uri uri = data.getData();//得到uri，后面就是将uri转化成file的过程。
           String string =uri.getPath().toString();
           File file = new File(string);
           IMEIMap.clear();
           if (file.exists()){
               try {
                   FileInputStream fin = new FileInputStream(file);
                   BufferedReader buffer = new BufferedReader(new InputStreamReader(fin));
                   String lineTxt = "";
                   Log.e("File", "read");
                   while ((lineTxt = buffer.readLine()) != null){
                       if (!"".equals(lineTxt)){
                           String [] strArray = lineTxt.split("\\t");
                           if (strArray.length == 2){
                               IMEIMap.put(strArray[0], strArray[1]);
                           }
                       }
                   }
                   buffer.close();
                   fin.close();
                   Toast.makeText(MainActivity.this, "读取成功", Toast.LENGTH_SHORT).show();
               }catch (Exception e){
                   e.printStackTrace();
               }
           }
       }
    }

    /*private void searchSN(String IMEI){
        for (Map.Entry<String, String> entry:IMEIMap.entrySet()){
            if (entry.getKey().toString().equals(IMEI)){
                Toast.makeText(MainActivity.this, entry.getValue().toString(), Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }*/

    private void createDir(){
        File filePath = this.getFilesDir();
        String  pathStr = filePath.toString();
        File file = new File(pathStr);
    }
}
