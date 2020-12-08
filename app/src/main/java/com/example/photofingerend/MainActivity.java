package com.example.photofingerend;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static final int REQUEST_IMAGE_CAPTURE = 1;

    private CircleImageView fingerprintImageView;
    private TextView instructionText;
    private TextView resultText;
    private View identifyButton;
    private View showStepsButton;

    public String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instructionText = findViewById(R.id.instructionText);
        resultText = findViewById(R.id.resultText);

        identifyButton = findViewById(R.id.identifyButton);
        showStepsButton = findViewById(R.id.showStepsButton);

        fingerprintImageView = findViewById(R.id.fingerprintImage);
        fingerprintImageView.setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                // TODO: obsluga gdy nie da sie utworzyc pliku do zdjecia
                // TODO: usuwanie zdjec po identyfikacji
                e.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                        "com.example.photofingerend.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        identifyButton.setVisibility(View.VISIBLE);
        resultText.setVisibility(View.INVISIBLE);
        showStepsButton.setVisibility(View.INVISIBLE);

        instructionText.setText(R.string.identify_tip);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            setPic();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void setPic() {
        // Get the dimensions of the View
        int targetW = fingerprintImageView.getWidth();
        int targetH = fingerprintImageView.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.max(1, Math.min(photoW / targetW, photoH / targetH));

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        fingerprintImageView.setImageBitmap(bitmap);
    }

    public void processPhoto(View view) {
        resultText.setVisibility(View.INVISIBLE);
        instructionText.setText(R.string.wait_tip);

        executorService.execute(() -> {

            ProcessImage pi = new ProcessImage(currentPhotoPath);
            pi.processImage();

            // otworz tutaj zdjecie i wyslij do klasy przetwarzajacej obraz

            // klasa co ma metody do przetwarzania tego obrazu
            // i z tej klasy jest zwrocone imie osoby
            // maja byc tez zwrocone obrazy poszczegolnych etapow (przypisac do odpowiednich pol statycznych ShowActivity)
            String result = "Radek";

            // po skonczeniu identyfikacji odswiezamy watek UI i konczymy prace (powracajac do watku UI)
            runOnUiThread(() -> {
                // pokaz instruction text i napisz czyj to palec
                resultText.setText(String.format(getResources().getString(R.string.result_person), result));
                resultText.setVisibility(View.VISIBLE);

                instructionText.setText(R.string.show_steps_tip);

                showStepsButton.setVisibility(View.VISIBLE);
            });
        });
    }

    public void showSteps(View view) {
        // uruchom nowe activity i tam wyswietlaj te zdj etapow ktore zostaly juz zainicjalizowane
    }
}