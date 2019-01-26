package com.diplomskitrud.identifikuvanjenabolesti_v1;

import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;


public class CameraActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        if (null == savedInstanceState) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, CameraFragment2.newInstance())
                    .commit();
        }
    }


}
