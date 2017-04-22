package com.xunce.qrcoder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import cn.bingoogolapple.qrcode.core.QRCodeView;
import cn.bingoogolapple.qrcode.zxing.ZXingView;

public class MainActivity extends AppCompatActivity implements QRCodeView.Delegate {
    private ZXingView   mQRCodeView;
    private HashMap<String ,String> IMEIMap;
    private TextView txt_SN;
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

        IMEIMap = new HashMap<>();

        mQRCodeView = (ZXingView) findViewById(R.id.zxingview);
        checkPermission();
        //getData();
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
            }
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
