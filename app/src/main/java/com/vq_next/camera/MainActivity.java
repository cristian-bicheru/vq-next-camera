package com.vq_next.camera;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.hardware.camera2.*;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.round;

public class MainActivity extends AppCompatActivity {
    @RequiresApi(api = Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentApiVersion = android.os.Build.VERSION.SDK_INT;
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if(currentApiVersion >= Build.VERSION_CODES.KITKAT)
        {
            getWindow().getDecorView().setSystemUiVisibility(flags);
            final View decorView = getWindow().getDecorView();
            decorView
                    .setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
                    {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility)
                        {
                            if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                            {
                                decorView.setSystemUiVisibility(flags);
                            }
                        }
                    });
        }
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        recordsoundeffect = MediaPlayer.create(this, R.raw.begin_record);
        endrecordsoundeffect = MediaPlayer.create(this, R.raw.end_record);
        final SurfaceView sfview = findViewById(R.id.cam_stream);
        final SurfaceHolder sfholder = sfview.getHolder();
        recordtimer = new TimerThread(R.id.cam_time);
        sthread.start();
        surfimghandler = new Handler(sthread.getLooper());
        spinnerworker = new SpinnerActivity();
        Spinner spinner = (Spinner) findViewById(R.id.cam_res);
        spinner.setOnItemSelectedListener(spinnerworker);
        sfholder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long numpixels = 0, test;
                Rect dims;
                camMgr = (CameraManager) getSystemService(CAMERA_SERVICE);

                try {
                    String[] camids = camMgr.getCameraIdList();

                    for (String id : camids) {
                        CameraCharacteristics characteristics = camMgr.getCameraCharacteristics(id);
                        dims = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                        test = dims.width() * dims.height();
                        if (test > numpixels) {
                            numpixels = test;
                            camid = id;
                            camdims = dims;
                        }
                    }

                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                    } else {
                        camMgr.openCamera(camid, AsyncCam, null);
                    }
                } catch (CameraAccessException e) {
                    if (e.getReason() == CameraAccessException.CAMERA_DISABLED) {
                        // ...
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        Button recordButton = (Button) findViewById(R.id.record_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!recording) {
                    try {
                        recording = true;
                        initRecord();
                    } catch (FileNotFoundException | CameraAccessException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        recording = false;
                        closeRecord();
                    } catch (IOException | CameraAccessException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @SuppressLint("NewApi")
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if(currentApiVersion >= Build.VERSION_CODES.KITKAT && hasFocus)
        {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            try {
                camMgr.openCamera(camid, AsyncCam, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public class SpinnerActivity extends Activity implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (!recording && position != 0) {
                String t = (String) parent.getItemAtPosition(position);
                dataio.replace();
                dataio = new ImageReaderIO(Integer.parseInt(t.split("x")[0]), Integer.parseInt(t.split("x")[1]));
                sess.close();
                try {
                    cam.createCaptureSession((List<Surface>) new ArrayList<Surface>(){{add(prevsurf);add(dataio.readersurf);}}, AsyncCap, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }

    protected class CameraCallback extends CameraDevice.StateCallback {

        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            final SurfaceView sfview = findViewById(R.id.cam_stream);
            final SurfaceHolder sfholder = sfview.getHolder();
            cam = camera;
            prevsurf = sfholder.getSurface();
            dataio = new ImageReaderIO(camdims.width(), camdims.height());
            try {
                camera.createCaptureSession((List<Surface>) new ArrayList<Surface>(){{add(prevsurf);add(dataio.readersurf);}}, AsyncCap, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    }

    void setFPS() {
        try {
            Range<Integer>[] availableframerates = camMgr.getCameraCharacteristics(camid).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

            Range<Integer> maxfps = new Range<>(0, 0);
            for (Range<Integer> x : availableframerates) {
                if (x.getUpper().equals(x.getLower())) {
                    if (x.getLower()>maxfps.getLower()) {
                        maxfps = x;
                    }
                }
            }
            camfps = maxfps;
            TextView fpstext = (TextView) findViewById(R.id.cam_fps);
            String text = maxfps.getLower()+" FPS";
            fpstext.setText(text);
        } catch(CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected class CaptureCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                if (load_resolutions) {
                    Spinner spinner = (Spinner) findViewById(R.id.cam_res);
                    StreamConfigurationMap streamConfigurationMap = camMgr.getCameraCharacteristics(camid).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] availableresolutions = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888);

                    ArrayList<String> resolutions = new ArrayList<>();
                    for (Size res : availableresolutions) {
                        resolutions.add(res.getWidth() + "x" + res.getHeight());
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, resolutions);
                    adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapter);
                    load_resolutions = false;
                }
                setFPS();

                CaptureRequest.Builder builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, camfps);
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                builder.addTarget(prevsurf);
                session.setRepeatingRequest(builder.build(), null, surfimghandler);
                sess = session;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            // F
        }
    }

    public long frames = 0;
    public long start_time;
    ImageReader.OnImageAvailableListener onFrame = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (recording) {
                Image img = reader.acquireLatestImage();
                if (img != null) {
                    frames += 1;
                    double fps = frames/((System.currentTimeMillis()-start_time+1.)/1000.);
                    Log.d("FPS", String.valueOf(fps));
                    final TextView fpstext = (TextView) findViewById(R.id.cam_fps);
                    final String text = round(fps)+" FPS";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fpstext.setText(text);
                        }
                    });
                    Image.Plane[] planes = img.getPlanes();
                    try {
                        dataio.bitstream.write(planes[0].getBuffer());
                        dataio.bitstream.write(planes[1].getBuffer());
                        dataio.outstream.write(planes[2].getBuffer().get(planes[2].getBuffer().remaining()-1));
                        //https://stackoverflow.com/questions/51399908/yuv-420-888-byte-format... really...
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    img.close();
                }
            }
        }
    };

    class Timer implements Runnable {
        TextView source;
        char[] newtext;
        final Handler timer_handler = new Handler();

        Timer(@IdRes int id) {
            source = (TextView) findViewById(id);
            newtext = "00:00:00".toCharArray();
            source.setText(newtext, 0, 8);
        }

        public void start() {
            timer_handler.postDelayed(this, 1000);
        }

        @Override
        public void run() {
            if (recording) {
                int hours = (newtext[0]-48)*10+newtext[1]-48;
                int mins = (newtext[3]-48)*10+newtext[4]-48;
                int secs = (newtext[6]-48)*10+newtext[7]-48;
                secs++;
                if (secs == 60) {
                    secs = 0;
                    mins++;
                    if (mins == 60) {
                        mins = 0;
                        hours++;
                    }
                }

                newtext[0] = (char) (hours/10 + 48);
                newtext[1] = (char) ((hours%10) + 48);
                newtext[2] = ':';
                newtext[3] = (char) (mins/10 + 48);
                newtext[4] = (char) ((mins%10) + 48);
                newtext[5] = ':';
                newtext[6] = (char) (secs/10 + 48);
                newtext[7] = (char) ((secs%10) + 48);

                source.setText(newtext, 0, 8);

                timer_handler.postDelayed(this, 1000);
            } else {
                newtext = "00:00:00".toCharArray();
                source.setText(newtext, 0, 8);
            }
        }
    }

    class TimerThread extends Thread {
        Timer timer;

        TimerThread(@IdRes int id) {
            timer = new Timer(id);
        }

        @Override
        public void run() {
            timer.start();
        }
    }

    protected class ImageReaderIO {
        FileOutputStream foutstream;
        DataOutputStream outstream;
        WritableByteChannel bitstream;
        ImageReader reader;
        Surface readersurf;
        int _width;
        int _height;
        HandlerThread pthread = new HandlerThread("pthread");
        Handler imgreaderhandler;

        ImageReaderIO(int width, int height) {
            _width = width;
            _height = height;
            reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);
            pthread.start();
            imgreaderhandler = new Handler(pthread.getLooper());
            reader.setOnImageAvailableListener(onFrame, imgreaderhandler);
            readersurf = reader.getSurface();
        }

        void open(String name, Context context) throws FileNotFoundException {
            File dcimpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            Log.d("IO", new File(dcimpath, name).getPath());
            foutstream = new FileOutputStream(new File(dcimpath, name));
            outstream = new DataOutputStream(foutstream);
            bitstream = Channels.newChannel(outstream);
            start_time = System.currentTimeMillis();
        }

        void close() throws IOException {
            outstream.flush();
            foutstream.flush();

            bitstream.close();
            outstream.close();
            foutstream.close();

            File dcimpath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File out = new File(dcimpath, writename);
            out.renameTo(new File(dcimpath, writename.split(".yuv")[0]+"-"+round(frames/((System.currentTimeMillis()-start_time+1.)/1000.))+"fps.yuv"));
        }

        void replace() {
            reader.close();
            pthread.quit();
        }
    }

    public void initRecord() throws FileNotFoundException, CameraAccessException {
        Spinner spinner = (Spinner) findViewById(R.id.cam_res);
        spinner.setEnabled(false);
        writename = DateTimeFormatter.ofPattern("yyyy'-'MM'-'dd'-'HH'-'mm'-'ss'-'").format(LocalDateTime.now())+dataio._width+"x"+dataio._height+".yuv";
        dataio.open(writename, getApplicationContext());

        sess.stopRepeating();
        CaptureRequest.Builder builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, camfps);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        builder.addTarget(prevsurf);
        builder.addTarget(dataio.reader.getSurface());
        sess.setRepeatingRequest(builder.build(), null, surfimghandler);

        recordtimer.start();
        recordsoundeffect.start();
    }

    public void closeRecord() throws IOException, CameraAccessException, InterruptedException {
        dataio.close();

        sess.stopRepeating();
        CaptureRequest.Builder builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, camfps);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        builder.addTarget(prevsurf);
        sess.setRepeatingRequest(builder.build(), null, surfimghandler);

        recordtimer.join();
        recordtimer = new TimerThread(R.id.cam_time);
        Spinner spinner = (Spinner) findViewById(R.id.cam_res);
        spinner.setEnabled(true);
        endrecordsoundeffect.start();
        Log.d("STAT", "STOPPED RECORDING");
    }

    public native ByteBuffer YUV_CHROMA_DEINTERLACER(ByteBuffer x, ByteBuffer y, long len);
    static {
        System.loadLibrary("converter");
    }
    public boolean recording = false;
    public CameraCallback AsyncCam = new CameraCallback();
    public CaptureCallback AsyncCap = new CaptureCallback();
    public CameraManager camMgr;
    public String camid;
    public CameraCaptureSession sess;
    public CameraDevice cam;
    public Surface prevsurf;
    public String writename;
    public ImageReaderIO dataio;
    public Rect camdims;
    public TimerThread recordtimer;
    public Range<Integer> camfps = new Range<>(0, 0);
    public HandlerThread sthread = new HandlerThread("sthread");
    public Handler surfimghandler;
    public SpinnerActivity spinnerworker;
    public Boolean load_resolutions = true;
    public MediaPlayer recordsoundeffect;
    public MediaPlayer endrecordsoundeffect;
    public int currentApiVersion;
}

