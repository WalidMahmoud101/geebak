# التراخيص والمكتبات المستخدمة

نبض نفسه تحت [ترخيص MIT](LICENSE). وبيستخدم مكتبات وخط وخدمات تانية تحت تراخيصها، دي قايمتها.

## مكتبات داخل التطبيق
| المكتبة | الاستخدام | الترخيص |
|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) و[Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | اللغة والتزامن | Apache 2.0 |
| [Jetpack Compose + Material 3](https://developer.android.com/jetpack/compose) | الواجهة | Apache 2.0 |
| [AndroidX Core / Activity / Lifecycle](https://developer.android.com/jetpack/androidx) | أساسيات أندرويد | Apache 2.0 |
| [Room](https://developer.android.com/jetpack/androidx/releases/room) | قاعدة البيانات على الموبايل | Apache 2.0 |
| [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) | المراجعة الدورية في الخلفية | Apache 2.0 |
| [KSP](https://github.com/google/ksp) | توليد كود Room وقت البناء | Apache 2.0 |
| [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) 5.5.1 | قراءة الفواتير على الموبايل | Apache 2.0 |
| [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) 4.9.0 | تشغيل Tesseract على أندرويد | Apache 2.0 |
| [Leptonica](http://leptonica.org) 1.85.0 | تجهيز صورة الفاتورة (جوه Tesseract4Android) | BSD-2-Clause |
| [libjpeg](https://ijg.org) v9f | فتح صور JPEG (جوه Tesseract4Android) | IJG |
| [libpng](http://www.libpng.org/pub/png/libpng.html) 1.6.48 | فتح صور PNG (جوه Tesseract4Android) | libpng-2.0 |

## نماذج القراءة
- **tessdata_fast** (`ara` و`eng`) من [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) تحت Apache 2.0، متضمّنة في `app/src/main/assets/tessdata` وبتشتغل على الموبايل من غير نت.

## الخط
- **IBM Plex Sans Arabic** © 2019 IBM Corp. تحت [SIL Open Font License 1.1](LICENSES/OFL-IBM-Plex-Sans-Arabic.txt). الخط متضمّن في التطبيق وفي صفحة الموقع.
- **Amiri** © 2010-2022 The Amiri Project Authors تحت [SIL Open Font License 1.1](docs/fonts/OFL-Amiri.txt). نسخة مصغّرة من الوزن العريض، لعناوين صفحة الموقع بس.

## للاختبارات فقط (مش جوه التطبيق)
| المكتبة | الترخيص |
|---|---|
| [JUnit 4](https://junit.org/junit4/) | EPL 1.0 |
| [Robolectric](https://robolectric.org) | MIT |
| [AndroidX Test / Compose UI Test](https://developer.android.com/jetpack/androidx/releases/test) | Apache 2.0 |

## خدمات بيتصل بيها التطبيق
| الخدمة | بتستخدم في | ملاحظة |
|---|---|---|
| [GitHub Releases](https://docs.github.com/rest/releases) | التحديث التلقائي | بيتحمّل منها ملف `nabd.apk` |
| [Exchange Rate API](https://www.exchangerate-api.com) (`open.er-api.com`) | سعر الدولار والعملات | الأسعار من Exchange Rate API |
| [gold-api.com](https://gold-api.com) | سعر الأوقية العالمي للدهب | بيتحوّل لسعر الجرام بالجنيه جوه التطبيق |

الأسعار للمعلومة فقط وليست نصيحة استثمارية.

## نص ترخيص Apache 2.0
نص الترخيص الكامل: <https://www.apache.org/licenses/LICENSE-2.0>

---
لو لقيت مكتبة أو خط ناقص من القايمة دي، [بلّغ عنه](https://github.com/WalidMahmoud101/nabd/issues) وهنضيفه.
