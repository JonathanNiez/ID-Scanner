package com.capstone.idscanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.bumptech.glide.RequestManager;
import com.capstone.idscanner.Utility.NetworkChangeReceiver;
import com.capstone.idscanner.Utility.NetworkConnectivityChecker;
import com.capstone.idscanner.databinding.ActivityMainBinding;
import com.capstone.idscanner.ml.Model;
import com.capstone.idscanner.ml.ModelUnquant;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private Uri imageUri;
    private Bitmap bitmap;
    private AlertDialog.Builder builder;
    private AlertDialog optionsDialog, cancelScanIDDialog, noInternetDialog,
            exitAppDialog;
    private TextRecognizer textRecognizer;
    private static final int CAMERA_REQUEST_CODE = 1;
    private static final int GALLERY_REQUEST_CODE = 2;
    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final int STORAGE_PERMISSION_REQUEST = 102;
    private final String TAG = "DEBUG";
    private boolean shouldExit = false;
    private Intent cameraIntent, galleryIntent;
    private NetworkChangeReceiver networkChangeReceiver;
    private StorageReference storageRef;
    private StorageReference imagesRef;
    private StorageReference fileRef;
    private RequestManager requestManager;
    private ActivityMainBinding binding;
    private BufferedReader bufferedReader;
    private int count = 0;
    private final int imageSize = 224;
    private String[] labels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeNetworkChecker();
        checkPermission();
        initializeLabels();

        binding.getImageBtn.setOnClickListener(v -> {
//            ImagePicker.with(this)
//                    .crop()                    //Crop image(Optional), Check Customization for more option
//                    .compress(1024)            //Final image size will be less than 1 MB(Optional)
//                    .maxResultSize(1080, 1080)    //Final image resolution will be less than 1080 x 1080(Optional)
//                    .start();
//
            showImageOptionsDialog();
        });

        binding.imgBackBtn.setOnClickListener(v -> {
            showExitConfirmationDialog();
        });

    }

    @Override
    protected void onPause() {
        super.onPause();

        closeImageOptionsDialog();
        closeNoInternetDialog();
        closeExitConfirmationDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (networkChangeReceiver != null) {
            unregisterReceiver(networkChangeReceiver);
        }

        closeImageOptionsDialog();
        closeNoInternetDialog();
        closeExitConfirmationDialog();
    }

    @Override
    public void onBackPressed() {

        if (shouldExit) {
            super.onBackPressed(); // Exit the app
        } else {
            // Show an exit confirmation dialog
            showExitConfirmationDialog();
        }
    }

    private void exitApp() {
        shouldExit = true;
        onBackPressed();

        finish();
    }

    private void initializeLabels() {
        labels = new String[1000];
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(getAssets().open("labels.txt")));
            String line = bufferedReader.readLine();

            while (line != null) {
                labels[count] = line;
                count++;
                line = bufferedReader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void classifyID(Bitmap bitmap) {
        try {
            ModelUnquant model = ModelUnquant.newInstance(getApplicationContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            int pixel = 0;
            for (int n = 0; n < imageSize; n++) {
                for (int i = 0; i < imageSize; i++) {
                    int val = intValues[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            ModelUnquant.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            float[] confidences = outputFeature0.getFloatArray();
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }
            String[] classes = {"Driver's License", "Senior Citizen ID", "PWD ID"};
            binding.resultTextView.setText(classes[maxPos]);

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        try {
//            Model model = Model.newInstance(this);
//
//            // Creates inputs for reference.
//            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.UINT8);
//
//            bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
//            inputFeature0.loadBuffer(TensorImage.fromBitmap(bitmap).getBuffer());
//
//            // Runs model inference and gets result.
//            Model.Outputs outputs = model.process(inputFeature0);
//
//            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
//
//            binding.resultTextView.setText(labels[getMax(outputFeature0.getFloatArray())]);
//
//            // Releases model resources if no longer used.
//            model.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    private int getMax(float[] arr) {
        int max = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > arr[max]) max = 1;
        }

        return max;
    }

    private void checkPermission() {
        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        }

        // Check for storage permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
        }
    }

    private void openGallery() {
        galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        if (galleryIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
            closeImageOptionsDialog();

        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            closeImageOptionsDialog();

        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImageOptionsDialog() {
        builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_camera_gallery, null);

        Button openCameraBtn = dialogView.findViewById(R.id.openCameraBtn);
        Button openGalleryBtn = dialogView.findViewById(R.id.openGalleryBtn);
        Button cancelBtn = dialogView.findViewById(R.id.cancelBtn);

        openCameraBtn.setOnClickListener(v -> {
            openCamera();
        });

        openGalleryBtn.setOnClickListener(v -> {
            openGallery();
        });

        cancelBtn.setOnClickListener(v -> {
            closeImageOptionsDialog();
        });

        builder.setView(dialogView);

        optionsDialog = builder.create();
        optionsDialog.show();
    }

    private void closeImageOptionsDialog() {
        if (optionsDialog != null && optionsDialog.isShowing()) {
            optionsDialog.dismiss();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (resultCode == RESULT_OK) {
                if (requestCode == CAMERA_REQUEST_CODE) {
                    if (data != null) {

//                        Bundle extras = data.getExtras();
//                        Bitmap imageBitmap = (Bitmap) extras.get("data");
//                        imageUri = getImageUri(getApplicationContext(), bitmap);

                        binding.idPreview.setVisibility(View.VISIBLE);
                        binding.textViewGuide.setVisibility(View.GONE);
                        binding.resultTextView.setVisibility(View.VISIBLE);

                        bitmap = (Bitmap) data.getExtras().get("data");
                        int dimension = Math.min(bitmap.getWidth(), bitmap.getHeight());
                        bitmap = ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension);
                        binding.idPreview.setImageBitmap(bitmap);

                        bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, false);
                        classifyID(bitmap);

                        Toast.makeText(this, "Image is Loaded from Camera", Toast.LENGTH_LONG).show();

                    } else {
                        Toast.makeText(this, "Image is not Selected", Toast.LENGTH_LONG).show();
                    }

                } else if (requestCode == GALLERY_REQUEST_CODE) {
                    if (data != null) {

                        binding.idPreview.setVisibility(View.VISIBLE);
                        binding.textViewGuide.setVisibility(View.GONE);
                        binding.resultTextView.setVisibility(View.VISIBLE);
//                        imageUri = data.getData();
//                        binding.idPreview.setImageURI(imageUri);

                        Uri uri = data.getData();
                        bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                        binding.idPreview.setImageBitmap(bitmap);

                        classifyID(bitmap);

                        Toast.makeText(this, "Image is Loaded from Gallery", Toast.LENGTH_LONG).show();

                    } else {
                        Toast.makeText(this, "Image is not Selected", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                Toast.makeText(this, "Image is not Selected", Toast.LENGTH_SHORT).show();
                Toast.makeText(this, "Failed to Extract Text", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera Permission Granted");
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Gallery Permission Granted");
            }
        } else {
            Log.e(TAG, "Permission Denied");

        }
    }

    private void showNoInternetDialog() {
        builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_internet, null);

        Button tryAgainBtn = dialogView.findViewById(R.id.tryAgainBtn);

        tryAgainBtn.setOnClickListener(v -> {
            closeNoInternetDialog();
        });

        builder.setView(dialogView);

        noInternetDialog = builder.create();
        noInternetDialog.show();
    }

    private void showExitConfirmationDialog() {
        builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_exit_app, null);

        Button yesBtn = dialogView.findViewById(R.id.yesBtn);
        Button noBtn = dialogView.findViewById(R.id.noBtn);

        yesBtn.setOnClickListener(v -> {
            exitApp();
        });

        noBtn.setOnClickListener(v -> {
            closeExitConfirmationDialog();
        });

        builder.setView(dialogView);

        exitAppDialog = builder.create();
        exitAppDialog.show();
    }

    private void closeExitConfirmationDialog() {
        if (exitAppDialog != null && exitAppDialog.isShowing()) {
            exitAppDialog.dismiss();
        }
    }

    private void closeNoInternetDialog() {
        if (noInternetDialog != null && noInternetDialog.isShowing()) {
            noInternetDialog.dismiss();

            boolean isConnected = NetworkConnectivityChecker.isNetworkConnected(this);
            updateConnectionStatus(isConnected);

        }
    }

    private void initializeNetworkChecker() {
        networkChangeReceiver = new NetworkChangeReceiver(new NetworkChangeReceiver.NetworkChangeListener() {
            @Override
            public void onNetworkChanged(boolean isConnected) {
                updateConnectionStatus(isConnected);
            }
        });

        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkChangeReceiver, intentFilter);

        // Initial network status check
        boolean isConnected = NetworkConnectivityChecker.isNetworkConnected(this);
        updateConnectionStatus(isConnected);

    }

    private void updateConnectionStatus(boolean isConnected) {
        if (isConnected) {
            if (noInternetDialog != null && noInternetDialog.isShowing()) {
                noInternetDialog.dismiss();
            }
        } else {
            showNoInternetDialog();
        }
    }
}
