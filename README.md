# دفتر — تطبيق أندرويد

المصدر الكامل لتطبيق دفتر، مع إعدادات Gradle والموارد والأيقونات. الإصدار المرفق هو **1.13** (`versionCode 14`) ومخصص لمعمارية **arm64-v8a**.

## ملفات الإصدار

- [تحميل APK الإصدار 1.13](releases/daftri-release-arm64-v8a-v1.13.apk)

## البناء محلياً

يلزم تثبيت Android SDK المناسب. من مجلد المشروع شغّل:

```bash
./gradlew assembleRelease
```

يقرأ إعداد التوقيع من الملف المحلي `$HOME/.daftri-signing/daftri.properties` بالمفاتيح `storeFile` و`storePassword` و`keyAlias` و`keyPassword`. احتفظ بملف المفتاح وكلمات مروره خارج المستودع؛ قواعد `.gitignore` تستبعد ملفات التوقيع.
