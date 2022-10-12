package net.radekw8733.antygarb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.media.Image;
import android.util.Log;
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

import net.radekw8733.antygarb.ml.LiteModelMovenetSingleposeLightning3;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class CameraInferenceUtil {
    private Context context;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private Camera camera;
    private LiteModelMovenetSingleposeLightning3 model;
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
                HashMap<String, Keypoint> keypoints = runPoseInference(image);
                previewView.getOverlay().clear();
                callback.returnKeypoints(keypoints);
                image.close();
            }
        });
    }

    private void takePicture() {
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                HashMap<String, Keypoint> keypoints = runPoseInference(image);
                previewView.getOverlay().clear();
                callback.returnKeypoints(keypoints);
                image.close();
            }
        });
    }

    private HashMap<String, Keypoint> runPoseInference(ImageProxy image) {
        @SuppressLint("UnsafeOptInUsageError") Bitmap rawImage = imageToBitmap(image.getImage());
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1);
        rawImage = Bitmap.createBitmap(rawImage, 0, 0, rawImage.getWidth(), rawImage.getHeight(), matrix, true);

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(rawImage);
        tensorImage = new Rot90Op(-1).apply(tensorImage);
        tensorImage = new ResizeOp(192, 192, ResizeOp.ResizeMethod.BILINEAR).apply(tensorImage);

        // inference
        LiteModelMovenetSingleposeLightning3.Outputs outputs = model.process(tensorImage.getTensorBuffer());

        float[] outputArray = outputs.getOutputFeature0AsTensorBuffer().getFloatArray();
        if (outputArray.length > 0) {
            FloatBuffer buffer = FloatBuffer.wrap(outputArray);

            HashMap<String, Keypoint> keypoints = new HashMap<>();
            while (buffer.hasRemaining()) {
                Keypoint point = new Keypoint();

                // prescaled to match preview size
                // +- 50 to fix weird offset issue
                point.y = Math.round(buffer.get() * previewView.getWidth()) + 50;
                point.x = Math.round(buffer.get() * previewView.getHeight()) - 50;
                point.confidence = Math.round(buffer.get() * 100); // percent

                // save only shoulder keypoints, rest is only visualised
                switch (buffer.position()) {
                    case 18:
                        keypoints.put("left_shoulder", point);
                        break;
                    case 21:
                        keypoints.put("right_shoulder", point);
                        break;
                    default:
                        keypoints.put(String.valueOf(buffer.position()), point);
                        break;
                }
            }
            return keypoints;
        }
        else {
            return null;
        }
    }

    // check if user posture is good
    // return false if it's bad or true if it's good or there isn't calibrated pose set
    public boolean estimatePose(Map<String, Keypoint> keypoints, CalibratedPose calibratedPose) {
        Keypoint leftShoulder = keypoints.get("left_shoulder");
        Keypoint rightShoulder = keypoints.get("right_shoulder");

        if (leftShoulder != null && rightShoulder != null && calibratedPose.isCalibrated) {
            // check if shoulders are on the same height
            int heightLevelDifference = Math.abs(leftShoulder.y - rightShoulder.y) - calibratedPose.shoulderLevel;
            if (heightLevelDifference > 40) {
                Log.d("Antihump", "=== Wrong pose detected! Shoulders not on the same height, difference:" + heightLevelDifference + " ===");
                return false;
            }

            // distance formula
            int leftX = leftShoulder.x - calibratedPose.leftShoulder.x;
            int leftY = leftShoulder.y - calibratedPose.leftShoulder.y;
            double leftDistance = Math.sqrt((leftX * leftX) + (leftY * leftY));

            int rightX = rightShoulder.x - calibratedPose.rightShoulder.x;
            int rightY = rightShoulder.y - calibratedPose.rightShoulder.y;
            double rightDistance = Math.sqrt((rightX * rightX) + (rightY * rightY));

            if (leftDistance + rightDistance > 60) {
                Log.d("Antihump", "=== Wrong pose detected! Distance between calibrated shoulder points too high, difference: " + (leftDistance + rightDistance) + " ===");
                return false;
            }
            return true;
        }
        else { return true; }
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

    public ShapeDrawable newPoint(int x, int y) {
        ShapeDrawable shape = new ShapeDrawable(new RectShape());
        shape.getPaint().setColor(Color.GREEN);
        shape.setBounds(x - 4, y - 4, x + 8, y + 8);
        return shape;
    }

    private void loadMovenetModel() {
        try {
            model = LiteModelMovenetSingleposeLightning3.newInstance(context);
        }
        catch (IOException e) {
            model.close();
        }
    }

    public static class Keypoint {
        public int x;
        public int y;
        public int confidence;
    }

    public static class CalibratedPose {
        public boolean isCalibrated = false;
        public int shoulderLevel = 0;
        public Keypoint leftShoulder;
        public Keypoint rightShoulder;
    }
}
