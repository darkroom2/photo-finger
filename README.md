# PhotoFinger

Oprogramowanie realizujące rozpoznawanie odcisku palca na podstawie fotografii palca

Wymagane:
-----

* [Android Studio](https://developer.android.com/studio)
* [OpenCV](https://github.com/opencv/opencv)
* [SourceAFIS](https://github.com/robertvazan/sourceafis-java)

Działanie:
---

Koncepcja rozwiązania polega na wykonaniu zdjęcia za pomocą aparatu w smartfonie. Zdjęcie to poddawane jest działaniu szeregu algorytmów polepszających obraz oraz wyłuskujących z niego potrzebne cechy (wektor minucji). Następnie ten wektor porównywany jest z wektorami cech już istniejącymi w bazie danych. Na koniec następuje zwrócenie wyniku tego porównania – jeśli użytkownik został zidentyfikowany, to podawany jest również jego identyfikator w systemie.


| | | |
|:-------------------------:|:-------------------------:|:-------------------------:|
|<img src="images/1.jpg" width="200">|<img src="images/2.jpg" width="200">|<img src="images/3.jpg" width="200">|
|<img src="images/4.jpg" width="200">|<img src="images/5.jpg" width="200">|<img src="images/6.jpg" width="200">|
|<img src="images/7.jpg" width="200">|<img src="images/8.jpg" width="200">|<img src="images/9.jpg" width="200">|
