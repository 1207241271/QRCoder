package com.xunce.qrcoder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.common.StringUtils;
import com.google.zxing.qrcode.encoder.QRCode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import cn.bingoogolapple.qrcode.core.QRCodeView;
import cn.bingoogolapple.qrcode.zxing.ZXingView;

public class MainActivity extends AppCompatActivity implements QRCodeView.Delegate {
    private ZXingView   mQRCodeView;
    private HashMap<String ,String> IMEIMap;
    private TextView txt_SN;
    private TextView txt_File;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btn_Choose_file = (Button)findViewById(R.id.btn_choose_file);
        btn_Choose_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser();
            }
        });

        txt_SN = (TextView) findViewById(R.id.txt_SN);
        txt_File = (TextView) findViewById(R.id.txt_file);

        IMEIMap = new HashMap<>();

        mQRCodeView = (ZXingView) findViewById(R.id.zxingview);
        checkPermission();
        getData();
        mQRCodeView.startCamera();
        mQRCodeView.startSpot();
        mQRCodeView.setResultHandler(this);
    }

    @Override
    public void onScanQRCodeSuccess(String result) {
        Log.d("TEST",result);
        String IMEI = StringDecodeUtil.getIMEIFromSim808(result);
        if (!IMEI.isEmpty()){
            if (IMEIMap.containsKey(IMEI)){
                vibrate();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(IMEIMap.get(IMEI));
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create().show();
                txt_SN.setText(IMEIMap.get(IMEI));
            }else {
                vibrate();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("IMEI号错误"+IMEI);
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create().show();
            }
        }else {
            vibrate();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("没有IMEI");
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            }).create().show();
        }
        mQRCodeView.startCamera();
        mQRCodeView.startSpot();
    }

    @Override
    public void onScanQRCodeOpenCameraError() {
        Log.e("e", "打开相机出错");
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
                // System.out.println(“lineTxt=” + lineTxt);
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
            String string = null;
            try {
                string = getPath(this, uri);
                String[] showFileName = string.split("/");
                txt_File.setText(showFileName[showFileName.length-1]);
            }catch (Exception e){
                e.printStackTrace();
            }
            if (TextUtils.isEmpty(string)){
                return;
            }
            File file = new File(string);
                IMEIMap.clear();
                try {
                    InputStreamReader read = new InputStreamReader(new FileInputStream(file), "UTF-8");// 考虑到编码格式
                    BufferedReader bufferedReader = new BufferedReader(read);
                    String lineTxt = null;
                    while ((lineTxt = bufferedReader.readLine()) != null) {//按行读取
                        // System.out.println(“lineTxt=” + lineTxt);
                        if (!"".equals(lineTxt)) {
                            String [] strArray = lineTxt.split("\\t");//对行的内容进行分析处理后再放入map里。
                            IMEIMap.put(strArray[0],strArray[1]);
                        }
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("读取文件成功");
                    builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
                    read.close();//关闭InputStreamReader
                    bufferedReader.close();//关闭BufferedReader
                    mQRCodeView.startCamera();
                    mQRCodeView.startSpot();
                }catch (Exception e){
                    e.printStackTrace();
                }

        }
    }
    public static String getPath(Context context, Uri uri) throws URISyntaxException {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {"_data"};
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                // Eat it  Or Log it.
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private void createDir(){
        File filePath = this.getFilesDir();
        String  pathStr = filePath.toString();
        File file = new File(pathStr);
    }
}
