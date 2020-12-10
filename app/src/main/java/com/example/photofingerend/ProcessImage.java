
package com.example.photofingerend;

import android.graphics.Bitmap;
import android.util.Log;

import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ProcessImage {

    private final Bitmap original;
    private final Bitmap enhanced;

    public ProcessImage(String photoPath) {
        Mat original = Imgcodecs.imread(photoPath); // BGR order
        this.original = matToBmp(original);

        Mat enhanced = processImage(original);
        this.enhanced = grayMatToBmp(enhanced);
    }

    public Mat processImage(Mat input) {
        /// Original input
        Mat src = input.clone();
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

        /// Equalize histogram
        CLAHE clahe = Imgproc.createCLAHE(32.0, new Size(100, 100));
        Mat equalized = new Mat();
        clahe.apply(grayMasked, equalized);
        Bitmap bEqualized = grayMatToBmp(equalized);
        System.out.println("wyswietl bEqualized brejkpojnt");

        /// Block size for processing
        int blockSize = 16;

        /// Get ridges mask
        Mat ridgesMask = findRidges(equalized, skinMask, blockSize, 0.35);
        Bitmap bRidgesMask = grayMatToBmp(ridgesMask);
        System.out.println("wyswietl bRidgesMask brejkpojnt");

        /// Blur the equalized to remove noise and preserve edges
        Mat filtered = new Mat();
        Imgproc.bilateralFilter(equalized, filtered, 5, 200, 200);
        Bitmap bFiltered = grayMatToBmp(filtered);
        System.out.println("wyswietl bFiltered brejkpojnt");

        /// Threshold the image with adaptive thresholding
        Mat threshed = new Mat();
        Imgproc.adaptiveThreshold(filtered, threshed, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 1);

        /// Invert image so ridges are black.
        Core.bitwise_not(threshed, threshed);
        Bitmap bThreshed = grayMatToBmp(threshed);
        System.out.println("wyswietl bThreshed brejkpojnt");

        /// Merge two masks to not include something we do not want to process
        Mat mask = new Mat();
        Core.bitwise_and(skinMask, ridgesMask, mask);
        Bitmap bMask = grayMatToBmp(mask);
        System.out.println("wyswietl bMask brejkpojnt");

        /// Apply mask to threshed image
        Core.bitwise_and(threshed, mask, threshed);
        bThreshed = grayMatToBmp(threshed);
        System.out.println("wyswietl bThreshed2 brejkpojnt");

        return threshed;
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
                // TODO: rozwiazac problem wyjscia poza tablice
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

    private void testowaWypiszMat(Mat varianceImg) {
        String ehh = "";
        for (int i = 200; i < varianceImg.height() - 200; ++i) {
            for (int j = 0; j < varianceImg.width(); ++j) {
                ehh += Double.toString(varianceImg.get(i, j)[0]);
            }
            ehh += '\n';
        }
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

        // wypelnienie najwiekszego konturu na bialo (na 1) TODO: zmienic na 1 z 255
        Mat skinMask = new Mat(_src.width(), _src.height(), CvType.CV_8U, Scalar.all(0));
        Imgproc.fillPoly(skinMask, cnt, Scalar.all(255));

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

    private Bitmap floatMatToBmp(Mat src) {
        Mat _src = src.clone();
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(_src.cols(), _src.rows(), Bitmap.Config.ARGB_8888);
            _src.convertTo(_src, CvType.CV_8U);
            Core.normalize(_src, _src, 0, 255, Core.NORM_MINMAX);
            Imgproc.cvtColor(_src, _src, Imgproc.COLOR_GRAY2RGB);
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


    public Bitmap getOriginal() {
        return original;
    }

    public Bitmap getEnchanced() {
        return enhanced;
    }

}
