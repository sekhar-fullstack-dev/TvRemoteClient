package com.example.tvremoteclient.base;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class BaseActivity extends AppCompatActivity {
    private static final int REQUEST_READ_STORAGE = 100;
    private static final String TAG = "BaseActivity";

    public void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_READ_STORAGE);
            } else {
                onStoragePermission(true);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_STORAGE);
            } else {
                onStoragePermission(true);
            }
        }
    }
    protected void onStoragePermission(boolean isPermissionGranted){
        Log.d(TAG, "onStoragePermission: ");
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE) {
            if (grantResults.length > 0) {
                onStoragePermission(grantResults[0] == PackageManager.PERMISSION_GRANTED);
            }
            else{
                onStoragePermission(false);
            }
        }
    }

}
