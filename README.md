# TutorSync

**TutorSync** is a comprehensive, offline-first Android application designed specifically for freelance home tutors. It streamlines the management of students, subjects, weekly schedules, attendance records, lesson progress, and financial billing (fees, invoices, and payments)—all while keeping your primary data secure on your local device.

## 🚀 Features

*   **Student & Subject Management:** Maintain a directory of students, their guardian contact details, and enrolled subjects.
*   **Recurring Schedules:** Set up weekly recurring class schedules and manage makeup classes seamlessly.
*   **Attendance & Lesson Tracking:** Log daily attendance (Present, Absent, Cancelled) along with private tutor notes, topics covered, and assigned homework.
*   **Smart Billing & Invoicing:**
    *   Supports both Monthly and Per-Class fee agreements.
    *   Automatically calculates unbilled classes and outstanding balances.
    *   Generates professional, itemized PDF invoices and payment receipts natively on the device.
*   **Payment & Ledger Tracking:** Record partial or full payments (Cash, bKash, Nagad, Bank Transfer) and handle refunds or corrections.
*   **Advanced Security:**
    *   Offline-first architecture ensures data privacy.
    *   Built-in App Lock supporting both PIN and Biometric (Fingerprint) authentication.
*   **Manual Cloud Backup:** Securely export and restore encrypted, AES-256-GCM chunked backups locally or to your private Google Drive AppData folder.
*   **Dual Language & Theme:** Full support for English and Bangla localizations, alongside Light and Dark mode UI themes.

## 🔐 Authentication

TutorSync offers flexible authentication tailored for offline reliability:
*   **Email & Password:** Standard Firebase authentication integrated with a local SQLite fallback, allowing you to log in offline if you've signed in previously.
*   **Google Sign-In:** One-tap authentication using Google Identity Services.
*   **Password Recovery:** Secure password recovery flows utilizing Firebase reset emails, mapped safely to the offline database.

## 🛠 Technology Stack

*   **Language:** Java
*   **UI Framework:** Android XML, Material Design 3, View Binding
*   **Local Database:** SQLite (Managed via `SQLiteOpenHelper`)
*   **Cloud Services:**
    *   **Firebase Authentication:** For robust identity management.
    *   **Firebase Realtime Database:** Used for tracking live session presence (online/offline status).
    *   **Cloud Firestore:** Used for lightweight backup synchronization of account metadata and active student/subject counts.
    *   **Google Drive API (REST v3):** For manual, user-controlled secure cloud backups using the hidden `drive.appdata` scope.
*   **Background Processing:** Custom `AppExecutors` thread pooling for database and file I/O operations.

## 🏗 Architecture

TutorSync follows a robust, repository-driven architecture adapted for an offline-first mobile environment:
*   **UI Layer (Activities & Adapters):** Standard Android Activity lifecycle, utilizing RecyclerViews and Material components for a responsive UI.
*   **Repository Layer:** Classes like `AttendanceRepository`, `InvoiceRepository`, and `StudentRepository` abstract raw SQLite operations, providing clean APIs for data access and complex SQL joins.
*   **Database Management:** `TutorSyncContract` defines all schemas, and a synchronized `DatabaseManager` ensures thread-safe access to the SQLite engine.
*   **Security & Encryption:** `AppLockManager` handles session security, while `BackupCrypto` secures database exports before they leave the app sandbox.

## 🗄 Database and Cloud

*   **Local SQLite:** The absolute source of truth. All students, schedules, transactions, and invoices are stored directly on the device ensuring 100% offline functionality.
*   **Firebase Services:** Operates securely in the background when an internet connection is available to log session presence and mirror minimal account state, without risking local data integrity.
*   **Google Drive AppData:** Tutors can manually push their encrypted database to Google Drive. This uses the app's hidden AppData folder, meaning it doesn't consume visible Drive space and is protected from other applications.

## 📱 Screenshots

| Dashboard Overview | Student Directory |
| :---: | :---: |
| ![Dashboard](screenshots/dashboard.png) | ![Student Directory](screenshots/student_directory.png) |

| Attendance & Lesson Log | Invoice Detail & PDF |
| :---: | :---: |
| ![Attendance](screenshots/attendance_sheet.png) | ![Invoice Detail](screenshots/invoice_detail.png) |

| App Settings & Security |
| :---: |
| ![Settings](screenshots/settings_screen.png) |

## ⚙️ Project Configuration

*   **Minimum SDK:** 26 (Android 8.0 Oreo)
*   **Target SDK:** 37
*   **Compile SDK:** 37
*   **Java Version:** 17

## 💻 Installation and Setup

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/abdussamadcodes/TutorSync.git
    ```
2.  **Open in Android Studio:** Open the cloned directory using Android Studio (Ladybug or newer recommended).
3.  **Firebase Configuration:**
    *   Create a Firebase Project in the Firebase Console.
    *   Enable Authentication (Email/Password & Google), Realtime Database, and Cloud Firestore.
    *   Download your `google-services.json` file and place it in the `app/` directory.
4.  **Sync Gradle:** Allow Android Studio to download the required dependencies, including Material Design 3 and Google API client libraries.
5.  **Run the App:** Deploy to an emulator or physical device running Android 8.0+.

## 👨‍💻 Author

**Md. Abdus Samad**
*   GitHub: [https://github.com/abdussamadcodes](https://github.com/abdussamadcodes)