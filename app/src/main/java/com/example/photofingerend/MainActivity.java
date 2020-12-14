package com.example.photofingerend;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
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

    public static final int REQUEST_IMAGE_CAPTURE = 1;

    final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private CircleImageView fingerprintImageView;
    private TextView instructionText;
    private TextView resultText;
    private View identifyButton;
    private View showStepsButton;

    public String currentPhotoPath;
    public String outDirPath;

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
                photoFile = createImageFile(".png");
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, "Blad tworzenia pliku (fingerprintImageView.onClick)", Toast.LENGTH_SHORT).show();
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
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", new Locale("pl")).format(new Date());
        String imageFileName = "TEMP_" + timeStamp + "_";
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

    public void processPhoto(View view) {
        identifyButton.setVisibility(View.INVISIBLE);
        instructionText.setText(R.string.wait_tip);

        identify();

    }

    private void identify() {
        // Nowy watek do obliczen w tle aby nie zawieszac watku UI
        executorService.execute(() -> {

            // domyslny wynik, nie znaleziono.
            String result = "nie znaleziono.";

            // jesli udalo sie zaladowac OpenCV to przechodzimy do przetwarzania
            if (openCvLoaded) {
                // przygotowanie folderow wynikow przetwarzania
                File outDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "output");
                outDirPath = outDir.getAbsolutePath();

                boolean success = true;
                if (!outDir.exists()) {
                    success = outDir.mkdirs();
                }
                if (!success) {
                    Toast.makeText(MainActivity.this, "Blad tworzenia folderu (identify)", Toast.LENGTH_SHORT).show();
                }

                ProcessImage pi = new ProcessImage(currentPhotoPath, outDir.getAbsolutePath());

                // usuniecie oryginalu przetworzonego zdjecia
                // TODO usuniecie plikow etapow (po zamknieciu apki / po zrobieniu nowego zdjecia)
                boolean deleted = new File(currentPhotoPath).delete();
                if (!deleted)
                    Toast.makeText(MainActivity.this, "Blad usuwania (identify)", Toast.LENGTH_SHORT).show();

//                // zapisanie przetworzonego zdjecia do png
//                File temp = null;
//                try {
//                    temp = createImageFile(".png");
//                } catch (IOException e) {
//                    // TODO: usuwanie zdjec po identyfikacji
//                    Toast.makeText(MainActivity.this, "Blad tworzenia pliku (identify)", Toast.LENGTH_SHORT).show();
//                }
//                if (temp != null) {
//                    try {
//                        FileOutputStream fos = new FileOutputStream(temp);
//                        pi.getEnchanced().compress(Bitmap.CompressFormat.PNG, 100, fos);
//                        fos.close();
//                    } catch (IOException e) {
//                        Toast.makeText(MainActivity.this, "Blad zapisu pliku (identify)", Toast.LENGTH_SHORT).show();
//                    }
//                }

                // AFIS przetworzonego zdjecia
                byte[] probeImage = null;
                try {
                    probeImage = Files.readAllBytes(new File(outDir, "threshMasked.png").toPath());
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "Blad wczytania pliku (identify)", Toast.LENGTH_SHORT).show();
                }

//                // usuniecie przetworzonego zdjecia po wczytaniu do AFISa
//                deleted = new File(currentPhotoPath).delete();
//                if (!deleted)
//                    Toast.makeText(MainActivity.this, "Blad usuwania (identify)", Toast.LENGTH_SHORT).show();


                if (probeImage != null) {

                    FingerprintTemplate probe = new FingerprintTemplate(new FingerprintImage().dpi(500).decode(probeImage));

                    // Wczytanie wzorcow do listy
                    File dirTemplates = new File("/storage/emulated/0/Android/data/com.example.photofingerend/files/Pictures/Wzorce");
                    File[] files = dirTemplates.listFiles();

                    List<UserDetails> users = new ArrayList<>();

                    if (files != null) {
                        for (File file : files) {
                            try {
                                byte[] serialized = Files.readAllBytes(file.toPath());
                                FingerprintTemplate template = new FingerprintTemplate(serialized);
                                users.add(new UserDetails(new Random().nextInt(150), file.getName(), template));
                            } catch (IOException e) {
                                Toast.makeText(MainActivity.this, "Blad wczytania pliku (identify)", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    // porownanie zdjecia z lista wzorcow
                    UserDetails found = null;
                    if (!users.isEmpty()) {
                        found = find(probe, users);
                    }

                    if (found != null)
                        result = found.name;
                } else {
                    Toast.makeText(MainActivity.this, "Blad wczytania pliku (identify)", Toast.LENGTH_SHORT).show();
                }
            }

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
//        for (File file : dirImgs.listFiles()) {
////        for (File file : dirImgs.listFiles()) {
//            ProcessImage pi = new ProcessImage(file.toPath().toString());
//
//            int i = file.getName().lastIndexOf('.');
//            String name = file.getName().substring(0, i);
//
//            File skeletonImg = new File(dirSkeletons, name + ".png");
//
//            try {
//                FileOutputStream fos = new FileOutputStream(skeletonImg);
//                pi.getEnchanced().compress(Bitmap.CompressFormat.PNG, 100, fos);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
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
        Intent intent = new Intent(this, ShowStepsActivity.class);
        intent.putExtra("outDirPath", outDirPath);
        startActivity(intent);
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

    static class UserDetails {
        final int id;
        final String name;
        final FingerprintTemplate template;

        public UserDetails(int id, String name, FingerprintTemplate template) {
            this.id = id;
            this.name = name;
            this.template = template;
        }
    }

}