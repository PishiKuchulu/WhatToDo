<div align="center">

# ✅ What ToDo

### مدیریت کارها، به زبانِ تو — Task management that speaks your language

[![Build APK](https://github.com/PishiKuchulu/WhatToDo/actions/workflows/build-apk.yml/badge.svg)](https://github.com/PishiKuchulu/WhatToDo/actions/workflows/build-apk.yml)
[![Release](https://img.shields.io/github/v/release/PishiKuchulu/WhatToDo?label=release&color=7F52FF)](https://github.com/PishiKuchulu/WhatToDo/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![API](https://img.shields.io/badge/API-24%2B-blue)](https://android-arsenal.com/api?level=24)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**What ToDo** یک اپلیکیشن مدیریت کار (To-Do) **آفلاین-اول** و **فارسی‌محور** برای اندروید است —
ساخته‌شده با Jetpack Compose و Material 3، برای کسانی که سادگی، سرعت و کنترل کامل روی داده‌هایشان را دوست دارند.

</div>

---

<div dir="rtl">

## ✨ چرا What ToDo؟

| | |
|---|---|
| 🌍 | **دوزبانهٔ واقعی** — فارسی و انگلیسی، با چیدمان کامل راست‌به‌چپ و ارقام فارسی |
| 📴 | **آفلاین-اول** — بدون حساب کاربری، بدون سرور؛ داده‌هایت فقط روی گوشی توست |
| ⏰ | **یادآور دقیق** — آلارم دقیق اندروید + باززمان‌بندی خودکار بعد از ریبوت |
| 🔁 | **تکرار هوشمند** — روزانه، هفتگی، ماهانه، سالانه؛ بدون خطای آخر ماه و سال کبیسه |
| 🎙️ | **ورودی صوتی و افزودن سریع** — با تشخیص هوشمند تاریخ و اولویت از متن فارسی |

## 🧩 امکانات

- **مدیریت کار:** زیروظیفه با پیشرفت سلسله‌مراتبی، دسته‌بندی رنگی، سه سطح اولویت، تاریخ سررسید
- **ماتریس آیزنهاور:** کارهایت را بر اساس اهمیت/فوریت بچین
- **تقویم:** نمای ماهانه با پشتیبانی از تقویم شمسی و میلادی
- **تولدها:** یادآور تولد با اعلان روز قبل
- **آمار:** نرخ واقعی انجام، روند ۷ روزه، توزیع دسته‌بندی‌ها — محاسبه‌شده از داده‌های خودت
- **جست‌وجوی فارسی:** نرمال‌سازی ی/ک عربی، فیلتر وضعیت و تاریخ، مرتب‌سازی دستی با درگ‌اند‌دراپ
- **بکاپ:** خروجی/ورودی JSON نسخه‌دار با بازیابی تراکنشی (بدون بازنویسی داده‌های موجود)
- **ویجت و دیپ‌لینک:** `wtodo://task/...` برای اتصال از بیرون اپ

## 🛠️ تکنولوژی‌ها

`Kotlin` · `Jetpack Compose (Material 3)` · `Room` · `DataStore` · `WorkManager` · `Navigation Compose` · `Coil` · `Vico`

معماری تمیز (Clean Architecture): لایه‌های `domain` / `data` / `ui` با UseCaseهای مستقل و تست‌پذیر.

## 🚀 ساخت و نصب

```bash
git clone https://github.com/PishiKuchulu/WhatToDo.git
cd WhatToDo
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

> 💡 فایل APK آماده را می‌توانی از بخش [Releases](../../releases) دانلود کنی —
> با هر تگ نسخه (مثل `v1.1.1`) به‌صورت خودکار ساخته و ضمیمه می‌شود.

## 🤝 مشارکت

Pull Requestها خوش‌آمدند! لطفاً قبلش نگاهی به [CONTRIBUTING.md](CONTRIBUTING.md) بینداز.

## 📄 لایسنس

[MIT](LICENSE) — آزاد برای استفاده، تغییر و انتشار.

</div>

---

## ✨ Why What ToDo?

An **offline-first**, **Persian-friendly** task manager for Android — built with Jetpack Compose and Material 3, for people who value simplicity, speed, and full control over their data.

| | |
|---|---|
| 🌍 | **Truly bilingual** — Persian & English, with full RTL layout and Persian digits |
| 📴 | **Offline-first** — no accounts, no servers; your data stays on your phone |
| ⏰ | **Exact reminders** — precise Android alarms, auto-rescheduled after reboot |
| 🔁 | **Smart recurrence** — daily/weekly/monthly/yearly, drift-free month-end & leap-year handling |
| 🎙️ | **Voice input & quick-add** — smart date/priority detection from Persian text |

## 🧩 Features

- **Tasks:** subtasks with hierarchical progress, colored categories, 3 priority levels, due dates
- **Eisenhower Matrix:** organize by importance/urgency
- **Calendar:** monthly view with Jalali & Gregorian support
- **Birthdays:** reminders with day-before notification
- **Statistics:** real completion rate, 7-day trend, category distribution — computed from your data
- **Persian search:** Arabic ye/kaf normalization, status/date filters, manual drag-&-drop ordering
- **Backup:** versioned JSON export/import with transactional restore (never overwrites existing data)
- **Widget & deep links:** `wtodo://task/...` for external integrations

## 🛠️ Tech stack

`Kotlin` · `Jetpack Compose (Material 3)` · `Room` · `DataStore` · `WorkManager` · `Navigation Compose` · `Coil` · `Vico`

Clean Architecture: `domain` / `data` / `ui` layers with independent, testable use cases.

## 🚀 Build & install

```bash
git clone https://github.com/PishiKuchulu/WhatToDo.git
cd WhatToDo
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

> 💡 Grab a ready-made APK from [Releases](../../releases) — built and attached automatically on every version tag (e.g. `v1.1.1`).

## 🤝 Contributing

PRs are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## 📄 License

[MIT](LICENSE) — free to use, modify and share.
