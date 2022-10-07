package net.radekw8733.antygarb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import net.radekw8733.antygarb.ml.LiteModelMovenetSingleposeLightningTfliteInt84;

import org.tensorflow.lite.support.image.TensorImage;

import java.io.IOException;
import java.nio.FloatBuffer;

public class CameraInferenceUtil {
    private Context context;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private Camera camera;
    private LiteModelMovenetSingleposeLightningTfliteInt84 model;
    private KeypointsReturn callback;

    // variant with preview and image analysis, intended for activity
    public CameraInferenceUtil(Context context, PreviewView previewView) {
        this.context = context;
        this.previewView = previewView;

        loadMovenetModel();
    }

    // variant without preview, with image capture, intended for service
    public CameraInferenceUtil(Context context) {
        this.context = context;

        loadMovenetModel();
    }

    // should be executed after succesful permission check
    public void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                setupLifecycle();
            }
            catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(context));
    }

    // bind camera usage to context
    @SuppressLint("RestrictedApi")
    private void setupLifecycle() {
        Display displayService = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

        if (previewView != null) {
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetRotation(displayService.getRotation())
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build();

            camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, imageAnalysis, preview);
            enableImageAnalysis();
        }
        else {
            imageCapture = new ImageCapture.Builder()
                    .setTargetRotation(displayService.getRotation())
                    .setBufferFormat(ImageFormat.FLEX_RGBA_8888)
                    .build();

            camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, imageAnalysis);
        }
    }

    private void enableImageAnalysis() {
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                Keypoints keypoints = runPoseInference(image);
                callback.returnKeypoints(keypoints);
                image.close();
            }
        });
    }

    private void takePicture() {
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Keypoints keypoints = runPoseInference(image);
                callback.returnKeypoints(keypoints);
                image.close();
            }
        });
    }

    private Keypoints runPoseInference(ImageProxy image) {
        @SuppressLint("UnsafeOptInUsageError") Bitmap rawImage = imageToBitmap(image.getImage());
        rawImage = Bitmap.createScaledBitmap(rawImage, 192, 192, true);

        TensorImage tensorImage = new TensorImage();
        tensorImage.load(rawImage);

        // inference
        LiteModelMovenetSingleposeLightningTfliteInt84.Outputs outputs = model.process(tensorImage.getTensorBuffer());

        float[] outputArray = outputs.getOutputFeature0AsTensorBuffer().getFloatArray();
        if (outputArray.length > 0) {
            FloatBuffer buffer = FloatBuffer.wrap(outputArray);
            buffer.position(16); // start position of left shoulder from model

            Keypoints keypoints = new Keypoints();
            keypoints.leftShoulderX = Math.round(buffer.get() * image.getWidth());
            keypoints.leftShoulderY = Math.round(buffer.get() * image.getHeight());
            keypoints.confidence = Math.round(buffer.get() * 100);

            keypoints.rightShoulderX = Math.round(buffer.get() * image.getWidth());
            keypoints.rightShoulderY = Math.round(buffer.get() * image.getHeight());
            keypoints.confidence = Math.round(buffer.get() * 100);

            keypoints.confidence /= 2; // calculate average
            return keypoints;
        }
        else {
            return null;
        }
    }

    public void setKeypointCallback(KeypointsReturn callback) {
        this.callback = callback;
    }

    public void getKeypoints() {
        if (previewView != null) {
            enableImageAnalysis();
        }
        else {
            takePicture();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Bitmap rawImage = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        rawImage.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
        return rawImage;
    }

    private void loadMovenetModel() {
        try {
            model = LiteModelMovenetSingleposeLightningTfliteInt84.newInstance(context);
        }
        catch (IOException e) {
            model.close();
        }
    }

    public class Keypoints {
        public int leftShoulderX;
        public int leftShoulderY;

        public int rightShoulderX;
        public int rightShoulderY;

        public int confidence;
    }
}
