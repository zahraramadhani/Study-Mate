# StudyMate Quest ⚔️🏆

[![Download APK](https://img.shields.io/badge/Download-APK%20Rilis-blue?style=for-the-badge&logo=android)](https://github.com/zahraramadhani/Study-Mate/releases/download/v1.0.0/studymate-quest-v1.0.0.apk)
[![GitHub Release](https://img.shields.io/badge/GitHub-Release%20Page-darkgreen?style=for-the-badge&logo=github)](https://github.com/zahraramadhani/Study-Mate/releases/latest)

**StudyMate Quest** adalah aplikasi manajemen tugas (Quest Board) pintar untuk Android yang digabungkan dengan gamifikasi RPG (Role-Playing Game). Setiap tugas kuliah atau catatan harian Anda dikemas sebagai "Quest" yang menantang. Selesaikan tugas Anda, dapatkan XP, naikkan level, kumpulkan koin, dan kembangkan karakter virtual pendamping Anda!

Aplikasi ini didesain unik dengan antarmuka dinamis yang digambar secara penuh secara programmatik (*custom canvas layout*) untuk memastikan performa yang cepat dan bebas lag tanpa XML layout yang berat.

---

## 🎮 Fitur Utama RPG & Produktivitas

### 🛡️ 3 Kelas Karakter Unik (Hero Classes)
Pilih gaya petualang Anda saat pertama kali mendaftar atau ubah kapan saja melalui menu profil:
*   **Ksatria (Knight)**: Karakter tangguh dengan visor baja, perisai salib emas, jubah merah ksatria, pedang perak, dan sayap ksatria perak yang megah.
*   **Penyihir (Mage)**: Ahli sihir dengan topi kerucut bintang emas, jubah sihir ungu gelap, tongkat sihir kayu berhias kristal cyan berpendar, dan aura magis ungu.
*   **Pemanah (Ranger)**: Petualang lincah berbalut tudung kepala (hoodie) hijau hutan, rompi kulit cokelat, busur panah kayu panjang, dan sayap angin hijau lembut.

*Setiap aksesoris visual di atas akan terbuka dan berkembang secara visual seiring level karakter Anda meningkat!*

### 🎈 Karakter Pendamping Interaktif & Animasi Hidup
Karakter virtual Anda tidak sekadar diam! Mereka memiliki serangkaian animasi yang membuatnya terasa hidup:
*   **Hovering Idle**: Karakter melayang naik-turun secara halus layaknya bernapas.
*   **Dynamic Shadow**: Bayangan di bawah karakter membesar dan mengecil secara otomatis mengikuti tinggi melayang karakter.
*   **Wing Flapping**: Sayap karakter mengepak lembut mengikuti ritme melayangnya (Level 7+).
*   **Eye Blinking**: Mata karakter berkedip secara spontan setiap beberapa detik sekali.
*   **Reaksi Sentuh**: Ketuk dua kali pada karakter pendamping untuk memicu gerakan animasi reaksi kustom (melambai, melompat ceria, berputar kecil, pura-pura sakit, atau kaget bahagia).

### ⌛ Mode Fokus & Hadiah Koin
*   Gunakan pengatur waktu (Timer Preset: 15, 25, atau 50 menit) untuk membantu Anda fokus belajar.
*   Menyelesaikan sesi fokus dengan sukses akan memberi Anda **+25 XP** dan **+10 koin** secara otomatis untuk ditukarkan dengan tema baru di Toko Hadiah!

### 🛒 Reward Shop (Toko Hadiah)
Tukarkan koin yang Anda kumpulkan untuk membeli tema visual premium:
*   **Classic Theme**: Tema biru dongker minimalis bawaan yang elegan.
*   **Ocean Theme**: Tema biru muda cyan yang menyegarkan.
*   **Sunset Theme**: Tema oranye kemerahan sunset yang hangat dan dramatis.
*   *Kartu pemilihan tema dilengkapi dengan linear gradien HSL yang interaktif.*

### 🔔 Pengingat Deadline Jam 8 Pagi Otomatis
*   Jangan pernah melewatkan tugas kuliah lagi! Sistem akan otomatis memindai quest Anda setiap hari.
*   Jika ada quest yang memiliki deadline kurang dari 3 hari, alarm sistem akan memicu notifikasi pengingat tepat pada pukul **08.00 pagi**.
*   Pengingat harian ini berjalan andal secara mandiri di latar belakang dan otomatis dipasang kembali bahkan setelah handphone Anda di-reboot (*boot completed recovery*).

---

## 🛠️ Cara Build & Menjalankan Aplikasi

Aplikasi ini menggunakan sistem Gradle standard. Pastikan Anda telah mengonfigurasi JDK dan Android SDK pada environment Anda.

1.  **Clone repositori ini**:
    ```bash
    git clone https://github.com/zahraramadhani/Study-Mate.git
    ```
2.  **Masuk ke direktori**:
    ```bash
    cd Study-Mate
    ```
3.  **Build APK Debug**:
    ```bash
    ./gradlew assembleDebug
    ```
    *File APK yang dihasilkan akan berada di direktori `app/build/outputs/apk/debug/app-debug.apk`.*

4.  **Build APK Release (Siap pakai)**:
    ```bash
    ./gradlew assembleRelease
    ```

---

## 📦 File Inti Repositori
Repositori ini hanya berisi file-file esensial untuk pengembangan StudyMate:
*   `app/src/main/` - Berisi kode sumber Java utama, ikon notifikasi, dan manifest aplikasi.
*   `build.gradle.kts` & `settings.gradle.kts` - Konfigurasi build Gradle proyek.
*   `gradle/` & `gradlew` - Wrapper Gradle untuk memudahkan build tanpa instalasi lokal.
