import glob
import math

import cv2 as cv
import numpy as np


def findOrientationMap(im, w):
    (y, x) = im.shape[:2]

    result = np.zeros((len(range(0, y, w)), len(range(0, x, w))), np.float32)

    gx = cv.Sobel(im, cv.CV_32FC1, 1, 0)
    gy = cv.Sobel(im, cv.CV_32FC1, 0, 1)

    for j in range(0, y, w):
        for i in range(0, x, w):
            gx_ = gx[j:min(j + w, y), i:min(i + w, x)]
            gy_ = gy[j:min(j + w, y), i:min(i + w, x)]
            nom = np.sum(2 * gx_ * gy_)
            denom = np.sum(gx_ ** 2 - gy_ ** 2)
            if nom or denom:
                result[j // w, i // w] = (np.pi + np.arctan2(nom, denom)) / 2
    return result


def get_line_ends(i, j, W, tang):
    if -1 <= tang <= 1:
        begin = (i, int((-W / 2) * tang + j + W / 2))
        end = (i + W, int((W / 2) * tang + j + W / 2))
    else:
        begin = (int(i + W / 2 + W / (2 * tang)), j + W // 2)
        end = (int(i + W / 2 - W / (2 * tang)), j - W // 2)
    return begin, end


def visualizeOrientationMap(im, angles, W):
    (y, x) = im.shape[:2]
    if len(im.shape) < 3:
        result = cv.cvtColor(im, cv.COLOR_GRAY2RGB)
    else:
        result = im.copy()
    for i in range(0, x, W):
        for j in range(0, y, W):
            tang = math.tan(angles[j // W, i // W])
            (begin, end) = get_line_ends(i, j, W, tang)
            cv.line(result, begin, end, (0, 0, 255), 1)

    return result


def findRidges(src, block_size, threshold):
    r, c = src.shape[:2]

    variance_img = np.zeros((r, c), np.float32)
    mask = np.zeros((r, c), np.uint8)

    threshold = np.std(src) * threshold

    for x in range(0, c, block_size):
        for y in range(0, r, block_size):
            variance = np.std(src[y:y + block_size, x:x + block_size])
            variance_img[y:y + block_size, x:x + block_size] = variance

    mask[variance_img > threshold] = 1

    mask = cv.erode(mask, np.ones((5, 5), np.uint8))

    # contours, _ = cv.findContours(mask, cv.RETR_TREE, cv.CHAIN_APPROX_SIMPLE)
    #
    # # domyslamy sie ze palec jest najwiekszym obiektem na zdjeciu, dlatego szukamy najwiekszego konturu
    # biggest_contour = max(contours, key=cv.contourArea)
    #
    # # Narysuj ten kontur i wypelnij na bialo, reszta czarna
    # mask = cv.fillPoly(np.zeros(mask.shape, np.uint8), pts=[biggest_contour], color=1)

    return mask


def getBlockFrequency(window, angle, kernel_size, minWaveLen, maxWaveLen):
    rows, cols = window.shape[:2]

    # obrot obrazu
    angle_deg = math.degrees(angle) + 90
    center = (rows / 2, cols / 2)
    rot_mat = cv.getRotationMatrix2D(center, angle_deg, 1.0)
    res = cv.warpAffine(window, rot_mat, (rows, cols))

    # przyciecie do najwiekszego kwadratu wpisanego w obraz po obrocie
    offset = int(np.ceil(rows * (0.5 - np.sqrt(2) / 4)))
    cropped = res[offset:rows - offset, offset:cols - offset]

    # suma slupkow (wzdluz bruzdy duza suma, wzdluz doliny - mala)
    ridge_sum = np.sum(cropped, axis=0, keepdims=True).astype(np.float32)

    # pogrub bruzde aby ...
    dilation = cv.dilate(ridge_sum, np.ones((kernel_size, kernel_size)))
    # usunac oryginal i w miejscu bruzdy wystapilo 0 lub niewielka wartosc
    ridge_noise = dilation - ridge_sum
    # lista booli gdzie jest bruzda
    peaks = ridge_noise < 10
    # lista booli gdzie wartosc jest wieksza niz srednia (peaki)
    above_avg = ridge_sum > np.mean(ridge_sum)

    # ostateczna lista bruzd
    ridges = peaks & above_avg

    freq = 0

    if np.any(ridges):
        ridge_indices = np.where(ridges)[1]
        peak_count = len(ridge_indices)

        if peak_count >= 2:
            # srednia odleglosc miedzy grzbietami
            wave_len = (ridge_indices[-1] - ridge_indices[0]) / (peak_count - 1)
            if minWaveLen <= wave_len <= maxWaveLen:
                freq = 1 / wave_len * np.ones(window.shape[:2], np.float32)

    return freq


def findRidgeFrequency(src, mask, orientation_map, block_size, kernel_size, minWaveLen, maxWaveLen):
    rows, cols = src.shape[:2]

    freqs = np.zeros((rows, cols), np.float32)

    for row in range(0, rows, block_size):
        for col in range(0, cols, block_size):
            window = src[row:row + block_size, col:col + block_size]
            angle = orientation_map[row // block_size, col // block_size]

            freq = getBlockFrequency(window, angle, kernel_size, minWaveLen, maxWaveLen)
            freqs[row:row + block_size, col:col + block_size] = freq

    freqs *= mask

    return np.median(freqs[freqs > 0])


def ridgeFilter(src, orient, freq, mask, orient_blk_sze, kx=0.65, ky=0.65):
    rows, cols = src.shape[:2]
    filtered_img = np.zeros((rows, cols))

    src = np.float32(src)

    angle_acc = 3

    sigma_x = kx / freq
    sigma_y = ky / freq
    range_size = int(np.round(3 * max(sigma_x, sigma_y)))
    filter_size = (2 * range_size + 1)

    # always generates uneven array
    arr = np.linspace(-range_size, range_size, filter_size)
    # size of meshgrid is uneven, used later as filter
    x, y = np.meshgrid(arr, arr)

    # prepare filter bank kernels
    filter_bank = []
    for theta in range(0, 180 // angle_acc):
        # # gabor filter equation
        rot_x = x * math.cos(math.radians(theta * angle_acc + 90)) + y * math.sin(math.radians(theta * angle_acc + 90))
        rot_y = -x * math.sin(math.radians(theta * angle_acc + 90)) + y * math.cos(math.radians(theta * angle_acc + 90))
        gabor = getGaborKernel(freq, rot_x, rot_y, sigma_x, sigma_y, (filter_size, filter_size))
        filter_bank.append(np.real(gabor))

    # Convert orientation matrix values from radians to an index value
    # that corresponds to round(degrees/angleInc)
    maxorientindex = 180 // angle_acc - 1
    orientindex = np.round(np.degrees(orient) / angle_acc)

    orientindex[orientindex > maxorientindex] = .0

    # Find indices of matrix points greater than maxsze from the image boundary
    valid_row, valid_col = np.where(mask > 0)

    valid_indices = np.where(
        (valid_row > range_size) &
        (valid_row < rows - range_size) &
        (valid_col > range_size) &
        (valid_col < cols - range_size)
    )[0]

    for ind in valid_indices:
        r = valid_row[ind]
        c = valid_col[ind]
        window = src[r - range_size:r + range_size + 1, c - range_size:c + range_size + 1]
        filtered_img[r, c] = np.sum(window * filter_bank[int(orientindex[r // orient_blk_sze, c // orient_blk_sze])])

    gabor_img_ridges = np.uint8((filtered_img > 0) * 255)

    return gabor_img_ridges


def getGaborKernel(freq, rotx, roty, sigma_x, sigma_y, shape):
    g = np.zeros(shape, dtype=np.complex)
    g[:] = np.exp(-0.5 * (rotx ** 2 / sigma_x ** 2 + roty ** 2 / sigma_y ** 2))
    g /= 2 * np.pi * sigma_x * sigma_y
    g *= np.exp(1j * (2 * np.pi * freq * rotx))
    return g


# TODO: WIP - narazie dziala slabo.
def detectFeaturesSIFTwip(src, mask):
    mask_fat = cv.dilate(mask, np.ones((5, 5)))
    feature_detector = cv.SIFT_create()
    return feature_detector.detectAndCompute(src, mask_fat)[1]


def getMinutiaeType(src, j, i, kernel_size):
    # CN(P) = 1/2 * sum_(i=1)^8{ abs[ Pi - P_(i-1) ]}

    pass


def findMinutiaes(src, kernel_size=3):
    minutiae_img = cv.cvtColor(src, cv.COLOR_GRAY2RGB)

    # for crossing number method
    my_src = np.int16(src.copy())
    my_src[my_src > 0] = 1

    rows, cols = my_src.shape[:2]

    for i in range(kernel_size // 2, cols - kernel_size // 2):
        for j in range(kernel_size // 2, rows - kernel_size // 2):
            cn = 0
            if my_src[j, i] == 1:  # If ridge..
                cn += abs(my_src[j - 1, i - 1] - my_src[j - 1, i])
                cn += abs(my_src[j - 1, i] - my_src[j - 1, i + 1])
                cn += abs(my_src[j - 1, i + 1] - my_src[j, i + 1])
                cn += abs(my_src[j, i + 1] - my_src[j + 1, i + 1])
                cn += abs(my_src[j + 1, i + 1] - my_src[j + 1, i])
                cn += abs(my_src[j + 1, i] - my_src[j + 1, i - 1])
                cn += abs(my_src[j + 1, i - 1] - my_src[j, i - 1])
                cn += abs(my_src[j, i - 1] - my_src[j - 1, i - 1])
                cn = cn // 2
                if cn == 1:
                    cv.circle(minutiae_img, (i, j), radius=3, color=(0, 0, 150), thickness=1)
                elif cn == 3:
                    cv.circle(minutiae_img, (i, j), radius=3, color=(0, 150, 0), thickness=1)
                else:
                    continue

    return minutiae_img


def findCorePoints(thinned, orientation_map, tolerance, block_size, mask):
    pass


def process_image(path):
    """Original input"""
    src = cv.imread(path)
    # cv.imshow(path[-25:], src)
    # cv.waitKey()

    """Crop to center 1000x1000 px"""
    width, height = src.shape[:2]
    x = int(width / 2) - 480
    y = int(height / 2) - 480
    roi_height = 960
    cropped = src[x:x + roi_height, y:y + roi_height]
    # cv.imshow('cropped', cropped)
    # cv.waitKey()

    """Resize image"""
    resized = cv.resize(cropped, (600, 600), interpolation=cv.INTER_CUBIC)
    # cv.imshow(path, resized)
    # cv.waitKey()

    """Get skin region"""
    mask = getSkin(resized)
    # cv.imshow('skin_mask', mask * 255)
    # cv.waitKey()

    # """Convert to grayscale"""
    # gray = cv.cvtColor(resized, cv.COLOR_BGR2GRAY)
    # cv.imshow('gray', gray)
    # cv.waitKey()
    #
    # """Apply mask to src"""
    # gray = cv.bitwise_and(gray, gray, mask=mask)
    # cv.imshow('gray_masked', gray)
    # cv.waitKey()
    #
    # """Equalize histogram"""
    # clahe = cv.createCLAHE(clipLimit=16.0, tileGridSize=(100, 100))
    # equalized = clahe.apply(gray)
    # # equalized = cv.equalizeHist(gray)
    # cv.imshow('equalized', equalized)
    # cv.waitKey()
    #
    # block_size = 16
    #
    # """Find ridges mask"""
    # mask2 = findRidges(resized, block_size, 0.35)
    # cv.imshow('mask', mask * 255)
    # cv.imshow('mask2', mask2 * 255)
    # cv.waitKey()
    #
    # """Blur the equalized to remove noise and preserve edges"""
    # # kernel = np.ones((5, 5), np.float32) / 25
    # # blurred = cv.filter2D(equalized, -1, kernel)
    # blurred = cv.bilateralFilter(equalized, 5, 200, 200)
    # # blurred = cv.GaussianBlur(equalized, (5, 5), 2)
    # cv.imshow('blurred', blurred)
    # cv.waitKey()
    #
    # """Threshold the image with adaptive thresholding"""
    # threshed = 255 - cv.adaptiveThreshold(blurred, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 11, 0.5)
    # # threshed = cv.threshold(blurred, 0, 255, cv.THRESH_BINARY + cv.THRESH_OTSU)[1]
    #
    # # return 255 - threshed
    #
    # cv.imshow('threshed', threshed)
    # cv.waitKey()
    #
    # """Orientation map"""
    # orientation_map = findOrientationMap(threshed, block_size)
    # orientation_img = visualizeOrientationMap(resized, orientation_map, block_size)
    # cv.imshow('orientation_map', orientation_map)
    # cv.waitKey()
    # cv.imshow('orientation_img', orientation_img)
    # cv.waitKey()
    #
    # """Ridge frequency"""
    # frequency = findRidgeFrequency(threshed, mask, orientation_map, block_size, kernel_size=5, minWaveLen=3,
    #                                maxWaveLen=15)
    #
    # """Ridge filter using bank of Gabor filters"""
    # filtered = ridgeFilter(threshed, orientation_map, frequency, mask, block_size)
    # cv.imshow('filtered', filtered)
    # cv.waitKey()
    #
    # """Binary image thinning"""
    # thinned = cv.ximgproc.thinning(filtered, thinningType=cv.ximgproc.THINNING_GUOHALL)
    # cv.imshow('thinned', thinned)
    # cv.waitKey()

    """Find minutiaes"""
    # TODO: list of minutiaes <- use it to help ORB.
    # minutiaes = findMinutiaes(thinned)
    # cv.imshow('minutiaes', minutiaes)
    # cv.waitKey()

    """Find singularities"""
    # TODO: list of singularities <- use it to help ORB.
    # core_point = findCorePoints(thinned, orientation_map, tolerance, block_size, mask)

    cv.destroyAllWindows()
    # return thinned, mask
    return mask


def detectFeaturesSIFT(src1, mask1, src2, mask2, path2):
    mask = mask1 & mask2
    mask_fat = cv.dilate(mask, np.ones((5, 5)))

    feature_detector = cv.SIFT_create()
    keypoints1, descriptors1 = feature_detector.detectAndCompute(src1, mask_fat)
    keypoints2, descriptors2 = feature_detector.detectAndCompute(src2, mask_fat)

    matcher = cv.BFMatcher_create()
    matches = matcher.knnMatch(descriptors1, descriptors2, 2)

    good_matches = []

    for match in matches:
        m1 = match[0]
        m2 = match[1]

        if m1.distance < m2.distance * 0.7:
            good_matches.append(m1)

    if len(good_matches) >= 7:
        print("Znalazłem ", path2)
        img = cv.drawMatches(src1, keypoints1, src2, keypoints2, good_matches, mask_fat)
        cv.imshow('matches', img)
        cv.waitKey()
        return img

    return None


def getSkin(src):
    # zmiana przestrzeni barw RGB na HSV.
    zdj_hsv = cv.cvtColor(src, cv.COLOR_BGR2HSV)

    # gorne i dolne granice koloru skory czlowieka (w HSV)
    skora_dolne = np.array([0, 30, 120])
    skora_gorne = np.array([35, 180, 255])

    # maskowanie pikseli nie znajdujacych sie w granicach koloru skory
    maska = cv.inRange(zdj_hsv, skora_dolne, skora_gorne)

    # jadro operacji morfologicznej otwierania i zamykania
    kernel = np.ones((9, 9), np.uint8)

    # Otwarcie usuwa małe obiekty z pierwszego planu (zwykle brane jako jasne piksele) obrazu, umieszczając je w tle
    maska = cv.morphologyEx(maska, cv.MORPH_OPEN, kernel)

    # Zamknięcie usuwa małe otwory w pierwszym planie, zmieniając małe wysepki tła na pierwszy plan.
    maska = cv.morphologyEx(maska, cv.MORPH_CLOSE, kernel)

    # znalezienie konturow obiektow
    kontury = cv.findContours(maska, cv.RETR_TREE, cv.CHAIN_APPROX_SIMPLE)[0]

    knt = max(kontury, key=cv.contourArea)

    maska = cv.fillPoly(np.zeros(maska.shape, np.uint8), pts=[knt], color=1)

    # end = cv.bitwise_and(src, src, mask=maska)

    return maska


if __name__ == '__main__':
    images_path = r'C:\Users\Radek\Downloads\latest_27-12-2020 (1)\latest_27-12-2020\BAZY ZDJEC\Baza Aplikacji\Obrazy'
    for file in glob.glob(images_path + r'\*'):
        maska = process_image(file)
        cv.imwrite(file[:-4] + '_mask.jpg', maska*255)
