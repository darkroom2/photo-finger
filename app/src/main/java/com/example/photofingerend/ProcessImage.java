package com.example.photofingerend;

import android.graphics.Bitmap;
import android.util.Pair;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.ximgproc.Ximgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


class MinutiaeTemplate {
    String version;
    int width;
    int height;
    int[] positionsX;
    int[] positionsY;
    double[] directions;
    String types;

    public MinutiaeTemplate(int width, int height, int[] positionsX, int[] positionsY, double[] directions, String types) {
        this.version = "3.11.0";
        this.width = width;
        this.height = height;
        this.positionsX = positionsX;
        this.positionsY = positionsY;
        this.directions = directions;
        this.types = types;
    }
}

class MinutiaeDetails {
    public int row;
    public int col;
    public double dir;
    public char type;

    public MinutiaeDetails(int row, int col, double dir, char type) {
        this.row = row;
        this.col = col;
        this.dir = dir;
        this.type = type;
    }
}

public class ProcessImage {

    private final Bitmap result;
    private byte[] minutiaeCBOR = null;

    public ProcessImage(String photoPath, String outDirPath) {
        this.result = processImage(photoPath, outDirPath);
    }

    public ProcessImage(String photoPath) {
        this.result = processImage(photoPath, null);
    }

    public Bitmap getResult() {
        return result;
    }

