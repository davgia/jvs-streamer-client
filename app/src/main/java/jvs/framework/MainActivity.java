package jvs.framework;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import jvs.framework.streamer.JVSStreamer;
import jvs.framework.streamer.Utilities;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int MY_REQUEST_CODE = 100;
    private Button actionButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        askPermissions();

        actionButton = findViewById(R.id.actionButton);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkPermissions()) {
                    if (!JVSStreamer.getInstance().isRunning()) {
                        JVSStreamer.getInstance().setContext(getApplicationContext());
                        JVSStreamer.getInstance().start();
                        ((Button)view).setText(getString(R.string.stop_button));
                        statusText.setText(getString(R.string.status_running));
                    } else {
                        JVSStreamer.getInstance().stop();
                        ((Button)view).setText(getString(R.string.start_button));
                        statusText.setText(getString(R.string.status_stopped));
                    }
                } else {
                    Log.d(TAG, "Cannot start the stream, not all needed permissions have been granted.");
                    askPermissions();
                }
            }
        });
        statusText = findViewById(R.id.statusText);
        statusText.setText(getString(R.string.status_idle));

        TextView addressText = findViewById(R.id.addressText);
        addressText.setText(Utilities.getLocalIpAddress(false));
    }

    private void askPermissions() {

        List permissionToRequest = new ArrayList<String>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionToRequest.add(Manifest.permission.CAMERA);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionToRequest.add(Manifest.permission.RECORD_AUDIO);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissionToRequest.size() > 0) {
            String[] arrayRequests = (String[])permissionToRequest.toArray(new String[permissionToRequest.size()]);
            ActivityCompat.requestPermissions(this, arrayRequests, MY_REQUEST_CODE);
        }
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_REQUEST_CODE: {
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permission not granted for: " + permissions[i], Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Log.d(TAG, "Permission granted for: " + permissions[i]);
                    }
                }
                break;
            }
        }
    }
}