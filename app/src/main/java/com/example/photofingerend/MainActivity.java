package com.example.photofingerend;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
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

    private boolean openCvLoaded = false;

    /*
    Zaladowanie biblioteki openCV
    */
    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                openCvLoaded = true;
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

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
                photoFile = createImageFile(".jpg");
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

    private File createImageFile(String ext) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ext,
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void processPhoto(View view) {
        resultText.setVisibility(View.INVISIBLE);
        instructionText.setText(R.string.wait_tip);

        testowa();

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void testowa() {
        executorService.execute(() -> {

            String result = "";

            if (openCvLoaded) {
                ProcessImage pi = new ProcessImage("/storage/emulated/0/Android/data/com.example.photofingerend/files/Pictures/lw.png");
//                ProcessImage pi = new ProcessImage(currentPhotoPath);

                File temp = null;
                try {
                    temp = createImageFile(".png");
                } catch (IOException e) {
                    // TODO: obsluga gdy nie da sie utworzyc pliku do zdjecia
                    // TODO: usuwanie zdjec po identyfikacji
                    e.printStackTrace();
                }

                byte[] probeImage = new byte[0];
                try {
                    FileOutputStream fos = new FileOutputStream(temp);
                    pi.getEnchanced().compress(Bitmap.CompressFormat.PNG, 100, fos);
                    probeImage = Files.readAllBytes(temp.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ////            try {
////                FileOutputStream fos = new FileOutputStream(skeletonImg);
////                pi.getEnchanced().compress(Bitmap.CompressFormat.PNG, 100, fos);
////            } catch (IOException e) {
////                e.printStackTrace();
////            }

                FingerprintTemplate probe = new FingerprintTemplate(
                        new FingerprintImage()
                                .dpi(500)
                                .decode(probeImage));

                File dirTemplates = new File("/storage/emulated/0/Android/data/com.example.photofingerend/files/Pictures/Wzorce");

                List<UserDetails> users = new ArrayList<>();

                for (File file : dirTemplates.listFiles()) {
                    byte[] serialized = new byte[0];
                    try {
                        serialized = Files.readAllBytes(file.toPath());
                        FingerprintTemplate template = new FingerprintTemplate(serialized);
                        users.add(new UserDetails(new Random().nextInt(150), file.getName(), template));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

                UserDetails found = find(probe, users);

                if(found != null)
                    result = found.name;

            }

            // otworz tutaj zdjecie i wyslij do klasy przetwarzajacej obraz

            // klasa co ma metody do przetwarzania tego obrazu
            // i z tej klasy jest zwrocone imie osoby
            // maja byc tez zwrocone obrazy poszczegolnych etapow (przypisac do odpowiednich pol statycznych ShowActivity)

            // po skonczeniu identyfikacji odswiezamy watek UI i konczymy prace (powracajac do watku UI)
            String finalResult = result;
            runOnUiThread(() -> {
                // pokaz instruction text i napisz czyj to palec
                resultText.setText(String.format(getResources().getString(R.string.result_person), finalResult));
                resultText.setVisibility(View.VISIBLE);

                instructionText.setText(R.string.show_steps_tip);

                showStepsButton.setVisibility(View.VISIBLE);
            });
        });
    }

//    @RequiresApi(api = Build.VERSION_CODES.O)
//    private void dodajBaze() {
//        File dirImgs = new File("/storage/emulated/0/Android/data/com.example.photofingerend/files/Pictures/Baza/Obrazy");
//        File dirSkeletons = new File("/storage/emulated/0/Android/data/com.example.photofingerend/files/Pictures/Baza/Szkielety");
//        File dirTemplates = new File("/storage/emulated/0/Android/data/com.example.photofingerend/files/Pictures/Baza/Wzorce");
//
//        for (File file : dirSkeletons.listFiles()) {
////        for (File file : dirImgs.listFiles()) {
////            ProcessImage pi = new ProcessImage(file.toPath().toString());
////
////            int i = file.getName().lastIndexOf('.');
////            String name = file.getName().substring(0, i);
////
////            File skeletonImg = new File(dirSkeletons, name + ".png");
////
////            try {
////                FileOutputStream fos = new FileOutputStream(skeletonImg);
////                pi.getEnchanced().compress(Bitmap.CompressFormat.PNG, 100, fos);
////            } catch (IOException e) {
////                e.printStackTrace();
////            }
////
//            byte[] image = new byte[0];
//            try {
//                image = Files.readAllBytes(file.toPath());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            FingerprintTemplate template = new FingerprintTemplate(
//                    new FingerprintImage()
//                            .dpi(500)
//                            .decode(image));
//            byte[] serialized = template.toByteArray();
//
//            int i = file.getName().lastIndexOf('.');
//            String name = file.getName().substring(0, i);
//            File templateFile = new File(dirTemplates, name + ".json.gz");
//            try {
//                FileOutputStream fos = new FileOutputStream(templateFile);
//                fos.write(serialized);
//                fos.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        System.out.println("brk pt");
////        byte[] image = Files.readAllBytes(Paths.get("fingerprint.jpeg"));
////        FingerprintTemplate template = new FingerprintTemplate(
////                new FingerprintImage()
////                        .dpi(500)
////                        .decode(image));
////        byte[] serialized = template.toByteArray();
//    }

    public void showSteps(View view) {
        // uruchom nowe activity i tam wyswietlaj te zdj etapow ktore zostaly juz zainicjalizowane
    }

    static class UserDetails {
        int id;
        String name;
        FingerprintTemplate template;

        public UserDetails(int id, String name, FingerprintTemplate template) {
            this.id = id;
            this.name = name;
            this.template = template;
        }
    }

    UserDetails find(FingerprintTemplate probe, Iterable<UserDetails> candidates) {
        FingerprintMatcher matcher = new FingerprintMatcher().index(probe);
        UserDetails match = null;
        double high = 0;
        for (UserDetails candidate : candidates) {
            double score = matcher.match(candidate.template);
            if (score > high) {
                high = score;
                match = candidate;
            }
        }
        double threshold = 30;
        return high >= threshold ? match : null;
    }

}