    private Bitmap processImage(String inputPath, String outDirPath) {
        /// Open file from supplied path
        Mat src = Imgcodecs.imread(inputPath);

        /// Wstepne przycinanie zdjecia do srodka do rozmiaru 1000x1000
        Rect roi = new Rect(src.width() / 2 - 480, src.height() / 2 - 480, 960, 960);
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
        CLAHE clahe = Imgproc.createCLAHE(32.0, new Size(90, 90));
        Mat equalized = new Mat();
        clahe.apply(grayMasked, equalized);

        /// Block size for processing
        int blockSize = 16;

        /// Get ridges mask
        Mat ridgesMask = getRidgesMask(equalized, skinMask, blockSize, 0.35);

        /// Blur the equalized to remove noise and preserve edges
        Mat blurred = new Mat();
        Imgproc.bilateralFilter(equalized, blurred, 5, 200, 200);

        /// Threshold the image with adaptive thresholding
        Mat threshed = new Mat();
        Imgproc.adaptiveThreshold(blurred, threshed, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, -1);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(threshed, threshed, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(threshed, threshed, Imgproc.MORPH_OPEN, kernel);

        /// Merge two masks to not include something we do not want to process
        Mat mask = new Mat();
        Core.bitwise_and(skinMask, ridgesMask, mask);

        // zmiana na biale linie
        Core.bitwise_not(threshed, threshed);

        /// Apply mask to threshed image
        Mat threshMasked = new Mat();
        Core.bitwise_and(threshed, threshed, threshMasked, mask);

        // zmiana na czarne linie
        Core.bitwise_not(threshMasked, threshMasked);

        /// Get orientation map
        Mat orientationMap = getOrientationMap(threshMasked, blockSize);
        Mat visualisation = visualizeOrientationMap(resized, orientationMap, blockSize);

        /// Calculate frequency
        double frequency = getRidgeFrequency(threshMasked, orientationMap, blockSize, 7, 5, 15);

        /// Gabor filter to remove noise
        Mat gaborFiltered = getGaborFiltered(threshMasked, orientationMap, frequency);

        /// Threshold gabor filtered image
        Mat gaborThreshed = new Mat();
        Imgproc.threshold(gaborFiltered, gaborThreshed, 128, 255, Imgproc.THRESH_BINARY);


        /// Skeletonize gabor filtered thresholded image
        gaborThreshed.convertTo(gaborThreshed, CvType.CV_8U);

        Imgproc.morphologyEx(gaborThreshed, gaborThreshed, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(gaborThreshed, gaborThreshed, Imgproc.MORPH_OPEN, kernel);

        Mat gaborThreshedInv = new Mat();
        Core.bitwise_not(gaborThreshed, gaborThreshedInv);

        Mat skeletonized = new Mat();
        Ximgproc.thinning(gaborThreshed, skeletonized);

        /// Find minutiaes
        List<MinutiaeDetails> minutiaesDets = new ArrayList<>();
        Mat minutiaes = findMinutiaes(skeletonized, orientationMap, minutiaesDets);

        int width = skeletonized.width();
        int height = skeletonized.height();
        List<Integer> positionsX = new ArrayList<>();
        List<Integer> positionsY = new ArrayList<>();
        List<Double> directions = new ArrayList<>();
        StringBuilder types = new StringBuilder();

        for (MinutiaeDetails detail : minutiaesDets) {
            positionsX.add(detail.col);
            positionsY.add(detail.row);
            directions.add(detail.dir);
            types.append(detail.type);
        }

        ObjectMapper mapper = new ObjectMapper(new CBORFactory()).setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        try {
            minutiaeCBOR = mapper.writeValueAsBytes(new MinutiaeTemplate(width, height, positionsX.stream()
                    .mapToInt(Integer::intValue)
                    .toArray(), positionsY.stream()
                    .mapToInt(Integer::intValue)
                    .toArray(), directions.stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray(), types.toString()));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        Core.bitwise_not(skeletonized, skeletonized);

        // zapisz wyniki
        if (outDirPath != null) {
            Imgcodecs.imwrite(outDirPath + File.separator + "00src.png", src);
            Imgcodecs.imwrite(outDirPath + File.separator + "01resized.png", resized);
            Imgcodecs.imwrite(outDirPath + File.separator + "02skinMask.png", skinMask);
            Imgcodecs.imwrite(outDirPath + File.separator + "03grayMasked.png", grayMasked);
            Imgcodecs.imwrite(outDirPath + File.separator + "04equalized.png", equalized);
            Imgcodecs.imwrite(outDirPath + File.separator + "05ridgesMask.png", ridgesMask);
            Imgcodecs.imwrite(outDirPath + File.separator + "06blurred.png", blurred);
            Imgcodecs.imwrite(outDirPath + File.separator + "07result.png", threshMasked);
            Imgcodecs.imwrite(outDirPath + File.separator + "08visualisation.png", visualisation);
            Imgcodecs.imwrite(outDirPath + File.separator + "09gaborThreshed.png", gaborThreshedInv);
            Imgcodecs.imwrite(outDirPath + File.separator + "10skeletonized.png", skeletonized);
            Imgcodecs.imwrite(outDirPath + File.separator + "11minutiaes.png", minutiaes);
        }

        return grayMatToBmp(threshMasked);
    }

    private Mat findMinutiaes(Mat skeletonized, Mat orientationMap, List<MinutiaeDetails> coords) {
        int kernelSize = 3;

        Mat skeletonized_inv = new Mat();
        Core.bitwise_not(skeletonized, skeletonized_inv);

        Mat minutiaeImg = new Mat();
        Imgproc.cvtColor(skeletonized_inv, minutiaeImg, Imgproc.COLOR_GRAY2RGB);

        Mat skel = skeletonized.clone();
        Mat mask = new Mat();
        Core.compare(skel, Scalar.all(0), mask, Core.CMP_GT);

        skel.setTo(Scalar.all(1), mask);

        int rows = skel.rows();
        int cols = skel.cols();

        for (int i = kernelSize / 2 + 100; i < cols - kernelSize / 2 - 100; ++i) {
            for (int j = kernelSize / 2 + 100; j < rows - kernelSize / 2 - 100; ++j) {
                int cn = 0;
                if (skel.get(j, i)[0] == 1) {
                    cn += Math.abs(skel.get(j - 1, i - 1)[0] - skel.get(j - 1, i)[0]);
                    cn += Math.abs(skel.get(j - 1, i)[0] - skel.get(j - 1, i + 1)[0]);
                    cn += Math.abs(skel.get(j - 1, i + 1)[0] - skel.get(j, i + 1)[0]);
                    cn += Math.abs(skel.get(j, i + 1)[0] - skel.get(j + 1, i + 1)[0]);
                    cn += Math.abs(skel.get(j + 1, i + 1)[0] - skel.get(j + 1, i)[0]);
                    cn += Math.abs(skel.get(j + 1, i)[0] - skel.get(j + 1, i - 1)[0]);
                    cn += Math.abs(skel.get(j + 1, i - 1)[0] - skel.get(j, i - 1)[0]);
                    cn += Math.abs(skel.get(j, i - 1)[0] - skel.get(j - 1, i - 1)[0]);
                    cn = cn / 2;
                    if (cn == 1) {
                        Imgproc.circle(minutiaeImg, new Point(i, j), 3, new Scalar(0, 0, 150), 1);
                        coords.add(new MinutiaeDetails(j, i, orientationMap.get(j / 16, i / 16)[0], 'E'));
                    } else if (cn == 3) {
                        Imgproc.circle(minutiaeImg, new Point(i, j), 3, new Scalar(0, 150, 0), 1);
                        coords.add(new MinutiaeDetails(j, i, orientationMap.get(j / 16, i / 16)[0], 'B'));
                    }
                }
            }
        }
        return minutiaeImg;
    }

    private Mat getGaborFiltered(Mat threshMasked, Mat orientationMap, double frequency) {
        int rows = threshMasked.rows();
        int cols = threshMasked.cols();

        double kx = 0.65;
        double ky = 0.65;

        double sigmaX = kx / frequency;
        double sigmaY = ky / frequency;

        int rangeSize = (int) (Math.round(3 * Math.max(sigmaX, sigmaY)));
        int size = (2 * rangeSize + 1);
        Size filterSize = new Size(size, size);

        int angleAcc = 3;

        Mat[] filterBank = new Mat[180 / angleAcc];

        for (int theta = 0; theta < filterBank.length; ++theta) {
            filterBank[theta] = Imgproc.getGaborKernel(filterSize, sigmaX, Math.toRadians(theta * 3 + 90), 1 / frequency, 0.5, 0);
        }

        // finally, find where there is valid frequency data then do the filtering
        Mat ridgeFilterImg = new Mat(threshMasked.size(), CvType.CV_32FC1);

        // convert orientation matrix values from radians to an index value
        // that corresponds to round(degrees/angleInc)
        Mat orientIndexes = new Mat(orientationMap.rows(), orientationMap.cols(), CvType.CV_8UC1);
        Core.multiply(orientationMap, Scalar.all((double) filterBank.length / Math.PI), orientIndexes, 1.0, CvType.CV_8UC1);

        Mat orientMask;
        Mat orientThreshold;

        orientMask = new Mat(orientationMap.rows(), orientationMap.cols(), CvType.CV_8UC1, Scalar.all(0));
        orientThreshold = new Mat(orientationMap.rows(), orientationMap.cols(), CvType.CV_8UC1, Scalar.all(0.0));
        Core.compare(orientIndexes, orientThreshold, orientMask, Core.CMP_LT);
        Core.add(orientIndexes, Scalar.all(filterBank.length), orientIndexes, orientMask);

        orientMask = new Mat(orientationMap.rows(), orientationMap.cols(), CvType.CV_8UC1, Scalar.all(0));
        orientThreshold = new Mat(orientationMap.rows(), orientationMap.cols(), CvType.CV_8UC1, Scalar.all(filterBank.length));
        Core.compare(orientIndexes, orientThreshold, orientMask, Core.CMP_GE);
        Core.subtract(orientIndexes, Scalar.all(filterBank.length), orientIndexes, orientMask);

        // finally, find where there is valid frequency data then do the filtering
        Mat value = new Mat(size, size, CvType.CV_32FC1);
        Mat subSegment;
        int orientIndex;
        double sum;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r > (rangeSize + 1)
                        && r < (rows - rangeSize - 1)
                        && c > (rangeSize + 1)
                        && c < (cols - rangeSize - 1)) {
                    orientIndex = (int) orientIndexes.get(r / 16, c / 16)[0];
                    subSegment = threshMasked.submat(r - rangeSize - 1, r + rangeSize, c - rangeSize - 1, c + rangeSize);
                    Core.multiply(subSegment, filterBank[orientIndex], value, 1, CvType.CV_32F);
                    sum = Core.sumElems(value).val[0];
                    ridgeFilterImg.put(r, c, sum);
                }
            }
        }

        return ridgeFilterImg;
    }

