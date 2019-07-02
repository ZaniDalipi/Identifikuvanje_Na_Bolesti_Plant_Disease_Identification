package com.diplomskitrud.identifikuvanjenabolesti_v1;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CameraFragment2 extends Fragment  implements ActivityCompat.OnRequestPermissionsResultCallback {

    //constants
    //private static final String FRAGMENT_DIALOG = "dialog";
    private static final String TAG = "PlanDiseaseApp";
    private static final String HANDLE_THREAD_NAME = "CameraBackground";
    /* Max preview width that is guaranteed by Camera2 API */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    /* Max preview height that is guaranteed by Camera2 API */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int REQUEST_CODE = 0;

    FrameLayout frameLayout;

    private AutoFitTextureView mTextureView;
    /** reference to the textureView the costom one that we created **/
    private TextView mTextView;
    private CameraDevice mCameraDevice;
    /** represents one phisical device camera **/
    private String mCameraId;
    /** variable that will hold the id of the camera  **/
    private boolean checkedPermissions = false;

    // we use this final lock object to synchronize classes that may interact with untrusted code
    private final Object lock = new Object();
    private boolean runClassifier = false;
    private ImageClassifier imageClassifier;


    /**this method is to setup the TextureView a view that can preview Camera here we are creating only a listener so it listens for different events
     that happens to textureview is it avaliable on start or when we pin down the app and that resume**/

    private final TextureView.SurfaceTextureListener mSurfaceListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };
    /**in this method we are going to cerate that CameraDevice and we will use StateCallBack listener **/
    private final CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice currentCamera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = currentCamera;
            startPreview();
          //  Toast.makeText(getActivity().getApplicationContext(), " camera succesfully connected  ", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice currentCamera) {
            mCameraOpenCloseLock.release();
            currentCamera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice currentCamera, int error) {
            mCameraOpenCloseLock.release();
            currentCamera.close();
            mCameraDevice = null;
            if (null != getActivity()) {
                getActivity().finish();
            }
        }
    };



    private HandlerThread mBackgroundThread;
    /** and  thread that will process the camera  **/
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private Size previewSize; /* preview size of camera preview. */
    private CameraManager manager;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private CaptureRequest.Builder mCaptureRequestBuilder;// this meember is the place that we put all the requests from the Capture session
    private CameraCaptureSession captureSession;
    private CaptureRequest previewRequest;

    private CameraCaptureSession.CaptureCallback captureCallBack =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {

                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                              @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                }
            };


    /**here create a @CaptureSession.CaptureCallback Listener */


    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    () -> mTextView.setText(text));
        }
    }


    public static CameraFragment2 newInstance() {
        return new CameraFragment2();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera_fragment2, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTextureView = view.findViewById(R.id.textureView);
        mTextView = view.findViewById(R.id.predictionText);
        frameLayout = view.findViewById(R.id.control);
        }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        try{
            imageClassifier = new ImageClassifier(getActivity());
            Toast.makeText(getActivity() , "Model and labels has been loaded " , Toast.LENGTH_LONG).show();
        }catch (IOException e){
            Log.e(TAG, "Failed to initialize an image classifier.");
        }
        startBackgroundThread();
    }

    /** this method will be useful to check if the texture is available when we start the app or when we resume and i though will be the best place to check**/
    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());/* we are setting it here like this cuz e know that the app has created it once here we resume**/
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceListener);
        }
    }

    @Override
    public void onPause() {
        //closeCamera();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getActivity().enterPictureInPictureMode();
        }
        stopBackgroundThread();
        super.onPause();
    }
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if(isInPictureInPictureMode){
            mTextView.setTextSize(8);
        }else{
            mTextView.setTextSize(18);

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setupCamera(int width, int height) {

        Activity activity = getActivity();/** we are getting activity object as we are going to need it multiple time also we have to specify that in fragment we cant access direct
         the view cuz it is not type of View and we have to get it though activity**/

        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        /**when making call to something android may msg us that it may return Null so we have to use try/catch**/
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                // we dont want to use the front camera in this case
                Integer cameraFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (cameraFacing != null && cameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;// skip
                }
                StreamConfigurationMap map = cameraCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                /**for still image capture we use the largest avaliabe size**/
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizeByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(null, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                // noinspection ConstantConditions
                /* Orientation of the camera sensor */
                int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }


                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = width;
                    rotatedPreviewHeight = height;
                    rotatedPreviewWidth = displaySize.x;
                    rotatedPreviewHeight = displaySize.y;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }
                previewSize =
                        chooseOptimalSize(
                                map.getOutputSizes(SurfaceTexture.class),// getting the preview sizes and pass to surface class
                                rotatedPreviewWidth,
                                rotatedPreviewHeight,
                                maxPreviewWidth,
                                maxPreviewHeight,
                                largest);

                /** We fit the aspect ratio of TextureView to the size of preview we picked.**/
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }

                // if not facing front
                this.mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.


            /**Commented this line cuz not needed */
            /*
            ErrorDialog.newInstance(getString(R.string.camera_error)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
             */
        }
    }
    private String[] getRequiredPermissions(){

        Activity activity = getActivity();
        try {
            PackageInfo infor = activity
                    .getPackageManager()
                    .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = infor.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        }catch (Exception e){
            return  new String[0];
        }

    }
    private boolean allPermissionsGranted(){
        for (String permmission : getRequiredPermissions()){
            if(ContextCompat.checkSelfPermission(getActivity(), permmission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {

        if (!checkedPermissions && !allPermissionsGranted()){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
               ActivityCompat.requestPermissions(this.getActivity() , getRequiredPermissions() , REQUEST_CODE);
               return;
            }else{
                checkedPermissions = true;
            }
        }

        setupCamera(width, height);// if permission granted call the setupCamera()
        //create a method configureTransformation(width , height)
        configureTransform(width, height);

        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }
    /** from the camera sensor we want to put data into the preview display (textureView)  */
    private void startPreview(){

        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            assert surfaceTexture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            // next creating a surface that will be feed by the camera sensor or the preview
            Surface previewSurface = new Surface(surfaceTexture);

            // here we setup a CaptureRequest.Builder with the output Surface
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            //here we are creating a capture session for the camera preview
            mCameraDevice.createCaptureSession(
                    Collections.singletonList(previewSurface), // changed this from Arrays.asList(previewSurface) cuz it did use more memory than the singletonList
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if(null == mCameraDevice) {
                                return;
                            }
                            // this is executed when the session is ready and we start displaying the preview
                            captureSession = session;


                            try {
                                mCaptureRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                );

                                // finally we start displaying the preview
                                previewRequest = mCaptureRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallBack, mBackgroundHandler);
                            } catch (CameraAccessException e){
                                e.printStackTrace();
                                 }
                            }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            showToast("Failed to setup camera preview");
                        }
                    }
            ,null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Activity activity = getActivity();
        if (requestCode == REQUEST_CODE){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this.getActivity() , new String[]{CAMERA_PERMISSION},REQUEST_CODE);
            }
        }
    }


    /* get rid of camera resources */
    private void closeCamera() {
        try{
            mCameraOpenCloseLock.acquire();
            if (null != captureSession){
                captureSession.close();
                captureSession = null;
            }
            // first check if camera is not equal to null
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null){
                mImageReader.close();
                mImageReader = null;
            }
        }catch (InterruptedException e){
            throw  new RuntimeException("Interrupted while trying to lock camera closing." , e);
        }finally {
            mCameraOpenCloseLock.release();
        }
    }
    /* we are starting this thread when onResume() and onActivityCreated() or when the aactivity calls with fragment */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        // start the classification train & load an initial model
        // and with the synchronized we make sure that 2 threads cannon execute the same method at the same time
        synchronized (lock){
            runClassifier = true;
        }
        mBackgroundHandler.post(periodicClassify);
    }
    private void stopBackgroundThread(){

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            synchronized (lock){
                runClassifier = false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    //Takes photos and classify them periodically
    private Runnable periodicClassify =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (runClassifier) {
                            classifyFrame();
                        }
                    }
                    mBackgroundHandler.post(periodicClassify);
                }
            };

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == previewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }
    private static Size chooseOptimalSize(
        Size[] choises ,
        int textureViewWidth,
        int textureViewHeight,
        int maxWidth ,
        int maxHeight,
        Size aspectRation ) {

        // creating a arraylist that will holt the supported resoulutions that are at least as big as the surface or texture vie resolution
        List<Size> bigEnough = new ArrayList<>();

        // next we are creating a arraylist that will hold the surface that are smaller that the previous surface
        List<Size> notBigEnogh = new ArrayList<>();
        int w = aspectRation.getWidth();
        int h = aspectRation.getHeight();

        for(Size option :   choises){
            if(option.getWidth() <= maxWidth && option.getHeight()<= maxHeight &&
                    option.getHeight() == option.getWidth()* h / w){
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);// if the previous statemanets are okay we put in bigEnough
                }else{
                    notBigEnogh.add(option);
                }
            }
        }
        //pick the smalles of those that are in the list of bigEnough variables , if there is no bigenough pick the largest of those that are in notBigEnough array
        if(bigEnough.size() > 0){
            return Collections.min(bigEnough , new CompareSizeByArea());// get the most suitable res that mean it will be close to the device resolution or the same from the list and create new instance of comareSizeArea object
        }else if (notBigEnogh.size() > 0){
            return Collections.max(notBigEnogh , new CompareSizeByArea());// get the biggest from the list and create new instance of comareSizeArea object
        }else{
            Log.e("Camera2 " , "couldt find any suitable preview size");
            return choises[0];
        }
    }
    /* method that  compares two sizes */
    static class CompareSizeByArea implements Comparator<Size>{

        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum((long) o1.getWidth() * o1.getHeight() - (long) o2.getWidth() * o2.getHeight());// we cast cuz while multiplication it wont overflow
        }
    }

    //THE METHOD THAT WILL CLASIFY A FRAME FROM THE STREAM HERE IS THE PLACE WHERE THE PREDICTION HAPPENS
    private void classifyFrame(){
        if (imageClassifier == null || getActivity() == null || mCameraDevice == null ){
            showToast("unitialized classifier or invalid context please please restart the app if this happens");
            return;
        }
        Bitmap bitmap =
                mTextureView.getBitmap(ImageClassifier.DIM_IMG_SIZE_X , ImageClassifier.DIM_IMG_SIZE_Y);
        String textToShow = imageClassifier.classifyFrame(bitmap);
        bitmap.recycle();
        showToast(textToShow);
    }

    /**Commented this line cuz not needed */
    /*
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(
                            android.R.string.ok,
                            (dialogInterface, i) -> activity.finish())
                    .create();
        }

    }*/
}
