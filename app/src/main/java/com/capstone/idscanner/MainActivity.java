package com.capstone.idscanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.RequestManager;
import com.capstone.idscanner.Utility.NetworkChangeReceiver;
import com.capstone.idscanner.Utility.NetworkConnectivityChecker;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.storage.StorageReference;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.droidsonroids.gif.GifImageView;

public class MainActivity extends AppCompatActivity {

    private ImageButton getImageBtn, calendarBtn;
    private GifImageView loadingGif;
    private ImageView idPreview;
    private Uri imageUri;
    private AlertDialog.Builder builder;
    private AlertDialog optionsDialog, cancelScanIDDialog, noInternetDialog;
    private TextRecognizer textRecognizer;
    private static final int CAMERA_REQUEST_CODE = 1;
    private static final int GALLERY_REQUEST_CODE = 2;
    private static final int CAMERA_PERMISSION_REQUEST = 101;
    private static final int STORAGE_PERMISSION_REQUEST = 102;
    private String TAG = "DEBUG";
    private NetworkChangeReceiver networkChangeReceiver;
    private StorageReference storageRef;
    private StorageReference imagesRef;
    private StorageReference fileRef;
    private RequestManager requestManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeNetworkChecker();
        checkPermission();

        getImageBtn = findViewById(R.id.getImageBtn);
        idPreview = findViewById(R.id.idPreview);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        getImageBtn.setOnClickListener(v -> {
//            ImagePicker.with(this)
//                    .crop()                    //Crop image(Optional), Check Customization for more option
//                    .compress(1024)            //Final image size will be less than 1 MB(Optional)
//                    .maxResultSize(1080, 1080)    //Final image resolution will be less than 1080 x 1080(Optional)
//                    .start();
//
            showOptionsDialog();
        });

    }

    @Override
    protected void onPause() {
        super.onPause();

        closeNoInternetDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (networkChangeReceiver != null) {
            unregisterReceiver(networkChangeReceiver);
        }

        closeNoInternetDialog();
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
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        if (galleryIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
            if (optionsDialog != null && optionsDialog.isShowing()) {
                optionsDialog.dismiss();
            }

        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
            if (optionsDialog != null && optionsDialog.isShowing()) {
                optionsDialog.dismiss();
            }

        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptionsDialog() {
        builder = new AlertDialog.Builder(this);

        View dialogView = getLayoutInflater().inflate(R.layout.camera_gallery_dialog, null);

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
            if (optionsDialog != null && optionsDialog.isShowing()) {
                optionsDialog.dismiss();
            }
        });

        builder.setView(dialogView);

        optionsDialog = builder.create();
        optionsDialog.show();
    }

    private boolean matchesPattern(String text, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        return m.find();
    }

    private Uri getImageUri(Context context, Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, "Title", null);
        return Uri.parse(path);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (resultCode == RESULT_OK) {
                if (requestCode == CAMERA_REQUEST_CODE) {
                    if (data != null) {

                        Bundle extras = data.getExtras();
                        Bitmap imageBitmap = (Bitmap) extras.get("data");

                        imageUri = getImageUri(getApplicationContext(), imageBitmap);
                        idPreview.setImageURI(imageUri);

                        Toast.makeText(this, "Image is Loaded from Camera", Toast.LENGTH_LONG).show();

                    } else {
                        Toast.makeText(this, "Image is not Selected", Toast.LENGTH_LONG).show();
                    }

                } else if (requestCode == GALLERY_REQUEST_CODE) {
                    if (data != null) {

                        imageUri = data.getData();
                        idPreview.setImageURI(imageUri);

                        Toast.makeText(this, "Image is Loaded from Gallery", Toast.LENGTH_LONG).show();

                    } else {
                        Toast.makeText(this, "Image is not Selected", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                Toast.makeText(this, "Image is not Selected", Toast.LENGTH_SHORT).show();
                Log.e(TAG + ":ERROR", "Failed to Extract Text");

                Toast.makeText(this, "Failed to Extract Text", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void recognizeText() {

        if (imageUri != null) {

            try {
                InputImage inputImage = InputImage.fromFilePath(MainActivity.this, imageUri);

                textRecognizer.process(inputImage).addOnSuccessListener(text -> {

                    String extractedText = text.getText();


                    // Define patterns for PWD and Senior Citizen IDs
                    String pwdPattern = "PWD-[0-9]{4}";
                    String seniorCitizenPattern = "SeniorCitizen-[0-9]{4}";


                    // Check if extracted text matches patterns
//                    if (matchesPattern(extractedText, pwdPattern)) {
//                        recognizedTextView.setText(extractedText);
//                    } else if (matchesPattern(extractedText, seniorCitizenPattern)) {
//                        recognizedTextView.setText(extractedText);
//                    } else {
//                        recognizedTextView.setText("Please use an ID");
//                    }


                }).addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();

                    e.printStackTrace();

                });


            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG + ":ERROR", "Failed to Extract Text");


            }
        } else {
            Toast.makeText(this, "Image is not loaded", Toast.LENGTH_LONG).show();
            Toast.makeText(this, "Failed to Extract Text", Toast.LENGTH_LONG).show();

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