    private double getRidgeFrequency(Mat ridgesImg, Mat orientationMap, int blockSize, int kernelSize, int minWaveLength, int maxWaveLength) {

        int rows = ridgesImg.rows();
        int cols = ridgesImg.cols();

        Mat freqencies = new Mat(ridgesImg.size(), CvType.CV_32F);

        // TODO: avoid processing masked data ?

        for (int row = 0; row < rows - blockSize; row += blockSize) {
            for (int col = 0; col < cols - blockSize; col += blockSize) {
                Mat window = ridgesImg.submat(row, row + blockSize, col, col + blockSize);
                double angle = orientationMap.get(row / blockSize, col / blockSize)[0];

                double freq = getBlockFrequency(window, angle, kernelSize, minWaveLength, maxWaveLength);

                freqencies.submat(row, row + blockSize, col, col + blockSize).setTo(Scalar.all(freq));
            }
        }

        return myMedianFrequency(freqencies);
    }

    private double getBlockFrequency(Mat window, double angle, int kernelSize, int minWaveLength, int maxWaveLength) {
        int rows = window.rows();
        int cols = window.cols();

        double angleDeg = Math.toDegrees(angle) + 90;

        Point center = new Point(rows >> 1, cols >> 1);

        Mat rotMat = Imgproc.getRotationMatrix2D(center, angleDeg, 1.0);

        Mat rotated = new Mat();
        Imgproc.warpAffine(window, rotated, rotMat, new Size(rows, cols));

        int offset = (int) Math.ceil(rows * (0.5 - Math.sqrt(2) / 4));

        Mat cropped = rotated.submat(offset, rows - offset, offset, cols - offset);

        Mat ridgeSum = new Mat();
        Core.reduce(cropped, ridgeSum, 0, Core.REDUCE_SUM, CvType.CV_32F);

        Mat dilated = new Mat();
        Imgproc.dilate(ridgeSum, dilated, Mat.ones(new Size(kernelSize, kernelSize), CvType.CV_8U), new Point(-1, -1), 1);

        Mat ridgeNoise = new Mat();
        Core.subtract(dilated, ridgeSum, ridgeNoise);

        Mat peaks = new Mat();
        Core.compare(ridgeNoise, Scalar.all(10), peaks, Core.CMP_LT);

        Scalar ridgeSumMean = Core.mean(ridgeSum);

        Mat aboveAvg = new Mat();
        Core.compare(ridgeSum, ridgeSumMean, aboveAvg, Core.CMP_GT);

        Mat ridges = new Mat();
        Core.bitwise_and(peaks, aboveAvg, ridges);

        int bufferSize = ridges.channels() * ridges.cols() * ridges.rows();
        byte[] bRidges = new byte[bufferSize];
        ridges.get(0, 0, bRidges); // get all the pixels

        ArrayList<Integer> ridgeIndices = where(bRidges);

        if (ridgeIndices.size() != 0) {
            int peakCount = ridgeIndices.size();
            if (peakCount >= 2) {
                double waveLength = (double) (ridgeIndices.get(peakCount - 1) - ridgeIndices.get(0)) / (peakCount - 1);
                if (waveLength >= minWaveLength && waveLength <= maxWaveLength) {
                    return 1.0 / waveLength;
                }
            }
        }
        return 0.f;
    }

