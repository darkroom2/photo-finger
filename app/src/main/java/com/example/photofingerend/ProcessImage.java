
package com.example.photofingerend;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ProcessImage {

    private final Mat original;

    public ProcessImage(String photoPath) {
        original = Imgcodecs.imread(photoPath); // BGR order
    }

    public void processImage() {
        /// Original input
        Mat src = original.clone();
        Bitmap bSrc = matToBmp(src);
        System.out.println("wyswietl oryginal brejkpojnt");

        /// Resize image (downscale)
        Size resizedSize = new Size(600, 600);
        Mat resized = new Mat();
        Imgproc.resize(src, resized, resizedSize, 0, 0, Imgproc.INTER_AREA);
        Bitmap bResized = matToBmp(resized);
        System.out.println("wyswietl resized brejkpojnt");

        /// Get skin region
        Mat skinMask = getSkinMask(resized);
        Bitmap bSkinMask = grayMatToBmp(skinMask);
        System.out.println("wyswietl skinMask brejkpojnt");

        /// Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY);
        Bitmap bGray = grayMatToBmp(gray);
        System.out.println("wyswietl bGray brejkpojnt");

        /// Apply mask
        Mat grayMasked = new Mat();
        Core.bitwise_and(gray, gray, grayMasked, skinMask);
        Bitmap bGrayMasked = grayMatToBmp(grayMasked);
        System.out.println("wyswietl bGrayMasked brejkpojnt");
//
//        """Equalize histogram"""
//        clahe = cv.createCLAHE(clipLimit=32.0, tileGridSize=(100, 100))
//        equalized = clahe.apply(gray)
//    # equalized = cv.equalizeHist(gray)
//    # cv.imshow('equalized', equalized)
//    # cv.waitKey()
//
//        block_size = 16
//
//        """Find ridges mask"""
//        mask2 = findRidges(resized, block_size, 0.35)
//    # cv.imshow('mask', mask * 255)
//    # cv.waitKey()
//
//        """Blur the equalized to remove noise and preserve edges"""
//    # kernel = np.ones((5, 5), np.float32) / 25
//    # blurred = cv.filter2D(equalized, -1, kernel)
//        blurred = cv.bilateralFilter(equalized, 5, 200, 200)
//    # blurred = cv.GaussianBlur(equalized, (5, 5), 2)
//    # cv.imshow('blurred', blurred)
//    # cv.waitKey()
//
//        """Threshold the image with adaptive thresholding"""
//        threshed = cv.adaptiveThreshold(blurred, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 11, 1)
//    # threshed = cv.threshold(blurred, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU)[1]
//
//        return 255 - threshed

    }

    private Mat getSkinMask(Mat src) {
        Mat _src = src.clone();

        // zmiana przestrzeni barw RGB na HSV.
        Mat imgHsv = new Mat();
        Imgproc.cvtColor(_src, imgHsv, Imgproc.COLOR_BGR2HSV);

        // gorne i dolne granice koloru skory czlowieka (w HSV)
        Scalar lowerBounds = new Scalar(0, 10, 60);
        Scalar upperBounds = new Scalar(25, 220, 255);

        // maskowanie pikseli nie znajdujacych sie w granicach koloru skory
        Mat mask = new Mat();
        Core.inRange(imgHsv, lowerBounds, upperBounds, mask);

        // jadro operacji morfologicznej otwierania i zamykania
        Mat kernel = new Mat(new Size(9, 9), CvType.CV_8UC1, Scalar.all(1));

        // Otwarcie usuwa małe obiekty z pierwszego planu (zwykle brane jako jasne piksele) obrazu, umieszczając je w tle
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);

        // Zamknięcie usuwa małe otwory w pierwszym planie, zmieniając małe wysepki tła na pierwszy plan.
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);

        // znalezienie konturow obiektow
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // znajdz najwiekszy kontur
        double maxArea = -1;
        int maxAreaIdx = -1;
        for (int idx = 0; idx < contours.size(); idx++) {
            Mat contour = contours.get(idx);
            double contourarea = Imgproc.contourArea(contour);
            if (contourarea > maxArea) {
                maxArea = contourarea;
                maxAreaIdx = idx;
            }
        }

        // najwiekszy kontur
        List<MatOfPoint> cnt = new ArrayList<>();
        cnt.add(contours.get(maxAreaIdx));

        // wypelnienie najwiekszego konturu na bialo (na 1) TODO: zmienic na 1 z 255
        Mat skinMask = new Mat(_src.width(), _src.height(), CvType.CV_8UC1, Scalar.all(0));
        Imgproc.fillPoly(skinMask, cnt, Scalar.all(1));

//        Mat end = new Mat();
//        Core.bitwise_and(_src, _src, end, skinMask);
//        return end;

        return skinMask;
    }

    private Bitmap matToBmp(Mat src) {
        Mat _src = src.clone();
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(_src.cols(), _src.rows(), Bitmap.Config.ARGB_8888);
            Imgproc.cvtColor(_src, _src, Imgproc.COLOR_BGR2RGB);
            Utils.matToBitmap(_src, bmp);
            return bmp;
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
            return null;
        }
    }

    private Bitmap grayMatToBmp(Mat src) {
        Mat _src = src.clone();
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(_src.cols(), _src.rows(), Bitmap.Config.ARGB_8888);
            Imgproc.cvtColor(_src, _src, Imgproc.COLOR_GRAY2RGB);
            Utils.matToBitmap(_src, bmp);
            return bmp;
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
            return null;
        }
    }
}
