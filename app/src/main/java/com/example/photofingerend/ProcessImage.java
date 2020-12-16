package com.example.photofingerend;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProcessImage {

    private final Bitmap result;

    public ProcessImage(String photoPath, String outDirPath) {
        this.result = processImage(photoPath, outDirPath);
    }

    public ProcessImage(String photoPath) {
        this.result = processImage(photoPath, null);
    }

    public Bitmap getResult() {
        return result;
    }


    // TODO: dodac etapy przetwarzania (szkieletonizacja, minucje, mapa orientacji)
    private Bitmap processImage(String inputPath, String outDirPath) {
        /// Open file from supplied path
        Mat src = Imgcodecs.imread(inputPath);

        /// Wstepne przycinanie zdjecia do srodka do rozmiaru 1000x1000
        Rect roi = new Rect(src.width() / 2 - 500, src.height() / 2 - 500, 1000, 1000);
        Mat cropped = new Mat(src, roi);

        /// Resize image (downscale)
        Size resizedSize = new Size(600, 600);
        Mat resized = new Mat();
        Imgproc.resize(cropped, resized, resizedSize, 0, 0, Imgproc.INTER_AREA);

        /// Get skin region
        Mat skinMask = getSkinMask(resized);

        /// Convert to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY);

        /// Apply mask
        Mat grayMasked = new Mat();
        Core.bitwise_and(gray, gray, grayMasked, skinMask);

        /// Equalize histogram
        CLAHE clahe = Imgproc.createCLAHE(32.0, new Size(100, 100));
        Mat equalized = new Mat();
        clahe.apply(grayMasked, equalized);

        /// Block size for processing
        int blockSize = 16;

        /// Get ridges mask
        Mat ridgesMask = findRidges(equalized, skinMask, blockSize, 0.35);

        /// Blur the equalized to remove noise and preserve edges
        Mat filtered = new Mat();
        Imgproc.bilateralFilter(equalized, filtered, 5, 200, 200);

        /// Threshold the image with adaptive thresholding
        Mat threshed = new Mat();
        Imgproc.adaptiveThreshold(filtered, threshed, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 1);
        // Invert image so ridges are black.
        Core.bitwise_not(threshed, threshed);

        /// Merge two masks to not include something we do not want to process
        Mat mask = new Mat();
        Core.bitwise_and(skinMask, ridgesMask, mask);

        /// Apply mask to threshed image
        Mat threshMasked = new Mat();
        Core.bitwise_and(threshed, mask, threshMasked);

        if (outDirPath != null) {
            Imgcodecs.imwrite(outDirPath + File.separator + "0original.png", src);
            Imgcodecs.imwrite(outDirPath + File.separator + "1resized.png", resized);
            Imgcodecs.imwrite(outDirPath + File.separator + "2skinMask.png", skinMask);
            Imgcodecs.imwrite(outDirPath + File.separator + "3gray.png", gray);
//            Imgcodecs.imwrite(outDirPath + File.separator + "4grayMasked.png", grayMasked);
            Imgcodecs.imwrite(outDirPath + File.separator + "5equalized.png", equalized);
            Imgcodecs.imwrite(outDirPath + File.separator + "6ridgesMask.png", ridgesMask);
            Imgcodecs.imwrite(outDirPath + File.separator + "7filtered.png", filtered);
//            Imgcodecs.imwrite(outDirPath + File.separator + "8threshed.png", threshed);
            Imgcodecs.imwrite(outDirPath + File.separator + "result.png", threshMasked);
        }

        return grayMatToBmp(threshMasked);
    }

    private Mat findRidges(Mat src, Mat mask, int blockSize, double threshold) {
        Size srcSize = src.size();
        Mat varianceImg = new Mat(srcSize, CvType.CV_32F);
        Mat ridgeMask = new Mat(srcSize, CvType.CV_8U);

        MatOfDouble std = new MatOfDouble();

        Core.meanStdDev(src, new MatOfDouble(), std, mask);

        threshold = std.toArray()[0] * threshold;

        for (int x = 0; x < srcSize.width - blockSize; x += blockSize) {
            for (int y = 0; y < srcSize.height - blockSize; y += blockSize) {
                Core.meanStdDev(src.submat(y, y + blockSize, x, x + blockSize), new MatOfDouble(), std, mask.submat(y, y + blockSize, x, x + blockSize));
                double variance = std.toArray()[0];
                varianceImg.submat(y, y + blockSize, x, x + blockSize).setTo(Scalar.all(variance));
            }
        }

        Core.compare(varianceImg, new Scalar(threshold), ridgeMask, Core.CMP_GT);

        Imgproc.morphologyEx(ridgeMask, ridgeMask, Imgproc.MORPH_CLOSE, Mat.ones(new Size(17, 17), CvType.CV_8U));
        Imgproc.erode(ridgeMask, ridgeMask, Mat.ones(new Size(51, 51), CvType.CV_8U));

        return ridgeMask;
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
        Mat kernel = new Mat(new Size(9, 9), CvType.CV_8U, Scalar.all(1));

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

        // wypelnienie najwiekszego konturu na bialo (lub na 1)
        Mat skinMask = new Mat(_src.width(), _src.height(), CvType.CV_8U, Scalar.all(0));
        Imgproc.fillPoly(skinMask, cnt, Scalar.all(255));

        return skinMask;
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
            e.printStackTrace();
            return null;
        }
    }

}