    private ArrayList<Integer> where(byte[] bRidges) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < bRidges.length; ++i) {
            if (bRidges[i] < 0)
                result.add(i);
        }
        return result;
    }

    private double myMedianFrequency(Mat image) {

        ArrayList<Double> values = new ArrayList<>();
        double value;

        for (int r = 0; r < image.rows(); r++) {
            for (int c = 0; c < image.cols(); c++) {
                value = image.get(r, c)[0];
                if (value > 0) {
                    values.add(value);
                }
            }
        }

        Collections.sort(values);
        int size = values.size();
        double median = 0;

        if (size > 0) {
            int halfSize = size / 2;
            if ((size % 2) == 0) {
                median = (values.get(halfSize - 1) + values.get(halfSize)) / 2.0;
            } else {
                median = values.get(halfSize);
            }
        }
        return median;
    }

    private Mat visualizeOrientationMap(Mat resized, Mat orientation, int blockSize) {
        Mat result = resized.clone();
        for (int i = 0; i < result.rows() - blockSize; i += blockSize) {
            for (int j = 0; j < result.cols() - blockSize; j += blockSize) {
                double tang = Math.tan(orientation.get(j / blockSize, i / blockSize)[0]);
                Pair<Point, Point> line_ends = getLineEnds(i, j, blockSize, tang);
                Imgproc.line(result, line_ends.first, line_ends.second, new Scalar(0, 0, 255), 1);
            }
        }
        return result;
    }

    private Pair<Point, Point> getLineEnds(int i, int j, int blockSize, double tang) {
        Point begin;
        Point end;
        if (tang >= -1 && tang <= 1) {
            begin = new Point(i, (int) ((-blockSize / 2) * tang + j + blockSize / 2));
            end = new Point(i + blockSize, (int) ((blockSize / 2) * tang + j + blockSize / 2));
        } else {
            begin = new Point((int) (i + blockSize / 2 + blockSize / (2 * tang)), j + (blockSize >> 1));
            end = new Point((int) (i + blockSize / 2 - blockSize / (2 * tang)), j - (blockSize >> 1));
        }
        return new Pair<>(begin, end);
    }

    private Mat getOrientationMap(Mat threshMasked, int blockSize) {
        int rows = threshMasked.rows();
        int cols = threshMasked.cols();

        // Create container element
        Mat result = new Mat(rows / blockSize, cols / blockSize, CvType.CV_32F, Scalar.all(0));

        // Get gradients
        Mat gradX = new Mat();
        Mat gradY = new Mat();
        Imgproc.Sobel(threshMasked, gradX, CvType.CV_32F, 1, 0);
        Imgproc.Sobel(threshMasked, gradY, CvType.CV_32F, 0, 1);

        // Calculate orientations of gradients --> in degrees
        for (int i = 0; i < rows - blockSize; i += blockSize) {
            for (int j = 0; j < cols - blockSize; j += blockSize) {
                Mat _gx = gradX.submat(i, i + blockSize, j, j + blockSize);
                Mat _gy = gradY.submat(i, i + blockSize, j, j + blockSize);

                Mat gxtgy = _gx.mul(_gy);

                Mat gxtgyt2 = new Mat();
                Core.multiply(gxtgy, new Scalar(2), gxtgyt2);

                Mat gxsq = _gx.mul(_gx);
                Mat gysq = _gy.mul(_gy);

                Mat gxsqmgysq = new Mat();
                Core.subtract(gxsq, gysq, gxsqmgysq);

                double nom = Core.sumElems(gxtgyt2).val[0];
                double denom = Core.sumElems(gxsqmgysq).val[0];

                // TODO: dodac sprawdzanie czy zero zeby nei dzielic przez zero xd
                // shift range to be 0 - 180 (line can rotate up to 180 deg)
                double res = (Math.PI + Math.atan2(nom, denom)) / 2;
                result.put(i / blockSize, j / blockSize, res);
            }
        }
        return result;
    }

    private Mat getRidgesMask(Mat src, Mat mask, int blockSize, double threshold) {
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
        Scalar lowerBounds = new Scalar(0, 30, 120);
        Scalar upperBounds = new Scalar(35, 180, 255);

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

    private Bitmap floatMatToBmp(Mat src) {
        Mat _src = src.clone();
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(_src.cols(), _src.rows(), Bitmap.Config.ARGB_8888);
            Core.normalize(_src, _src, 0, 255, Core.NORM_MINMAX);
            _src.convertTo(_src, CvType.CV_8U);
            Imgproc.cvtColor(_src, _src, Imgproc.COLOR_GRAY2RGB);
            Utils.matToBitmap(_src, bmp);
            return bmp;
        } catch (CvException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getBytes() {
        return minutiaeCBOR;
    }
}