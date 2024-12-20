package com.herohan.uvcdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcdemo.utils.CustomFPS;
import com.herohan.uvcdemo.utils.NV21ToBitmap;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.text.DecimalFormat;
import java.util.List;

public class SetFrameCallbackActivity extends AppCompatActivity implements View.OnClickListener {

    private static final boolean DEBUG = true;
    private static final String TAG = SetFrameCallbackActivity.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 3840;
    private static final int DEFAULT_HEIGHT = 2880;

    private ICameraHelper mCameraHelper;

    private AspectRatioSurfaceView mCameraViewMain;
    private ImageView mFrameCallbackPreview;

    private NV21ToBitmap mNv21ToBitmap;

    private CustomFPS mCustomFPS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_frame_callback);
        setTitle(R.string.entry_basic_preview);

        initViews();

        mNv21ToBitmap = new NV21ToBitmap(this);
    }

    private void initViews() {
        mCameraViewMain = findViewById(R.id.svCameraViewMain);
        mCameraViewMain.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        mCameraViewMain.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });
        mFrameCallbackPreview = findViewById(R.id.ivFrameCallbackPreview);

        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        btnOpenCamera.setOnClickListener(this);
        Button btnCloseCamera = findViewById(R.id.btnCloseCamera);
        btnCloseCamera.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCameraHelper();
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.getDeviceName());
        mCameraHelper.selectDevice(device);
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:");
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:");
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:");

            mCameraHelper.startPreview();

            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                int width = size.width;
                int height = size.height;
                //auto aspect ratio
                mCameraViewMain.setAspectRatio(width, height);
            }

            mCameraHelper.addSurface(mCameraViewMain.getHolder().getSurface(), false);

            mCameraHelper.setFrameCallback(frame -> {
                Log.d(TAG,"数据来了");

                if (mCustomFPS != null) {
                    //Refresh FPS
                    mCustomFPS.doFrame();
                }

//
                byte[] nv21 = new byte[frame.remaining()];
                frame.get(nv21, 0, nv21.length);
//
//                Bitmap bitmap = mNv21ToBitmap.nv21ToBitmap(nv21, size.width, size.height);
                Bitmap bitmap = convertRGBXToBitmap(nv21,size.width,size.height);
                runOnUiThread(() -> {
                    mFrameCallbackPreview.setImageBitmap(bitmap);
                });
            }, UVCCamera.PIXEL_FORMAT_RGBX);

            initFPS();
        }


        public Bitmap convertRGBXToBitmap(byte[] rgbxData, int width, int height) {
            // 每个像素四个字节：RGBX
            int[] pixels = new int[width * height];

            for (int i = 0; i < pixels.length; i++) {
                int r = rgbxData[i * 4] & 0xFF;     // 红色通道
                int g = rgbxData[i * 4 + 1] & 0xFF; // 绿色通道
                int b = rgbxData[i * 4 + 2] & 0xFF; // 蓝色通道
                // int x = rgbxData[i * 4 + 3] & 0xFF; // 未使用的字节

                // 构建 ARGB 颜色，A（透明度）设为 255（不透明）
                pixels[i] = 0xFF << 24 | (r << 16) | (g << 8) | b;
            }

            // 创建一个 Bitmap 对象
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            return bitmap;
        }


        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
            }

            clearFPS();
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:");
        }

    };

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnOpenCamera) {
            // select a uvc device
            if (mCameraHelper != null) {
                final List<UsbDevice> list = mCameraHelper.getDeviceList();
                if (list != null && list.size() > 0) {
                    mCameraHelper.selectDevice(list.get(0));
                }
            }
        } else if (v.getId() == R.id.btnCloseCamera) {
            // close camera
            if (mCameraHelper != null) {
                mCameraHelper.closeCamera();

            }
        }else if(v.getId() ==R.id.btnAspectRatio){
            mCameraViewMain.setAspectRatio(9 / 16f);
        }
    }

    /**
     * Initialize the FPS display
     */
    private void initFPS() {
        DecimalFormat decimal = new DecimalFormat(" #.0' fps'");

        mCustomFPS = new CustomFPS();
        mCustomFPS.addListener(fps -> {
            if (DEBUG) Log.v(TAG, "fps:" + fps);
            runOnUiThread(() -> {
                setTitle(decimal.format(fps));
            });
        });
    }

    /**
     * End FPS display
     */
    private void clearFPS() {
        if (mCustomFPS != null) {
            mCustomFPS.release();
            mCustomFPS = null;
        }

        setTitle(R.string.entry_basic_preview);
    }
}