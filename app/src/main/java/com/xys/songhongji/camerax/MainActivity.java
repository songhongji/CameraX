package com.xys.songhongji.camerax;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.xys.songhongji.camerax.camera.CameraOneActivity;
import com.xys.songhongji.camerax.camera.CameraPictureActivity;
import com.xys.songhongji.camerax.camera.CameraVideoActivity;

public class MainActivity extends AppCompatActivity {

    TextView takePhotoTv;
    TextView takeVideoTv;
    TextView cameraOneTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        takePhotoTv = findViewById(R.id.take_photo_tv);
        takePhotoTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, CameraPictureActivity.class);
                startActivity(intent);
            }
        });

        takeVideoTv = findViewById(R.id.take_video_tv);
        takeVideoTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, CameraVideoActivity.class);
                startActivity(intent);
            }
        });

        cameraOneTv = findViewById(R.id.camera_one_tv);
        cameraOneTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, CameraOneActivity.class);
                startActivity(intent);
            }
        });
    }
}
