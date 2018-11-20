package com.xys.songhongji.camerax;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.xys.songhongji.camerax.camera.CameraActivity;

public class MainActivity extends AppCompatActivity {

    TextView takePhotoTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        takePhotoTv = findViewById(R.id.take_photo_tv);
        takePhotoTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            }
        });
    }
}
