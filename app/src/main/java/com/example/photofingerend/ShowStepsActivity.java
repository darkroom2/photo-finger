package com.example.photofingerend;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ShowStepsActivity extends AppCompatActivity {

    Map<String, String> dictionary;
    private File[] fileArray;
    private ImageView stepView;
    private TextView stepText;
    private int index;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_steps);

        initializeDict();

        Intent intent = getIntent();
        String outDirPath = intent.getExtras().getString("outDirPath");

        fileArray = new File(outDirPath).listFiles();
        index = 0;

        stepView = findViewById(R.id.stepView);
        stepText = findViewById(R.id.stepTextView);

        if (fileArray != null) {
            Arrays.sort(fileArray, (a, b) -> a.getName().compareTo(b.getName()));
            stepView.setImageBitmap(BitmapFactory.decodeFile(fileArray[index].getAbsolutePath()));
            stepText.setText(String.format(getResources().getString(R.string.step_description), dictionary.get(fileArray[index].getName())));
            stepView.setOnTouchListener((v, event) -> {

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if ((int) event.getX() < getResources().getDisplayMetrics().widthPixels / 2) {
                        previousPhoto();
                    } else {
                        nextPhoto();
                    }
                }

                return true;
            });
        }
    }

    private void initializeDict() {
        dictionary = new HashMap<>();
        dictionary.put("0src.png", "Step 0: Original image");
        dictionary.put("1resized.png", "Step 1: Crop image & resize");
        dictionary.put("2skinMask.png", "Step 2: Find skin mask");
        dictionary.put("3grayMasked.png", "Step 3: Grayscale conversion");
        dictionary.put("4equalized.png", "Step 4: Histogram equalization");
        dictionary.put("5ridgesMask.png", "Step 5: Find ridges mask");
        dictionary.put("6blurred.png", "Step 6: Blur image & Enhance edges");
        dictionary.put("7result.png", "Step 7: Apply thresholding & mask");
//        dictionary.put("visualisation.png", "Orientation map");
//        dictionary.put("gaborThreshed.png", "Gabor threshed");
//        dictionary.put("skeletonized.png", "Skeletonized");
//        dictionary.put("minutiaes.png", "Minutiaes");
    }

    private void nextPhoto() {
        if (index + 1 < fileArray.length) {
            ++index;
            File temp = fileArray[index];
            stepView.setImageBitmap(BitmapFactory.decodeFile(temp.getAbsolutePath()));
            stepText.setText(String.format(getResources().getString(R.string.step_description), dictionary.get(temp.getName())));
        }
    }

    private void previousPhoto() {
        if (index - 1 >= 0) {
            --index;
            File temp = fileArray[index];
            stepView.setImageBitmap(BitmapFactory.decodeFile(temp.getAbsolutePath()));
            stepText.setText(String.format(getResources().getString(R.string.step_description), dictionary.get(temp.getName())));
        }
    }
}