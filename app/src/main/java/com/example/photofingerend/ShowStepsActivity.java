package com.example.photofingerend;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.Map;

public class ShowStepsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_steps);

        Intent intent = getIntent();
        String outDirPath = intent.getExtras().getString("outDirPath");

//        for (File file : new File(outDirPath).listFiles()) {
//
//        }

        File[] files = new File(outDirPath).listFiles();

        ImageView step = findViewById(R.id.stepView);

        Bitmap bMap = BitmapFactory.decodeFile(files[0].getAbsolutePath());

        step.setImageBitmap(bMap);

    }
}