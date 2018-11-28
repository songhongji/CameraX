package com.xys.songhongji.camerax.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;

import com.xys.songhongji.camerax.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// 只有api在21 也就是android5.0以上才能使用Camera2 api
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraTwoActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1;
    private static final String TAG = "CameraTwoActivity";

    // 用于预览的TextureView
    private TextureView mTextureViewPreview;
    private Button mCapturePictureBtn;
    private Button mCaptureVideoBtn;

    // 用于静态图片拍摄
    private ImageReader mImageReader;

    // 当前camera的id
    private String mCameraId;

    // 预览大小
    private Size mPreviewSize;

    // 控制在后台的任务
    private Handler mBackgroundHandler;

    // 控制任务在子线程执行
    private HandlerThread mBackgroundThread;

    // 防止退出应用前没有关闭摄像头
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    // 打开的摄像设备
    private CameraDevice mCameraDevice;

    private CameraCaptureSession mCaptureSession;

    private CaptureRequest mPreviewRequest;

    // 用于存储照片
    private File mFile;

    //
    private CaptureRequest.Builder mPreviewRequestBuilder;

    // 当图片ready能save的时候回调
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage(), mFile));
        }
    };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
//            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_two);

        mTextureViewPreview = findViewById(R.id.camera_texture_preview);
        mCapturePictureBtn = findViewById(R.id.button_capture_picture);
        mCaptureVideoBtn = findViewById(R.id.button_capture_video);


    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mTextureViewPreview.isAvailable()) {
            openCamera(mTextureViewPreview.getWidth(), mTextureViewPreview.getHeight());
        } else {
            mTextureViewPreview.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    // 打开摄像头, 开启preview
    private void openCamera(int width, int height) {
        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQUEST_PERMISSION);
            return;
        }

        if (!setupCameraOutputs(width, height)) {
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                return;
            }

            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 设置摄像头参数
    private boolean setupCameraOutputs(int width, int height) {

        // CameraManger 管理所有的CameraDevice
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                // CameraCharacteristics是CameraDevices的属性描述类
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);

                // 禁用前置摄像头
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // 管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (streamConfigurationMap == null) {
                    continue;
                }

                // 获取capture尺寸
                Size largest = Collections.max(Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

                // 设置ImageReader 用于拍摄静态图片
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                // 获取preview尺寸
                mPreviewSize = getOptimalSize(streamConfigurationMap.getOutputSizes(SurfaceTexture.class), width, height);

                // 处理屏幕横屏竖屏
//                int orientation = getResources().getConfiguration().orientation;
//                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    mTextureViewPreview.setAspectRatio(
//                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
//                } else {
//                    mTextureViewPreview.setAspectRatio(
//                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
//                }

                mCameraId = cameraId;
                return true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return false;
    }

    // 根据面积大小来对比
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // 选择sizeMap中大于并且最接近width和height的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    // 启动后台子线程，初始化handler
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    // 结束后台子线程
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            // join：子线程执行完毕之后，才会执行后面的代码
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        SurfaceTexture surfaceTexture = mTextureViewPreview.getSurfaceTexture();
        if (surfaceTexture == null) {
            return;
        }

        // 设置默认buffer size
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        Surface surface = new Surface(surfaceTexture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice == null) {
                        return;
                    }

                    mCaptureSession = session;
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                    mPreviewRequest = mPreviewRequestBuilder.build();
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // 在子线程中保存图片
    private static class ImageSaver implements Runnable {

        private final Image mImage;
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }

}
