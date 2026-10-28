package com.abdussamadcodes.tutorsync.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AttendanceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.InvoiceItemEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.OccurrenceEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentAllocationEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.PaymentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.StudentEntry;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.TransactionEntry;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.security.SessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class FinancialPdfGenerator {

    // Standard A4 dimensions in PostScript points (1/72 inch)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN_H = 40f;
    private static final float MARGIN_TOP = 38f;
    private static final float FOOTER_RESERVE = 56f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2f);

    // High-Contrast, Print-Safe Minimal Palette (Crisp in both Color & B&W)
    private static final int COLOR_INK_STRONG = Color.parseColor("#0F172A");    // Deep Slate/Black
    private static final int COLOR_INK_BODY = Color.parseColor("#1E293B");      // Dark Slate
    private static final int COLOR_INK_SECONDARY = Color.parseColor("#475569"); // Medium Slate
    private static final int COLOR_INK_MUTED = Color.parseColor("#64748B");     // Muted Label Slate
    private static final int COLOR_ACCENT = Color.parseColor("#1D4ED8");        // Subtle Deep Blue

    private static final int COLOR_RULE_DARK = Color.parseColor("#0F172A");     // Structural Rule
    private static final int COLOR_RULE_MEDIUM = Color.parseColor("#CBD5E1");   // Card/Box Border
    private static final int COLOR_RULE_LIGHT = Color.parseColor("#E2E8F0");    // Row Divider
    private static final int COLOR_SURFACE_SUBTLE = Color.parseColor("#F8FAFC");// Minimal Tint

    // High-contrast semantic status colors (paired with explicit text labels)
    private static final int COLOR_STATUS_PAID = Color.parseColor("#15803D");
    private static final int COLOR_STATUS_PARTIAL = Color.parseColor("#B45309");
    private static final int COLOR_STATUS_UNPAID = Color.parseColor("#B91C1C");
    private static final int COLOR_STATUS_VOID = Color.parseColor("#475569");

    private FinancialPdfGenerator() {}

    // =========================================================================================
    // 1. PUBLIC ENTRY POINTS (Preserves all existing signatures across Activities)
    // =========================================================================================

    @Nullable
    public static File generateInvoicePdf(@NonNull Context context, Object... args) {
        String invoiceId = resolveInvoiceIdFromArgs(args);
        if (TextUtils.isEmpty(invoiceId)) return null;

        InvoiceData data = loadInvoiceData(context, invoiceId);
        if (data == null) return null;

        File pdf = renderInvoiceDocument(context, data);
        if (pdf != null) {
            savePdfToDownloads(context, pdf);
        }
        return pdf;
    }

    @Nullable
    public static File generatePaymentReceiptPdf(@NonNull Context context, Object... args) {
        String txnOrPaymentId = null;
        String invoiceIdHint = null;
        String studentIdHint = null;

        if (args != null) {
            for (Object arg : args) {
                if (arg == null || arg instanceof Collection<?>) continue;
                if (arg instanceof Invoice) {
                    invoiceIdHint = ((Invoice) arg).getId();
                } else if (arg instanceof Student) {
                    studentIdHint = ((Student) arg).getId();
                } else if (!(arg instanceof String)) {
                    String candidateId = extractId(arg);
                    if (!TextUtils.isEmpty(candidateId)) {
                        txnOrPaymentId = candidateId;
                    }
                }
            }
            if (TextUtils.isEmpty(txnOrPaymentId)) {
                txnOrPaymentId = resolveTransactionIdFromStrings(args);
            }
        }

        if (TextUtils.isEmpty(txnOrPaymentId)) return null;

        ReceiptData data = loadReceiptData(context, txnOrPaymentId, invoiceIdHint, studentIdHint);
        if (data == null) return null;

        File pdf = renderReceiptDocument(context, data);
        if (pdf != null) {
            savePdfToDownloads(context, pdf);
        }
        return pdf;
    }

    @Nullable
    public static File generateReceiptPdf(@NonNull Context context, Object... args) {
        return generatePaymentReceiptPdf(context, args);
    }

    @Nullable
    public static File generateTransactionReceiptPdf(@NonNull Context context, Object... args) {
        return generatePaymentReceiptPdf(context, args);
    }

    @Nullable
    public static File generateStudentStatementPdf(@NonNull Context context, Object... args) {
        String studentId = resolveStudentIdFromArgs(args);
        if (TextUtils.isEmpty(studentId)) return null;

        StatementData data = loadStudentStatementData(context, studentId);
        if (data == null) return null;

        File pdf = renderStudentStatementDocument(context, data);
        if (pdf != null) {
            savePdfToDownloads(context, pdf);
        }
        return pdf;
    }

    @Nullable
    public static File generateStatementPdf(@NonNull Context context, Object... args) {
        return generateStudentStatementPdf(context, args);
    }

    @Nullable
    public static File generateTransactionHistoryPdf(@NonNull Context context, Object... args) {
        return generateStudentStatementPdf(context, args);
    }

    @Nullable
    public static File generateTransactionsPdf(@NonNull Context context, Object... args) {
        return generateStudentStatementPdf(context, args);
    }

    @Nullable
    public static File generateStudentTransactionsPdf(@NonNull Context context, Object... args) {
        return generateStudentStatementPdf(context, args);
    }

    @Nullable
    public static File generateFinancialReportPdf(@NonNull Context context, @Nullable String billingPeriodFilter) {
        FinancialReportData data = loadFinancialReportData(context, billingPeriodFilter);
        if (data == null) return null;
        File pdf = renderFinancialReportDocument(context, data);
        if (pdf != null) {
            savePdfToDownloads(context, pdf);
        }
        return pdf;
    }

    @Nullable
    public static Uri savePdfToDownloads(@NonNull Context context, @NonNull File pdfFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, pdfFile.getName());
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TutorSync");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri itemUri = context.getContentResolver().insert(collection, values);
                if (itemUri == null) return null;

                try (OutputStream out = context.getContentResolver().openOutputStream(itemUri);
                     FileInputStream in = new FileInputStream(pdfFile)) {
                    if (out == null) return null;
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                context.getContentResolver().update(itemUri, values, null, null);
                return itemUri;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TutorSync");
                if (!dir.exists()) dir.mkdirs();
                File dest = new File(dir, pdfFile.getName());
                try (FileInputStream in = new FileInputStream(pdfFile);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                return Uri.fromFile(dest);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void sharePdf(@NonNull Context context, @Nullable File pdfFile) {
        sharePdf(context, pdfFile, "Share PDF Document");
    }

    public static void sharePdf(@NonNull Context context, @Nullable File pdfFile, @Nullable String chooserTitle) {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(context, "Unable to generate PDF file", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = resolveShareableUri(context, pdfFile);
            if (uri == null) return;

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent,
                    chooserTitle != null ? chooserTitle : "Share PDF Document"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void openPdf(@NonNull Context context, @Nullable File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) return;
        try {
            Uri uri = resolveShareableUri(context, pdfFile);
            if (uri == null) return;

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
            context.startActivity(Intent.createChooser(intent, "Open PDF"));
        } catch (Exception e) {
            sharePdf(context, pdfFile, "Open or Share PDF");
        }
    }

    @Nullable
    private static Uri resolveShareableUri(@NonNull Context context, @NonNull File pdfFile) {
        try {
            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    pdfFile
            );
        } catch (Exception e) {
            return savePdfToDownloads(context, pdfFile);
        }
    }

    @Nullable
    private static String extractId(@Nullable Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) {
            String s = ((String) obj).trim();
            return s.isEmpty() ? null : s;
        }
        try {
            Method m = obj.getClass().getMethod("getId");
            Object val = m.invoke(obj);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String resolveInvoiceIdFromArgs(@Nullable Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Invoice) {
                return ((Invoice) arg).getId();
            }
        }
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            for (Object arg : args) {
                if (arg instanceof String) {
                    String candidate = ((String) arg).trim();
                    if (!candidate.isEmpty() && querySingleInvoice(db, candidate) != null) {
                        return candidate;
                    }
                }
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @Nullable
    private static String resolveTransactionIdFromStrings(@Nullable Object[] args) {
        if (args == null) return null;
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            for (Object arg : args) {
                if (arg instanceof String) {
                    String s = ((String) arg).trim();
                    if (s.isEmpty()) continue;
                    try (Cursor c = db.query(TransactionEntry.TABLE_NAME, new String[]{TransactionEntry.COLUMN_ID},
                            TransactionEntry.COLUMN_ID + " = ? OR " + TransactionEntry.COLUMN_TXN_ID_DISPLAY + " = ?",
                            new String[]{s, s}, null, null, null)) {
                        if (c != null && c.moveToFirst()) return c.getString(0);
                    }
                    try (Cursor c = db.query(PaymentEntry.TABLE_NAME, new String[]{PaymentEntry.COLUMN_ID},
                            PaymentEntry.COLUMN_ID + " = ?", new String[]{s}, null, null, null)) {
                        if (c != null && c.moveToFirst()) return c.getString(0);
                    }
                }
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    @Nullable
    private static String resolveStudentIdFromArgs(@Nullable Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Student) {
                return ((Student) arg).getId();
            }
        }
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            for (Object arg : args) {
                if (arg instanceof String) {
                    String s = ((String) arg).trim();
                    if (s.isEmpty()) continue;
                    try (Cursor c = db.query(StudentEntry.TABLE_NAME, new String[]{StudentEntry.COLUMN_ID},
                            StudentEntry.COLUMN_ID + " = ? OR " + StudentEntry.COLUMN_STUDENT_ID_DISPLAY + " = ?",
                            new String[]{s, s}, null, null, null)) {
                        if (c != null && c.moveToFirst()) return c.getString(0);
                    }
                }
            }
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
        return null;
    }

    // =========================================================================================
    // 2. MINIMAL, SINGLE-PAGE INVOICE PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderInvoiceDocument(@NonNull Context context, @NonNull InvoiceData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoSmall = loadLogoBitmap(context, 36, COLOR_INK_STRONG);

        try {
            PageHolder current = startNewPage(pdfDocument, 1);
            pages.add(current);
            Canvas canvas = current.canvas;

            // 1. Clean Top Header: Brand + Invoice Number + Explicit Text Status Badge
            String statusText = resolveExplicitStatusText(data.status, data.isVoid);
            int statusColor = resolveStatusColor(data.status, data.isVoid);
            float y = drawMinimalHeader(canvas, logoSmall, "INVOICE", data.invoiceDisplayId, statusText, statusColor);
            y += 16f;

            // 2. Two-Column Party & Metadata Block (Tutor & Student Info on Left, Dates on Right)
            y = drawInvoicePartiesAndDates(canvas, y, data);
            y += 16f;

            // 3. Three Key Figures Strip: Total Amount | Amount Paid | Amount Due
            int currentDue = Math.max(0, data.totalAmountMinor - data.paidAmountMinor);
            y = drawThreeKeyFiguresStrip(
                    canvas,
                    y,
                    "TOTAL AMOUNT", formatCurrency(data.totalAmountMinor),
                    "AMOUNT PAID", formatCurrency(data.paidAmountMinor),
                    "AMOUNT DUE", formatCurrency(currentDue),
                    currentDue > 0 ? COLOR_STATUS_UNPAID : COLOR_INK_STRONG
            );
            y += 20f;

            // 4. Simple Class Fee Table
            y = drawFeeTableHeader(canvas, y);

            for (int i = 0; i < data.items.size(); i++) {
                LineItemRow item = data.items.get(i);
                float rowH = measureFeeItemRowHeight(item);

                if (y + rowH > PAGE_HEIGHT - FOOTER_RESERVE) {
                    current = startNewPage(pdfDocument, pages.size() + 1);
                    pages.add(current);
                    canvas = current.canvas;
                    y = drawContinuationBar(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                    y += 12f;
                    y = drawFeeTableHeader(canvas, y);
                }
                y = drawFeeItemRow(canvas, y, item);
            }

            // Minimal inline adjustments (only shown if discount or previous unpaid balance exists)
            y = drawMinimalFeeAdjustments(canvas, y, data);
            y += 18f;

            // 5. Compact Payment History Section
            float paymentSectionMinH = data.payments.isEmpty() ? 42f : 56f;
            if (y + paymentSectionMinH > PAGE_HEIGHT - FOOTER_RESERVE) {
                current = startNewPage(pdfDocument, pages.size() + 1);
                pages.add(current);
                canvas = current.canvas;
                y = drawContinuationBar(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                y += 12f;
            }

            y = drawCompactPaymentsHeader(canvas, y);
            if (data.payments.isEmpty()) {
                y = drawCompactEmptyRow(canvas, y, "No payments recorded for this invoice.");
            } else {
                for (int i = 0; i < data.payments.size(); i++) {
                    PaymentRow p = data.payments.get(i);
                    if (y + 20f > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationBar(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                        y += 12f;
                        y = drawCompactPaymentsHeader(canvas, y);
                    }
                    y = drawCompactPaymentRow(canvas, y, p);
                }
            }

            // 6. Optional Notes (compact, only if present)
            if (!TextUtils.isEmpty(data.notes)) {
                y += 14f;
                if (y + 36f > PAGE_HEIGHT - FOOTER_RESERVE) {
                    current = startNewPage(pdfDocument, pages.size() + 1);
                    pages.add(current);
                    canvas = current.canvas;
                    y = drawContinuationBar(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                    y += 12f;
                }
                y = drawCompactNoteBlock(canvas, y, data.notes);
            }

            // 7. Minimal Footer on all pages
            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                drawMinimalFooter(pages.get(i).canvas, i + 1, totalPages);
                pdfDocument.finishPage(pages.get(i).page);
            }

            String safeInvoiceName = sanitizeFilename(data.invoiceDisplayId) + "_"
                    + sanitizeFilename(data.student.studentIdDisplay != null ? data.student.studentIdDisplay : data.student.name);
            File outputFile = getOutputPdfFile(context, "Invoice_" + safeInvoiceName + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                pdfDocument.writeTo(fos);
            }
            return outputFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    // =========================================================================================
    // 3. MINIMAL PAYMENT RECEIPT PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderReceiptDocument(@NonNull Context context, @NonNull ReceiptData data) {
        PdfDocument pdfDocument = new PdfDocument();
        Bitmap logoSmall = loadLogoBitmap(context, 36, COLOR_INK_STRONG);

        try {
            PageHolder current = startNewPage(pdfDocument, 1);
            Canvas canvas = current.canvas;

            String statusText = data.isReversed ? "STATUS: REVERSED" : "STATUS: CONFIRMED";
            int statusColor = data.isReversed ? COLOR_STATUS_VOID : COLOR_STATUS_PAID;

            float y = drawMinimalHeader(canvas, logoSmall, "PAYMENT RECEIPT", data.receiptDisplayId, statusText, statusColor);
            y += 16f;

            // Two-Column Party & Receipt Metadata
            y = drawReceiptPartiesAndDates(canvas, y, data);
            y += 18f;

            // Key Figures Strip
            if (data.invoiceTotalMinor > 0) {
                int remainingDue = Math.max(0, data.invoiceTotalMinor - data.invoicePaidMinor);
                y = drawThreeKeyFiguresStrip(
                        canvas,
                        y,
                        "INVOICE TOTAL", formatCurrency(data.invoiceTotalMinor),
                        "THIS PAYMENT", formatCurrency(data.amountMinor),
                        "REMAINING DUE", formatCurrency(remainingDue),
                        remainingDue > 0 ? COLOR_STATUS_UNPAID : COLOR_INK_STRONG
                );
            } else {
                y = drawThreeKeyFiguresStrip(
                        canvas,
                        y,
                        "TRANSACTION TYPE", nonEmptyOrDefault(data.type, "PAYMENT"),
                        "PAYMENT METHOD", nonEmptyOrDefault(data.method, "Cash"),
                        "AMOUNT RECEIVED", formatCurrency(data.amountMinor),
                        COLOR_INK_STRONG
                );
            }
            y += 22f;

            if (!TextUtils.isEmpty(data.notes)) {
                drawCompactNoteBlock(canvas, y, data.notes);
            }

            drawMinimalFooter(canvas, 1, 1);
            pdfDocument.finishPage(current.page);

            String safeId = sanitizeFilename(data.receiptDisplayId);
            File outputFile = getOutputPdfFile(context, "Receipt_" + safeId + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                pdfDocument.writeTo(fos);
            }
            return outputFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    // =========================================================================================
    // 4. MINIMAL STUDENT STATEMENT PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderStudentStatementDocument(@NonNull Context context, @NonNull StatementData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoSmall = loadLogoBitmap(context, 36, COLOR_INK_STRONG);

        try {
            PageHolder current = startNewPage(pdfDocument, 1);
            pages.add(current);
            Canvas canvas = current.canvas;

            int totalDue = Math.max(0, data.totalBilledMinor - data.totalPaidMinor);
            String statusText = totalDue == 0 ? "STATUS: SETTLED" : "STATUS: BALANCE DUE";
            int statusColor = totalDue == 0 ? COLOR_STATUS_PAID : COLOR_STATUS_UNPAID;
            String refId = !TextUtils.isEmpty(data.student.studentIdDisplay) ? data.student.studentIdDisplay : "STATEMENT";

            float y = drawMinimalHeader(canvas, logoSmall, "STUDENT STATEMENT", refId, statusText, statusColor);
            y += 16f;

            // Tutor & Student Info Row
            y = drawStatementPartiesBlock(canvas, y, data.tutor, data.student);
            y += 16f;

            // 3 Key Figures Strip
            y = drawThreeKeyFiguresStrip(
                    canvas,
                    y,
                    "TOTAL AMOUNT", formatCurrency(data.totalBilledMinor),
                    "AMOUNT PAID", formatCurrency(data.totalPaidMinor),
                    "AMOUNT DUE", formatCurrency(totalDue),
                    totalDue > 0 ? COLOR_STATUS_UNPAID : COLOR_INK_STRONG
            );
            y += 20f;

            // Invoices Table
            y = drawStatementInvoicesHeader(canvas, y);
            if (data.invoices.isEmpty()) {
                y = drawCompactEmptyRow(canvas, y, "No invoices issued for this student.");
            } else {
                for (InvoiceData inv : data.invoices) {
                    if (y + 20f > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationBar(canvas, "STUDENT STATEMENT", data.student.getFormattedName());
                        y += 12f;
                        y = drawStatementInvoicesHeader(canvas, y);
                    }
                    y = drawStatementInvoiceRow(canvas, y, inv);
                }
            }
            y += 18f;

            // Payments History
            if (y + 50f > PAGE_HEIGHT - FOOTER_RESERVE) {
                current = startNewPage(pdfDocument, pages.size() + 1);
                pages.add(current);
                canvas = current.canvas;
                y = drawContinuationBar(canvas, "STUDENT STATEMENT", data.student.getFormattedName());
                y += 12f;
            }

            y = drawCompactPaymentsHeader(canvas, y);
            if (data.transactions.isEmpty()) {
                drawCompactEmptyRow(canvas, y, "No payments recorded for this student.");
            } else {
                for (PaymentRow p : data.transactions) {
                    if (y + 20f > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationBar(canvas, "STUDENT STATEMENT", data.student.getFormattedName());
                        y += 12f;
                        y = drawCompactPaymentsHeader(canvas, y);
                    }
                    y = drawCompactPaymentRow(canvas, y, p);
                }
            }

            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                drawMinimalFooter(pages.get(i).canvas, i + 1, totalPages);
                pdfDocument.finishPage(pages.get(i).page);
            }

            String safeId = sanitizeFilename(data.student.studentIdDisplay != null ? data.student.studentIdDisplay : data.student.name);
            File outputFile = getOutputPdfFile(context, "Statement_" + safeId + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                pdfDocument.writeTo(fos);
            }
            return outputFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    // =========================================================================================
    // 5. MINIMAL FINANCIAL SUMMARY REPORT PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderFinancialReportDocument(@NonNull Context context, @NonNull FinancialReportData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoSmall = loadLogoBitmap(context, 36, COLOR_INK_STRONG);

        try {
            PageHolder current = startNewPage(pdfDocument, 1);
            pages.add(current);
            Canvas canvas = current.canvas;

            int totalDue = Math.max(0, data.totalBilledMinor - data.totalCollectedMinor);
            String statusText = "PERIOD: " + data.periodLabel.toUpperCase(Locale.US);

            float y = drawMinimalHeader(canvas, logoSmall, "FINANCIAL REPORT", data.periodLabel, statusText, COLOR_INK_STRONG);
            y += 16f;

            y = drawThreeKeyFiguresStrip(
                    canvas,
                    y,
                    "TOTAL AMOUNT", formatCurrency(data.totalBilledMinor),
                    "AMOUNT PAID", formatCurrency(data.totalCollectedMinor),
                    "AMOUNT DUE", formatCurrency(totalDue),
                    totalDue > 0 ? COLOR_STATUS_UNPAID : COLOR_INK_STRONG
            );
            y += 20f;

            y = drawReportTableHeader(canvas, y);
            if (data.invoices.isEmpty()) {
                drawCompactEmptyRow(canvas, y, "No invoices found for this period.");
            } else {
                for (InvoiceData inv : data.invoices) {
                    if (y + 20f > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationBar(canvas, "FINANCIAL REPORT", data.periodLabel);
                        y += 12f;
                        y = drawReportTableHeader(canvas, y);
                    }
                    y = drawReportInvoiceRow(canvas, y, inv);
                }
            }

            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                drawMinimalFooter(pages.get(i).canvas, i + 1, totalPages);
                pdfDocument.finishPage(pages.get(i).page);
            }

            String safePeriod = sanitizeFilename(data.periodLabel);
            File outputFile = getOutputPdfFile(context, "Financial_Report_" + safePeriod + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                pdfDocument.writeTo(fos);
            }
            return outputFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            pdfDocument.close();
        }
    }

    // =========================================================================================
    // 6. MINIMAL, HIGH-CONTRAST DRAWING BUILDING BLOCKS
    // =========================================================================================

    private static PageHolder startNewPage(@NonNull PdfDocument doc, int pageNumber) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        // Pure white background with no watermark or ink-heavy blocks
        canvas.drawColor(Color.WHITE);
        return new PageHolder(page, canvas);
    }

    /**
     * Top header with subtle TutorSync branding on the left, and Document Title + Outlined Text Status Badge on the right.
     */
    private static float drawMinimalHeader(@NonNull Canvas canvas,
                                           @Nullable Bitmap logoSmall,
                                           @NonNull String docTitle,
                                           @NonNull String docNumber,
                                           @NonNull String statusBadgeText,
                                           int statusColor) {
        float y = MARGIN_TOP;
        float leftX = MARGIN_H;

        // Subtle TutorSync Brand Mark + Wordmark
        if (logoSmall != null) {
            RectF dst = new RectF(leftX, y, leftX + 18f, y + 18f);
            Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(logoSmall, null, dst, bmpPaint);
            leftX += 24f;
        }

        TextPaint brandPaint = createTextPaint(11f, Typeface.BOLD, COLOR_INK_STRONG);
        brandPaint.setLetterSpacing(0.06f);
        canvas.drawText("TUTORSYNC", leftX, y + 13f, brandPaint);

        // Document Title (Top Left below brand)
        TextPaint titlePaint = createTextPaint(20f, Typeface.BOLD, COLOR_INK_STRONG);
        canvas.drawText(docTitle, MARGIN_H, y + 38f, titlePaint);

        // Right Side: Document Number + Text-First Outlined Status Badge
        float rightEdge = PAGE_WIDTH - MARGIN_H;
        TextPaint numPaint = createTextPaint(12f, Typeface.BOLD, COLOR_INK_STRONG);
        numPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(docNumber, rightEdge, y + 14f, numPaint);

        drawOutlinedStatusBadge(canvas, rightEdge, y + 22f, statusBadgeText, statusColor);

        float bottomY = y + 48f;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_DARK, 1.2f);
        return bottomY;
    }

    /**
     * Outlined badge that communicates payment status clearly via explicit text + border (works in B&W and Color).
     */
    private static void drawOutlinedStatusBadge(@NonNull Canvas canvas,
                                                float rightEdge,
                                                float topY,
                                                @NonNull String text,
                                                int strokeAndTextColor) {
        TextPaint tp = createTextPaint(8.5f, Typeface.BOLD, strokeAndTextColor);
        tp.setLetterSpacing(0.04f);
        float textW = tp.measureText(text);
        float padH = 9f;
        float boxW = textW + (padH * 2f);
        float boxH = 18f;

        RectF rect = new RectF(rightEdge - boxW, topY, rightEdge, topY + boxH);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1.1f);
        border.setColor(strokeAndTextColor);
        canvas.drawRoundRect(rect, 4f, 4f, border);

        canvas.drawText(text, rect.left + padH, rect.top + 12.2f, tp);
    }

    /**
     * Clean 3-column block:
     * Column 1: FROM (Tutor)
     * Column 2: BILLED TO (Student Name · STU-XXXX)
     * Column 3: INVOICE DETAILS (Billing Period, Issue Date, Due Date)
     */
    private static float drawInvoicePartiesAndDates(@NonNull Canvas canvas, float topY, @NonNull InvoiceData data) {
        float col1X = MARGIN_H;
        float col2X = MARGIN_H + 175f;
        float col3X = MARGIN_H + 355f;
        float rightEdge = PAGE_WIDTH - MARGIN_H;
        float colWidth = 165f;

        TextPaint labelPaint = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_MUTED);
        labelPaint.setLetterSpacing(0.06f);
        TextPaint namePaint = createTextPaint(10.5f, Typeface.BOLD, COLOR_INK_STRONG);
        TextPaint bodyPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);

        // Column 1: Tutor Info
        float y1 = topY + 10f;
        canvas.drawText("FROM (TUTOR)", col1X, y1, labelPaint);
        y1 += 15f;
        String tutorName = !TextUtils.isEmpty(data.tutor.fullName) ? data.tutor.fullName : "TutorSync Educator";
        canvas.drawText(ellipsize(tutorName, namePaint, colWidth), col1X, y1, namePaint);
        y1 += 13f;

        if (!TextUtils.isEmpty(data.tutor.phone)) {
            canvas.drawText(ellipsize(data.tutor.phone, bodyPaint, colWidth), col1X, y1, bodyPaint);
            y1 += 12.5f;
        }
        if (!TextUtils.isEmpty(data.tutor.email)) {
            canvas.drawText(ellipsize(data.tutor.email, bodyPaint, colWidth), col1X, y1, bodyPaint);
            y1 += 12.5f;
        }
        if (!TextUtils.isEmpty(data.tutor.address)) {
            canvas.drawText(ellipsize(data.tutor.address, bodyPaint, colWidth), col1X, y1, bodyPaint);
            y1 += 12.5f;
        }

        // Column 2: Student Info (Name · STU-XXXX without duplicate ID row)
        float y2 = topY + 10f;
        canvas.drawText("BILLED TO (STUDENT)", col2X, y2, labelPaint);
        y2 += 15f;
        canvas.drawText(ellipsize(data.student.getFormattedName(), namePaint, colWidth), col2X, y2, namePaint);
        y2 += 13f;

        String gradeSchool = data.student.gradeOrSchool();
        if (!TextUtils.isEmpty(gradeSchool)) {
            canvas.drawText(ellipsize(gradeSchool, bodyPaint, colWidth), col2X, y2, bodyPaint);
            y2 += 12.5f;
        }
        String guardian = data.student.guardianSummary();
        if (!TextUtils.isEmpty(guardian)) {
            canvas.drawText(ellipsize(guardian, bodyPaint, colWidth), col2X, y2, bodyPaint);
            y2 += 12.5f;
        }
        if (!TextUtils.isEmpty(data.student.address)) {
            canvas.drawText(ellipsize(data.student.address, bodyPaint, colWidth), col2X, y2, bodyPaint);
            y2 += 12.5f;
        }

        // Column 3: Dates & Billing Period
        float y3 = topY + 10f;
        canvas.drawText("DETAILS", col3X, y3, labelPaint);
        y3 += 15f;

        drawMetaPair(canvas, col3X, rightEdge, y3, "Billing Period", data.billingPeriod);
        y3 += 13.5f;

        if (data.periodStart > 0 && data.periodEnd > 0) {
            String cycle = formatDateShort(data.periodStart) + " – " + formatDateShort(data.periodEnd);
            drawMetaPair(canvas, col3X, rightEdge, y3, "Cycle", cycle);
            y3 += 13.5f;
        }

        drawMetaPair(canvas, col3X, rightEdge, y3, "Issue Date", formatDate(data.issueDate));
        y3 += 13.5f;

        if (data.dueDate > 0) {
            drawMetaPair(canvas, col3X, rightEdge, y3, "Due Date", formatDate(data.dueDate));
            y3 += 13.5f;
        }

        return Math.max(y1, Math.max(y2, y3)) + 2f;
    }

    private static float drawReceiptPartiesAndDates(@NonNull Canvas canvas, float topY, @NonNull ReceiptData data) {
        float col1X = MARGIN_H;
        float col2X = MARGIN_H + 175f;
        float col3X = MARGIN_H + 345f;
        float rightEdge = PAGE_WIDTH - MARGIN_H;
        float colWidth = 160f;

        TextPaint labelPaint = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_MUTED);
        labelPaint.setLetterSpacing(0.06f);
        TextPaint namePaint = createTextPaint(10.5f, Typeface.BOLD, COLOR_INK_STRONG);
        TextPaint bodyPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);

        float y1 = topY + 10f;
        canvas.drawText("RECEIVED BY (TUTOR)", col1X, y1, labelPaint);
        y1 += 15f;
        String tutorName = !TextUtils.isEmpty(data.tutor.fullName) ? data.tutor.fullName : "TutorSync Educator";
        canvas.drawText(ellipsize(tutorName, namePaint, colWidth), col1X, y1, namePaint);
        y1 += 13f;
        if (!TextUtils.isEmpty(data.tutor.phone)) {
            canvas.drawText(ellipsize(data.tutor.phone, bodyPaint, colWidth), col1X, y1, bodyPaint);
            y1 += 12.5f;
        }
        if (!TextUtils.isEmpty(data.tutor.email)) {
            canvas.drawText(ellipsize(data.tutor.email, bodyPaint, colWidth), col1X, y1, bodyPaint);
            y1 += 12.5f;
        }

        float y2 = topY + 10f;
        canvas.drawText("STUDENT", col2X, y2, labelPaint);
        y2 += 15f;
        canvas.drawText(ellipsize(data.student.getFormattedName(), namePaint, colWidth), col2X, y2, namePaint);
        y2 += 13f;
        if (!TextUtils.isEmpty(data.student.gradeOrSchool())) {
            canvas.drawText(ellipsize(data.student.gradeOrSchool(), bodyPaint, colWidth), col2X, y2, bodyPaint);
            y2 += 12.5f;
        }
        if (!TextUtils.isEmpty(data.student.guardianSummary())) {
            canvas.drawText(ellipsize(data.student.guardianSummary(), bodyPaint, colWidth), col2X, y2, bodyPaint);
            y2 += 12.5f;
        }

        float y3 = topY + 10f;
        canvas.drawText("PAYMENT DETAILS", col3X, y3, labelPaint);
        y3 += 15f;
        drawMetaPair(canvas, col3X, rightEdge, y3, "Date", formatDate(data.paymentTimestamp));
        y3 += 13.5f;
        drawMetaPair(canvas, col3X, rightEdge, y3, "Method", nonEmptyOrDefault(data.method, "Cash"));
        y3 += 13.5f;
        if (!TextUtils.isEmpty(data.invoiceDisplayId)) {
            drawMetaPair(canvas, col3X, rightEdge, y3, "Invoice", data.invoiceDisplayId);
            y3 += 13.5f;
        }
        if (!TextUtils.isEmpty(data.referenceNo)) {
            drawMetaPair(canvas, col3X, rightEdge, y3, "Ref", data.referenceNo);
            y3 += 13.5f;
        }

        return Math.max(y1, Math.max(y2, y3)) + 2f;
    }

    private static float drawStatementPartiesBlock(@NonNull Canvas canvas,
                                                   float topY,
                                                   @NonNull TutorProfile tutor,
                                                   @NonNull StudentProfile student) {
        float col1X = MARGIN_H;
        float col2X = MARGIN_H + 220f;

        TextPaint labelPaint = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_MUTED);
        labelPaint.setLetterSpacing(0.06f);
        TextPaint namePaint = createTextPaint(10.5f, Typeface.BOLD, COLOR_INK_STRONG);
        TextPaint bodyPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);

        float y1 = topY + 10f;
        canvas.drawText("TUTOR", col1X, y1, labelPaint);
        y1 += 15f;
        canvas.drawText(nonEmptyOrDefault(tutor.fullName, "TutorSync Educator"), col1X, y1, namePaint);
        y1 += 13f;
        String tutorContact = TextUtils.join("  •  ", nonEmptyList(tutor.phone, tutor.email));
        if (!TextUtils.isEmpty(tutorContact)) {
            canvas.drawText(ellipsize(tutorContact, bodyPaint, 200f), col1X, y1, bodyPaint);
            y1 += 12.5f;
        }

        float y2 = topY + 10f;
        canvas.drawText("STUDENT", col2X, y2, labelPaint);
        y2 += 15f;
        canvas.drawText(student.getFormattedName(), col2X, y2, namePaint);
        y2 += 13f;
        String studentMeta = TextUtils.join("  •  ", nonEmptyList(student.gradeOrSchool(), student.guardianSummary()));
        if (!TextUtils.isEmpty(studentMeta)) {
            canvas.drawText(ellipsize(studentMeta, bodyPaint, 290f), col2X, y2, bodyPaint);
            y2 += 12.5f;
        }

        return Math.max(y1, y2) + 2f;
    }

    /**
     * Highlights the 3 key figures (Total Amount, Amount Paid, Amount Due) in a clean bordered 3-column strip.
     */
    private static float drawThreeKeyFiguresStrip(@NonNull Canvas canvas,
                                                  float topY,
                                                  @NonNull String label1, @NonNull String val1,
                                                  @NonNull String label2, @NonNull String val2,
                                                  @NonNull String label3, @NonNull String val3,
                                                  int thirdValueColor) {
        float boxH = 48f;
        RectF outer = new RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + boxH);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(COLOR_SURFACE_SUBTLE);
        canvas.drawRoundRect(outer, 5f, 5f, fill);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);
        border.setColor(COLOR_RULE_MEDIUM);
        canvas.drawRoundRect(outer, 5f, 5f, border);

        float colW = CONTENT_WIDTH / 3f;
        // Vertical dividers between the 3 key figures
        canvas.drawLine(MARGIN_H + colW, topY + 8f, MARGIN_H + colW, topY + boxH - 8f, border);
        canvas.drawLine(MARGIN_H + colW * 2f, topY + 8f, MARGIN_H + colW * 2f, topY + boxH - 8f, border);

        drawKeyFigureCell(canvas, MARGIN_H, topY, colW, label1, val1, COLOR_INK_STRONG);
        drawKeyFigureCell(canvas, MARGIN_H + colW, topY, colW, label2, val2, COLOR_INK_STRONG);
        drawKeyFigureCell(canvas, MARGIN_H + colW * 2f, topY, colW, label3, val3, thirdValueColor);

        return topY + boxH;
    }

    private static void drawKeyFigureCell(@NonNull Canvas canvas,
                                          float leftX,
                                          float topY,
                                          float width,
                                          @NonNull String label,
                                          @NonNull String value,
                                          int valueColor) {
        TextPaint lp = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_MUTED);
        lp.setLetterSpacing(0.06f);
        canvas.drawText(label, leftX + 14f, topY + 16f, lp);

        TextPaint vp = createTextPaint(14f, Typeface.BOLD, valueColor);
        canvas.drawText(ellipsize(value, vp, width - 24f), leftX + 14f, topY + 36f, vp);
    }

    /**
     * Minimal, ink-free table header for Class Fees.
     */
    private static float drawFeeTableHeader(@NonNull Canvas canvas, float topY) {
        TextPaint secPaint = createTextPaint(8f, Typeface.BOLD, COLOR_INK_STRONG);
        secPaint.setLetterSpacing(0.06f);
        canvas.drawText("CLASS & ROUTINE FEES", MARGIN_H, topY + 10f, secPaint);

        float headerY = topY + 16f;
        drawHorizontalRule(canvas, headerY, COLOR_RULE_DARK, 1f);

        float base = headerY + 14f;
        TextPaint th = createTextPaint(8f, Typeface.BOLD, COLOR_INK_SECONDARY);
        canvas.drawText("DESCRIPTION", MARGIN_H, base, th);
        canvas.drawText("TYPE", MARGIN_H + 265f, base, th);

        th.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("QTY", MARGIN_H + 355f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("RATE", MARGIN_H + 430f, base, th);
        canvas.drawText("AMOUNT", PAGE_WIDTH - MARGIN_H, base, th);

        float bottomY = headerY + 20f;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_MEDIUM, 0.8f);
        return bottomY;
    }

    private static float measureFeeItemRowHeight(@NonNull LineItemRow item) {
        TextPaint descPaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_BODY);
        StaticLayout descLayout = buildStaticLayout(item.description, descPaint, 250);
        float h = 8f + descLayout.getHeight();
        if (!TextUtils.isEmpty(item.classDatesSummary)) {
            TextPaint subPaint = createTextPaint(8f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            StaticLayout subLayout = buildStaticLayout(item.classDatesSummary, subPaint, 250);
            h += 2f + subLayout.getHeight();
        }
        return Math.max(22f, h + 5f);
    }

    private static float drawFeeItemRow(@NonNull Canvas canvas, float topY, @NonNull LineItemRow item) {
        float rowH = measureFeeItemRowHeight(item);
        float textTop = topY + 6f;

        TextPaint descPaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_BODY);
        StaticLayout descLayout = buildStaticLayout(item.description, descPaint, 250);
        canvas.save();
        canvas.translate(MARGIN_H, textTop);
        descLayout.draw(canvas);
        canvas.restore();

        if (!TextUtils.isEmpty(item.classDatesSummary)) {
            TextPaint datesPaint = createTextPaint(8f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            StaticLayout datesLayout = buildStaticLayout(item.classDatesSummary, datesPaint, 250);
            canvas.save();
            canvas.translate(MARGIN_H, textTop + descLayout.getHeight() + 2f);
            datesLayout.draw(canvas);
            canvas.restore();
        }

        String modelText = "PER_CLASS".equalsIgnoreCase(item.billingModel) ? "Per Class" : "Monthly";
        TextPaint regPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(modelText, MARGIN_H + 265f, textTop + 9f, regPaint);

        String qtyLabel = "PER_CLASS".equalsIgnoreCase(item.billingModel)
                ? item.quantity + (item.quantity == 1 ? " class" : " classes")
                : String.valueOf(item.quantity);
        regPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(qtyLabel, MARGIN_H + 355f, textTop + 9f, regPaint);

        regPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(item.unitPriceMinor), MARGIN_H + 430f, textTop + 9f, regPaint);

        TextPaint amtPaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_STRONG);
        amtPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(item.amountMinor), PAGE_WIDTH - MARGIN_H, textTop + 9f, amtPaint);

        float bottomY = topY + rowH;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_LIGHT, 0.7f);
        return bottomY;
    }

    /**
     * Only renders inline rows below the table when a discount or prior unpaid balance exists,
     * avoiding duplicate repetition of Total Amount / Amount Paid / Amount Due.
     */
    private static float drawMinimalFeeAdjustments(@NonNull Canvas canvas, float topY, @NonNull InvoiceData data) {
        float y = topY;
        float labelX = MARGIN_H + 310f;
        float rightEdge = PAGE_WIDTH - MARGIN_H;

        TextPaint labelPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        TextPaint valPaint = createTextPaint(9f, Typeface.BOLD, COLOR_INK_STRONG);
        valPaint.setTextAlign(Paint.Align.RIGHT);

        if (data.discountMinor > 0) {
            y += 14f;
            canvas.drawText("Subtotal", labelX, y, labelPaint);
            canvas.drawText(formatCurrency(data.subtotalMinor), rightEdge, y, valPaint);

            y += 15f;
            canvas.drawText("Discount", labelX, y, labelPaint);
            canvas.drawText("- " + formatCurrency(data.discountMinor), rightEdge, y, valPaint);

            y += 6f;
            canvas.drawLine(labelX, y, rightEdge, y, createLinePaint(COLOR_RULE_LIGHT, 0.8f));
        }

        if (data.previousDuesMinor > 0) {
            y += 15f;
            int currentDue = Math.max(0, data.totalAmountMinor - data.paidAmountMinor);
            int totalWithPrev = currentDue + data.previousDuesMinor;
            String prevLabel = !TextUtils.isEmpty(data.previousDuesRefs)
                    ? "Previous Unpaid (" + data.previousDuesRefs + ")"
                    : "Previous Unpaid Balance";
            canvas.drawText(ellipsize(prevLabel, labelPaint, 125f), labelX, y, labelPaint);
            canvas.drawText(formatCurrency(data.previousDuesMinor), rightEdge, y, valPaint);

            y += 15f;
            TextPaint boldLabel = createTextPaint(9f, Typeface.BOLD, COLOR_INK_STRONG);
            canvas.drawText("Total Balance (Incl. Previous)", labelX, y, boldLabel);
            canvas.drawText(formatCurrency(totalWithPrev), rightEdge, y, valPaint);
        }

        return y;
    }

    /**
     * Compact Payment History Section Header.
     */
    private static float drawCompactPaymentsHeader(@NonNull Canvas canvas, float topY) {
        TextPaint secPaint = createTextPaint(8f, Typeface.BOLD, COLOR_INK_STRONG);
        secPaint.setLetterSpacing(0.06f);
        canvas.drawText("PAYMENT HISTORY", MARGIN_H, topY + 10f, secPaint);

        float headerY = topY + 16f;
        drawHorizontalRule(canvas, headerY, COLOR_RULE_DARK, 1f);

        float base = headerY + 13.5f;
        TextPaint th = createTextPaint(8f, Typeface.BOLD, COLOR_INK_SECONDARY);
        canvas.drawText("RECEIPT ID", MARGIN_H, base, th);
        canvas.drawText("DATE", MARGIN_H + 105f, base, th);
        canvas.drawText("METHOD", MARGIN_H + 220f, base, th);
        canvas.drawText("REFERENCE / NOTES", MARGIN_H + 300f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("AMOUNT", PAGE_WIDTH - MARGIN_H, base, th);

        float bottomY = headerY + 19f;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_MEDIUM, 0.8f);
        return bottomY;
    }

    private static float drawCompactPaymentRow(@NonNull Canvas canvas, float topY, @NonNull PaymentRow p) {
        float rowH = 19f;
        float base = topY + 13f;

        TextPaint idPaint = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_BODY);
        canvas.drawText(ellipsize(p.displayId, idPaint, 95f), MARGIN_H, base, idPaint);

        TextPaint regPaint = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(formatDateTimeShort(p.timestamp), MARGIN_H + 105f, base, regPaint);
        canvas.drawText(ellipsize(nonEmptyOrDefault(p.method, "Cash"), regPaint, 72f), MARGIN_H + 220f, base, regPaint);

        String refNote = buildRefAndNote(p.referenceNo, p.notes);
        canvas.drawText(ellipsize(refNote, regPaint, 130f), MARGIN_H + 300f, base, regPaint);

        TextPaint amtPaint = createTextPaint(9f, Typeface.BOLD, COLOR_INK_STRONG);
        amtPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(p.amountMinor), PAGE_WIDTH - MARGIN_H, base, amtPaint);

        float bottomY = topY + rowH;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_LIGHT, 0.6f);
        return bottomY;
    }

    private static float drawCompactEmptyRow(@NonNull Canvas canvas, float topY, @NonNull String message) {
        TextPaint tp = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_MUTED);
        canvas.drawText(message, MARGIN_H, topY + 14f, tp);
        float bottomY = topY + 21f;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_LIGHT, 0.6f);
        return bottomY;
    }

    private static float drawCompactNoteBlock(@NonNull Canvas canvas, float topY, @NonNull String notes) {
        TextPaint label = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_MUTED);
        label.setLetterSpacing(0.06f);
        canvas.drawText("NOTES", MARGIN_H, topY + 9f, label);

        TextPaint body = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        StaticLayout layout = buildStaticLayout(notes, body, (int) CONTENT_WIDTH);
        canvas.save();
        canvas.translate(MARGIN_H, topY + 13f);
        layout.draw(canvas);
        canvas.restore();
        return topY + 15f + layout.getHeight();
    }

    private static float drawStatementInvoicesHeader(@NonNull Canvas canvas, float topY) {
        TextPaint secPaint = createTextPaint(8f, Typeface.BOLD, COLOR_INK_STRONG);
        secPaint.setLetterSpacing(0.06f);
        canvas.drawText("INVOICES", MARGIN_H, topY + 10f, secPaint);

        float headerY = topY + 16f;
        drawHorizontalRule(canvas, headerY, COLOR_RULE_DARK, 1f);

        float base = headerY + 13.5f;
        TextPaint th = createTextPaint(8f, Typeface.BOLD, COLOR_INK_SECONDARY);
        canvas.drawText("INVOICE", MARGIN_H, base, th);
        canvas.drawText("PERIOD", MARGIN_H + 85f, base, th);
        canvas.drawText("ISSUED", MARGIN_H + 195f, base, th);
        canvas.drawText("STATUS", MARGIN_H + 270f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("TOTAL", MARGIN_H + 395f, base, th);
        canvas.drawText("PAID", MARGIN_H + 455f, base, th);
        canvas.drawText("DUE", PAGE_WIDTH - MARGIN_H, base, th);

        float bottomY = headerY + 19f;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_MEDIUM, 0.8f);
        return bottomY;
    }

    private static float drawStatementInvoiceRow(@NonNull Canvas canvas, float topY, @NonNull InvoiceData inv) {
        float rowH = 19f;
        float base = topY + 13f;

        TextPaint idP = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_BODY);
        canvas.drawText(inv.invoiceDisplayId, MARGIN_H, base, idP);

        TextPaint reg = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(ellipsize(inv.billingPeriod, reg, 102f), MARGIN_H + 85f, base, reg);
        canvas.drawText(formatDateShort(inv.issueDate), MARGIN_H + 195f, base, reg);
        canvas.drawText(resolveShortStatusLabel(inv.status, inv.isVoid), MARGIN_H + 270f, base, reg);

        int due = Math.max(0, inv.totalAmountMinor - inv.paidAmountMinor);
        reg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(inv.totalAmountMinor), MARGIN_H + 395f, base, reg);
        canvas.drawText(formatCurrency(inv.paidAmountMinor), MARGIN_H + 455f, base, reg);

        TextPaint dueP = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_STRONG);
        dueP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(due), PAGE_WIDTH - MARGIN_H, base, dueP);

        float bottomY = topY + rowH;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_LIGHT, 0.6f);
        return bottomY;
    }

    private static float drawReportTableHeader(@NonNull Canvas canvas, float topY) {
        TextPaint secPaint = createTextPaint(8f, Typeface.BOLD, COLOR_INK_STRONG);
        secPaint.setLetterSpacing(0.06f);
        canvas.drawText("INVOICE SUMMARY", MARGIN_H, topY + 10f, secPaint);

        float headerY = topY + 16f;
        drawHorizontalRule(canvas, headerY, COLOR_RULE_DARK, 1f);

        float base = headerY + 13.5f;
        TextPaint th = createTextPaint(8f, Typeface.BOLD, COLOR_INK_SECONDARY);
        canvas.drawText("INVOICE", MARGIN_H, base, th);
        canvas.drawText("STUDENT", MARGIN_H + 72f, base, th);
        canvas.drawText("PERIOD", MARGIN_H + 230f, base, th);
        canvas.drawText("STATUS", MARGIN_H + 310f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("TOTAL", MARGIN_H + 402f, base, th);
        canvas.drawText("PAID", MARGIN_H + 458f, base, th);
        canvas.drawText("DUE", PAGE_WIDTH - MARGIN_H, base, th);

        float bottomY = headerY + 19f;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_MEDIUM, 0.8f);
        return bottomY;
    }

    private static float drawReportInvoiceRow(@NonNull Canvas canvas, float topY, @NonNull InvoiceData inv) {
        float rowH = 19f;
        float base = topY + 13f;

        TextPaint idP = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_BODY);
        canvas.drawText(ellipsize(inv.invoiceDisplayId, idP, 66f), MARGIN_H, base, idP);

        TextPaint reg = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(ellipsize(inv.student.getFormattedName(), reg, 150f), MARGIN_H + 72f, base, reg);
        canvas.drawText(ellipsize(inv.billingPeriod, reg, 75f), MARGIN_H + 230f, base, reg);
        canvas.drawText(resolveShortStatusLabel(inv.status, inv.isVoid), MARGIN_H + 310f, base, reg);

        int due = Math.max(0, inv.totalAmountMinor - inv.paidAmountMinor);
        reg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(inv.totalAmountMinor), MARGIN_H + 402f, base, reg);
        canvas.drawText(formatCurrency(inv.paidAmountMinor), MARGIN_H + 458f, base, reg);

        TextPaint dueP = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_STRONG);
        dueP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(due), PAGE_WIDTH - MARGIN_H, base, dueP);

        float bottomY = topY + rowH;
        drawHorizontalRule(canvas, bottomY, COLOR_RULE_LIGHT, 0.6f);
        return bottomY;
    }

    private static float drawContinuationBar(@NonNull Canvas canvas, @NonNull String docTitle, @NonNull String subtitle) {
        float y = MARGIN_TOP;
        TextPaint leftP = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_STRONG);
        canvas.drawText(docTitle + " (Continued)", MARGIN_H, y + 10f, leftP);

        TextPaint rightP = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        rightP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(subtitle, PAGE_WIDTH - MARGIN_H, y + 10f, rightP);

        drawHorizontalRule(canvas, y + 16f, COLOR_RULE_MEDIUM, 0.8f);
        return y + 16f;
    }

    private static void drawMinimalFooter(@NonNull Canvas canvas, int pageNum, int totalPages) {
        float footerTop = PAGE_HEIGHT - 38f;
        drawHorizontalRule(canvas, footerTop, COLOR_RULE_LIGHT, 0.8f);

        TextPaint leftFooter = createTextPaint(8f, Typeface.NORMAL, COLOR_INK_MUTED);
        canvas.drawText("Generated by TutorSync  •  " + formatDate(System.currentTimeMillis()), MARGIN_H, footerTop + 14f, leftFooter);

        TextPaint pagePaint = createTextPaint(8f, Typeface.BOLD, COLOR_INK_SECONDARY);
        pagePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Page " + pageNum + " of " + totalPages, PAGE_WIDTH - MARGIN_H, footerTop + 14f, pagePaint);
    }

    // =========================================================================================
    // 7. DATABASE QUERIES (Preserves all existing SQLite logic & calculations)
    // =========================================================================================

    @Nullable
    private static InvoiceData loadInvoiceData(@NonNull Context context, @NonNull String invoiceId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            InvoiceData data = querySingleInvoice(db, invoiceId);
            if (data == null) return null;

            String accountId = !TextUtils.isEmpty(data.accountId)
                    ? data.accountId
                    : new SessionManager(context).getActiveAccountId();
            data.tutor = queryTutorProfile(db, accountId);
            data.student = queryStudentProfile(db, data.studentId);
            data.items = queryInvoiceItemsWithClassDates(db, data);
            data.payments = queryPaymentsForInvoice(db, data.id);
            computePreviousDues(db, data);

            return data;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    private static InvoiceData querySingleInvoice(@NonNull SQLiteDatabase db, @NonNull String invoiceId) {
        try (Cursor c = db.query(
                InvoiceEntry.TABLE_NAME,
                null,
                InvoiceEntry.COLUMN_ID + " = ? OR " + InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY + " = ?",
                new String[]{invoiceId, invoiceId},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return parseInvoiceCursor(c);
            }
        }
        return null;
    }

    @NonNull
    private static InvoiceData parseInvoiceCursor(@NonNull Cursor c) {
        InvoiceData d = new InvoiceData();
        d.id = getStringOrEmpty(c, InvoiceEntry.COLUMN_ID);
        d.accountId = getStringOrEmpty(c, InvoiceEntry.COLUMN_ACCOUNT_ID);
        d.studentId = getStringOrEmpty(c, InvoiceEntry.COLUMN_STUDENT_ID);
        String disp = getStringOrNull(c, InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY);
        d.invoiceDisplayId = !TextUtils.isEmpty(disp) ? disp : ("#INV-" + d.id.substring(0, Math.min(6, d.id.length())).toUpperCase(Locale.US));
        d.billingPeriod = getStringOrEmpty(c, InvoiceEntry.COLUMN_BILLING_PERIOD);
        d.periodStart = getLongOrZero(c, InvoiceEntry.COLUMN_PERIOD_START);
        d.periodEnd = getLongOrZero(c, InvoiceEntry.COLUMN_PERIOD_END);
        d.createdAt = getLongOrZero(c, InvoiceEntry.COLUMN_CREATED_AT);
        long issue = getLongOrZero(c, InvoiceEntry.COLUMN_ISSUE_DATE);
        d.issueDate = issue > 0 ? issue : (d.createdAt > 0 ? d.createdAt : System.currentTimeMillis());
        d.dueDate = getLongOrZero(c, InvoiceEntry.COLUMN_DUE_DATE);
        d.discountMinor = getIntOrZero(c, InvoiceEntry.COLUMN_DISCOUNT_MINOR);
        d.totalAmountMinor = getIntOrZero(c, InvoiceEntry.COLUMN_TOTAL_AMOUNT);
        int sub = getIntOrZero(c, InvoiceEntry.COLUMN_SUBTOTAL_MINOR);
        d.subtotalMinor = sub > 0 ? sub : (d.totalAmountMinor + d.discountMinor);
        d.paidAmountMinor = getIntOrZero(c, InvoiceEntry.COLUMN_PAID_AMOUNT);
        d.status = nonEmptyOrDefault(getStringOrNull(c, InvoiceEntry.COLUMN_STATUS), Invoice.STATUS_UNPAID);
        d.notes = getStringOrNull(c, InvoiceEntry.COLUMN_NOTES);
        d.isVoid = getIntOrZero(c, InvoiceEntry.COLUMN_IS_VOID) == 1;
        return d;
    }

    @NonNull
    private static TutorProfile queryTutorProfile(@NonNull SQLiteDatabase db, @NonNull String accountId) {
        TutorProfile profile = new TutorProfile();
        if (TextUtils.isEmpty(accountId)) return profile;

        try (Cursor c = db.query(
                AccountEntry.TABLE_NAME,
                null,
                AccountEntry.COLUMN_ID + " = ?",
                new String[]{accountId},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                profile.fullName = trimOrNull(getStringOrNull(c, AccountEntry.COLUMN_FULL_NAME));
                profile.phone = trimOrNull(getStringOrNull(c, AccountEntry.COLUMN_PHONE));
                profile.email = trimOrNull(getStringOrNull(c, AccountEntry.COLUMN_EMAIL));
                profile.address = trimOrNull(getStringOrNull(c, AccountEntry.COLUMN_ADDRESS));
            }
        } catch (Exception ignored) {}
        return profile;
    }

    @NonNull
    private static StudentProfile queryStudentProfile(@NonNull SQLiteDatabase db, @NonNull String studentId) {
        StudentProfile s = new StudentProfile();
        s.name = "Unknown Student";
        if (TextUtils.isEmpty(studentId)) return s;

        try (Cursor c = db.query(
                StudentEntry.TABLE_NAME,
                null,
                StudentEntry.COLUMN_ID + " = ?",
                new String[]{studentId},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                s.id = getStringOrEmpty(c, StudentEntry.COLUMN_ID);
                s.studentIdDisplay = trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_STUDENT_ID_DISPLAY));
                s.name = nonEmptyOrDefault(trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_NAME)), "Unknown Student");
                s.grade = trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_GRADE));
                s.school = trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_SCHOOL));
                s.guardianName = trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_GUARDIAN_NAME));
                s.guardianPhone = trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_GUARDIAN_PHONE));
                s.address = trimOrNull(getStringOrNull(c, StudentEntry.COLUMN_ADDRESS));
            }
        } catch (Exception ignored) {}
        return s;
    }

    @NonNull
    private static List<LineItemRow> queryInvoiceItemsWithClassDates(@NonNull SQLiteDatabase db, @NonNull InvoiceData invoice) {
        List<LineItemRow> list = new ArrayList<>();
        try (Cursor c = db.query(
                InvoiceItemEntry.TABLE_NAME,
                null,
                InvoiceItemEntry.COLUMN_INVOICE_ID + " = ?",
                new String[]{invoice.id},
                null, null,
                InvoiceItemEntry.COLUMN_CREATED_AT + " ASC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    LineItemRow row = new LineItemRow();
                    row.id = getStringOrEmpty(c, InvoiceItemEntry.COLUMN_ID);
                    row.occurrenceId = getStringOrNull(c, InvoiceItemEntry.COLUMN_OCCURRENCE_ID);
                    row.description = nonEmptyOrDefault(getStringOrNull(c, InvoiceItemEntry.COLUMN_DESCRIPTION), "Tuition Fee");
                    row.billingModel = nonEmptyOrDefault(getStringOrNull(c, InvoiceItemEntry.COLUMN_BILLING_MODEL), "MONTHLY");
                    row.quantity = Math.max(1, getIntOrZero(c, InvoiceItemEntry.COLUMN_QUANTITY));
                    row.unitPriceMinor = getIntOrZero(c, InvoiceItemEntry.COLUMN_UNIT_PRICE_MINOR);
                    row.amountMinor = getIntOrZero(c, InvoiceItemEntry.COLUMN_AMOUNT_MINOR);
                    row.classDatesSummary = queryLinkedClassDates(db, row.id, row.occurrenceId);
                    list.add(row);
                }
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            LineItemRow fallback = new LineItemRow();
            fallback.id = invoice.id;
            fallback.description = "Tuition Fee — " + invoice.billingPeriod;
            fallback.billingModel = "MONTHLY";
            fallback.quantity = 1;
            fallback.unitPriceMinor = invoice.subtotalMinor;
            fallback.amountMinor = invoice.subtotalMinor;
            list.add(fallback);
        }
        return list;
    }

    @Nullable
    private static String queryLinkedClassDates(@NonNull SQLiteDatabase db, @NonNull String invoiceItemId, @Nullable String singleOccurrenceId) {
        List<String> dates = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());

        String sql = "SELECT o." + OccurrenceEntry.COLUMN_DATE +
                " FROM " + AttendanceEntry.TABLE_NAME + " a" +
                " INNER JOIN " + OccurrenceEntry.TABLE_NAME + " o ON a." + AttendanceEntry.COLUMN_OCCURRENCE_ID + " = o." + OccurrenceEntry.COLUMN_ID +
                " WHERE a." + AttendanceEntry.COLUMN_BILLED_INVOICE_ITEM_ID + " = ?" +
                " ORDER BY o." + OccurrenceEntry.COLUMN_DATE + " ASC";
        try (Cursor c = db.rawQuery(sql, new String[]{invoiceItemId})) {
            if (c != null) {
                while (c.moveToNext()) {
                    long dt = c.getLong(0);
                    if (dt > 0) dates.add(sdf.format(new Date(dt)));
                }
            }
        } catch (Exception ignored) {}

        if (dates.isEmpty() && !TextUtils.isEmpty(singleOccurrenceId)) {
            try (Cursor c = db.query(OccurrenceEntry.TABLE_NAME, new String[]{OccurrenceEntry.COLUMN_DATE},
                    OccurrenceEntry.COLUMN_ID + " = ?", new String[]{singleOccurrenceId}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    long dt = c.getLong(0);
                    if (dt > 0) dates.add(sdf.format(new Date(dt)));
                }
            } catch (Exception ignored) {}
        }

        if (dates.isEmpty()) return null;
        return "Classes: " + TextUtils.join(", ", dates);
    }

    @NonNull
    private static List<PaymentRow> queryPaymentsForInvoice(@NonNull SQLiteDatabase db, @NonNull String invoiceId) {
        List<PaymentRow> list = new ArrayList<>();

        String sql = "SELECT t." + TransactionEntry.COLUMN_ID + ", " +
                "t." + TransactionEntry.COLUMN_TXN_ID_DISPLAY + ", " +
                "t." + TransactionEntry.COLUMN_TIMESTAMP + ", " +
                "t." + TransactionEntry.COLUMN_METHOD + ", " +
                "t." + TransactionEntry.COLUMN_REFERENCE_NO + ", " +
                "t." + TransactionEntry.COLUMN_NOTES + ", " +
                "pa." + PaymentAllocationEntry.COLUMN_ALLOCATED_MINOR +
                " FROM " + PaymentAllocationEntry.TABLE_NAME + " pa" +
                " INNER JOIN " + TransactionEntry.TABLE_NAME + " t ON pa." + PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = t." + TransactionEntry.COLUMN_ID +
                " WHERE pa." + PaymentAllocationEntry.COLUMN_INVOICE_ID + " = ? AND t." + TransactionEntry.COLUMN_IS_REVERSED + " = 0" +
                " ORDER BY t." + TransactionEntry.COLUMN_TIMESTAMP + " ASC";

        try (Cursor c = db.rawQuery(sql, new String[]{invoiceId})) {
            if (c != null) {
                while (c.moveToNext()) {
                    PaymentRow row = new PaymentRow();
                    String id = c.getString(0);
                    String disp = c.getString(1);
                    row.displayId = !TextUtils.isEmpty(disp) ? disp : ("PAY-" + id.substring(0, Math.min(6, id.length())).toUpperCase(Locale.US));
                    row.timestamp = c.getLong(2);
                    row.method = c.getString(3);
                    row.referenceNo = c.getString(4);
                    row.notes = c.getString(5);
                    row.amountMinor = c.getInt(6);
                    list.add(row);
                }
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            try (Cursor c = db.query(
                    PaymentEntry.TABLE_NAME,
                    null,
                    PaymentEntry.COLUMN_INVOICE_ID + " = ?",
                    new String[]{invoiceId},
                    null, null,
                    PaymentEntry.COLUMN_PAYMENT_DATE + " ASC")) {
                if (c != null) {
                    int idx = 1;
                    while (c.moveToNext()) {
                        PaymentRow row = new PaymentRow();
                        String id = getStringOrEmpty(c, PaymentEntry.COLUMN_ID);
                        row.displayId = id.startsWith("PAY-") ? id : String.format(Locale.US, "PAY-%04d", idx++);
                        row.timestamp = getLongOrZero(c, PaymentEntry.COLUMN_PAYMENT_DATE);
                        row.method = getStringOrNull(c, PaymentEntry.COLUMN_METHOD);
                        row.amountMinor = getIntOrZero(c, PaymentEntry.COLUMN_AMOUNT);
                        list.add(row);
                    }
                }
            } catch (Exception ignored) {}
        }

        return list;
    }

    private static void computePreviousDues(@NonNull SQLiteDatabase db, @NonNull InvoiceData currentInvoice) {
        int prevDues = 0;
        List<String> prevRefs = new ArrayList<>();

        String selection = InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " +
                InvoiceEntry.COLUMN_STUDENT_ID + " = ? AND " +
                InvoiceEntry.COLUMN_ID + " != ? AND " +
                InvoiceEntry.COLUMN_IS_VOID + " = 0 AND " +
                InvoiceEntry.COLUMN_CREATED_AT + " < ?";

        String[] args = new String[]{
                currentInvoice.accountId,
                currentInvoice.studentId,
                currentInvoice.id,
                String.valueOf(currentInvoice.createdAt > 0 ? currentInvoice.createdAt : Long.MAX_VALUE)
        };

        try (Cursor c = db.query(InvoiceEntry.TABLE_NAME, null, selection, args, null, null, InvoiceEntry.COLUMN_CREATED_AT + " ASC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    int total = getIntOrZero(c, InvoiceEntry.COLUMN_TOTAL_AMOUNT);
                    int paid = getIntOrZero(c, InvoiceEntry.COLUMN_PAID_AMOUNT);
                    int due = Math.max(0, total - paid);
                    if (due > 0) {
                        prevDues += due;
                        String disp = getStringOrNull(c, InvoiceEntry.COLUMN_INVOICE_ID_DISPLAY);
                        if (!TextUtils.isEmpty(disp) && prevRefs.size() < 2) {
                            prevRefs.add(disp);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        currentInvoice.previousDuesMinor = prevDues;
        currentInvoice.previousDuesRefs = TextUtils.join(", ", prevRefs);
    }

    @Nullable
    private static ReceiptData loadReceiptData(@NonNull Context context,
                                               @NonNull String paymentOrTxnId,
                                               @Nullable String invoiceIdHint,
                                               @Nullable String studentIdHint) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            ReceiptData r = new ReceiptData();
            String resolvedInvoiceId = invoiceIdHint;
            r.studentId = studentIdHint != null ? studentIdHint : "";

            try (Cursor c = db.query(
                    TransactionEntry.TABLE_NAME,
                    null,
                    TransactionEntry.COLUMN_ID + " = ? OR " + TransactionEntry.COLUMN_TXN_ID_DISPLAY + " = ?",
                    new String[]{paymentOrTxnId, paymentOrTxnId},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String internalId = getStringOrEmpty(c, TransactionEntry.COLUMN_ID);
                    String disp = getStringOrNull(c, TransactionEntry.COLUMN_TXN_ID_DISPLAY);
                    r.receiptDisplayId = !TextUtils.isEmpty(disp) ? disp : ("PAY-" + internalId.substring(0, Math.min(6, internalId.length())).toUpperCase(Locale.US));
                    r.accountId = getStringOrEmpty(c, TransactionEntry.COLUMN_ACCOUNT_ID);
                    String dbStudentId = getStringOrEmpty(c, TransactionEntry.COLUMN_STUDENT_ID);
                    if (!TextUtils.isEmpty(dbStudentId)) r.studentId = dbStudentId;
                    r.type = getStringOrNull(c, TransactionEntry.COLUMN_TYPE);
                    r.amountMinor = getIntOrZero(c, TransactionEntry.COLUMN_AMOUNT_MINOR);
                    r.paymentTimestamp = getLongOrZero(c, TransactionEntry.COLUMN_TIMESTAMP);
                    r.method = getStringOrNull(c, TransactionEntry.COLUMN_METHOD);
                    r.referenceNo = getStringOrNull(c, TransactionEntry.COLUMN_REFERENCE_NO);
                    r.notes = getStringOrNull(c, TransactionEntry.COLUMN_NOTES);
                    r.isReversed = getIntOrZero(c, TransactionEntry.COLUMN_IS_REVERSED) == 1;

                    if (TextUtils.isEmpty(resolvedInvoiceId)) {
                        try (Cursor ac = db.query(PaymentAllocationEntry.TABLE_NAME,
                                new String[]{PaymentAllocationEntry.COLUMN_INVOICE_ID},
                                PaymentAllocationEntry.COLUMN_TRANSACTION_ID + " = ?",
                                new String[]{internalId}, null, null, null)) {
                            if (ac != null && ac.moveToFirst()) {
                                resolvedInvoiceId = ac.getString(0);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (TextUtils.isEmpty(r.receiptDisplayId)) {
                try (Cursor c = db.query(
                        PaymentEntry.TABLE_NAME,
                        null,
                        PaymentEntry.COLUMN_ID + " = ?",
                        new String[]{paymentOrTxnId},
                        null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        String id = getStringOrEmpty(c, PaymentEntry.COLUMN_ID);
                        r.receiptDisplayId = id.startsWith("PAY-") ? id : ("PAY-" + id.substring(0, Math.min(6, id.length())).toUpperCase(Locale.US));
                        r.accountId = getStringOrEmpty(c, PaymentEntry.COLUMN_ACCOUNT_ID);
                        resolvedInvoiceId = getStringOrEmpty(c, PaymentEntry.COLUMN_INVOICE_ID);
                        r.type = "PAYMENT";
                        r.amountMinor = getIntOrZero(c, PaymentEntry.COLUMN_AMOUNT);
                        r.paymentTimestamp = getLongOrZero(c, PaymentEntry.COLUMN_PAYMENT_DATE);
                        r.method = getStringOrNull(c, PaymentEntry.COLUMN_METHOD);
                    }
                } catch (Exception ignored) {}
            }

            if (TextUtils.isEmpty(r.receiptDisplayId)) return null;

            if (!TextUtils.isEmpty(resolvedInvoiceId)) {
                InvoiceData inv = querySingleInvoice(db, resolvedInvoiceId);
                if (inv != null) {
                    if (TextUtils.isEmpty(r.studentId)) r.studentId = inv.studentId;
                    if (TextUtils.isEmpty(r.accountId)) r.accountId = inv.accountId;
                    r.invoiceDisplayId = inv.invoiceDisplayId;
                    r.billingPeriod = inv.billingPeriod;
                    r.invoiceTotalMinor = inv.totalAmountMinor;
                    r.invoicePaidMinor = inv.paidAmountMinor;
                }
            }

            if (TextUtils.isEmpty(r.accountId)) {
                r.accountId = new SessionManager(context).getActiveAccountId();
            }
            r.tutor = queryTutorProfile(db, r.accountId);
            r.student = queryStudentProfile(db, r.studentId);
            return r;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    private static StatementData loadStudentStatementData(@NonNull Context context, @NonNull String studentId) {
        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            StatementData data = new StatementData();
            data.student = queryStudentProfile(db, studentId);
            String accountId = new SessionManager(context).getActiveAccountId();

            try (Cursor c = db.query(
                    InvoiceEntry.TABLE_NAME,
                    null,
                    InvoiceEntry.COLUMN_STUDENT_ID + " = ? AND " + InvoiceEntry.COLUMN_IS_VOID + " = 0",
                    new String[]{studentId},
                    null, null,
                    InvoiceEntry.COLUMN_CREATED_AT + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        InvoiceData inv = parseInvoiceCursor(c);
                        if (TextUtils.isEmpty(accountId)) accountId = inv.accountId;
                        data.totalBilledMinor += inv.totalAmountMinor;
                        data.totalPaidMinor += inv.paidAmountMinor;
                        data.invoices.add(inv);
                    }
                }
            }

            try (Cursor c = db.query(
                    TransactionEntry.TABLE_NAME,
                    null,
                    TransactionEntry.COLUMN_STUDENT_ID + " = ? AND " + TransactionEntry.COLUMN_IS_REVERSED + " = 0",
                    new String[]{studentId},
                    null, null,
                    TransactionEntry.COLUMN_TIMESTAMP + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        PaymentRow row = new PaymentRow();
                        String id = getStringOrEmpty(c, TransactionEntry.COLUMN_ID);
                        String disp = getStringOrNull(c, TransactionEntry.COLUMN_TXN_ID_DISPLAY);
                        row.displayId = !TextUtils.isEmpty(disp) ? disp : ("TXN-" + id.substring(0, Math.min(6, id.length())).toUpperCase(Locale.US));
                        row.timestamp = getLongOrZero(c, TransactionEntry.COLUMN_TIMESTAMP);
                        row.method = getStringOrNull(c, TransactionEntry.COLUMN_METHOD);
                        row.referenceNo = getStringOrNull(c, TransactionEntry.COLUMN_REFERENCE_NO);
                        row.notes = getStringOrNull(c, TransactionEntry.COLUMN_NOTES);
                        row.amountMinor = getIntOrZero(c, TransactionEntry.COLUMN_AMOUNT_MINOR);
                        data.transactions.add(row);
                    }
                }
            } catch (Exception ignored) {}

            data.tutor = queryTutorProfile(db, accountId);
            return data;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    @Nullable
    private static FinancialReportData loadFinancialReportData(@NonNull Context context, @Nullable String periodFilter) {
        String accountId = new SessionManager(context).getActiveAccountId();
        if (TextUtils.isEmpty(accountId)) return null;

        SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
        try {
            FinancialReportData data = new FinancialReportData();
            data.tutor = queryTutorProfile(db, accountId);
            data.periodLabel = !TextUtils.isEmpty(periodFilter) ? periodFilter : "All Periods";

            String sel = InvoiceEntry.COLUMN_ACCOUNT_ID + " = ? AND " + InvoiceEntry.COLUMN_IS_VOID + " = 0";
            String[] args;
            if (!TextUtils.isEmpty(periodFilter)) {
                sel += " AND " + InvoiceEntry.COLUMN_BILLING_PERIOD + " = ?";
                args = new String[]{accountId, periodFilter};
            } else {
                args = new String[]{accountId};
            }

            try (Cursor c = db.query(InvoiceEntry.TABLE_NAME, null, sel, args, null, null, InvoiceEntry.COLUMN_CREATED_AT + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        InvoiceData inv = parseInvoiceCursor(c);
                        inv.student = queryStudentProfile(db, inv.studentId);
                        data.totalBilledMinor += inv.totalAmountMinor;
                        data.totalCollectedMinor += inv.paidAmountMinor;
                        data.invoices.add(inv);
                    }
                }
            }
            return data;
        } finally {
            DatabaseManager.getInstance().closeDatabase();
        }
    }

    // =========================================================================================
    // 8. FORMATTING & GRAPHICS HELPERS
    // =========================================================================================

    private static void drawHorizontalRule(@NonNull Canvas canvas, float y, int color, float strokeWidth) {
        canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, createLinePaint(color, strokeWidth));
    }

    @NonNull
    private static Paint createLinePaint(int color, float strokeWidth) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(strokeWidth);
        return p;
    }

    private static void drawMetaPair(@NonNull Canvas canvas, float leftX, float rightX, float baselineY, @NonNull String label, @NonNull String value) {
        TextPaint lp = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        TextPaint vp = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_STRONG);
        vp.setTextAlign(Paint.Align.RIGHT);

        canvas.drawText(label, leftX, baselineY, lp);
        float maxW = Math.max(40f, (rightX - leftX) - lp.measureText(label) - 8f);
        canvas.drawText(ellipsize(value, vp, maxW), rightX, baselineY, vp);
    }

    @Nullable
    private static Bitmap loadLogoBitmap(@NonNull Context context, int sizePx, int tintColor) {
        try {
            Drawable drawable = AppCompatResources.getDrawable(context, R.drawable.ic_tutor_logo);
            if (drawable == null) return null;
            Drawable mutated = DrawableCompat.wrap(drawable.mutate());
            DrawableCompat.setTint(mutated, tintColor);

            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            mutated.setBounds(0, 0, sizePx, sizePx);
            mutated.draw(c);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static TextPaint createTextPaint(float sizePt, int style, int color) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setTextSize(sizePt);
        tp.setTypeface(Typeface.create(Typeface.SANS_SERIF, style));
        tp.setColor(color);
        return tp;
    }

    @NonNull
    private static StaticLayout buildStaticLayout(@NonNull String text, @NonNull TextPaint paint, int width) {
        int safeWidth = Math.max(40, width);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, safeWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(1.2f, 1.0f)
                    .setIncludePad(false)
                    .build();
        } else {
            return new StaticLayout(text, paint, safeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 1.2f, false);
        }
    }

    @NonNull
    private static String formatCurrency(int minorUnits) {
        return String.format(Locale.US, "৳ %,.2f", minorUnits / 100.0);
    }

    @NonNull
    private static String formatDate(long millis) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(millis));
    }

    @NonNull
    private static String formatDateShort(long millis) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(new Date(millis));
    }

    @NonNull
    private static String formatDateTimeShort(long millis) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    @NonNull
    private static String resolveExplicitStatusText(@Nullable String status, boolean isVoid) {
        if (isVoid) return "STATUS: VOID";
        if (Invoice.STATUS_PAID.equalsIgnoreCase(status)) return "STATUS: PAID";
        if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(status)) return "STATUS: PARTIALLY PAID";
        return "STATUS: UNPAID";
    }

    @NonNull
    private static String resolveShortStatusLabel(@Nullable String status, boolean isVoid) {
        if (isVoid) return "VOID";
        if (Invoice.STATUS_PAID.equalsIgnoreCase(status)) return "PAID";
        if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(status)) return "PARTIAL";
        return "UNPAID";
    }

    private static int resolveStatusColor(@Nullable String status, boolean isVoid) {
        if (isVoid) return COLOR_STATUS_VOID;
        if (Invoice.STATUS_PAID.equalsIgnoreCase(status)) return COLOR_STATUS_PAID;
        if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(status)) return COLOR_STATUS_PARTIAL;
        return COLOR_STATUS_UNPAID;
    }

    @NonNull
    private static String ellipsize(@Nullable String text, @NonNull TextPaint paint, float maxWidth) {
        if (text == null) return "";
        return TextUtils.ellipsize(text, paint, Math.max(20f, maxWidth), TextUtils.TruncateAt.END).toString();
    }

    @NonNull
    private static File getOutputPdfFile(@NonNull Context context, @NonNull String fileName) {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "TutorSync_PDFs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }

    @NonNull
    private static String sanitizeFilename(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Document";
        return raw.trim().replaceAll("[^a-zA-Z0-9\\-_]", "_");
    }

    @NonNull
    private static String buildRefAndNote(@Nullable String ref, @Nullable String notes) {
        String r = trimOrNull(ref);
        String n = trimOrNull(notes);
        if (r != null && n != null) return r + " • " + n;
        if (r != null) return "Ref: " + r;
        if (n != null) return n;
        return "-";
    }

    @Nullable
    private static String trimOrNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @NonNull
    private static String nonEmptyOrDefault(@Nullable String s, @NonNull String def) {
        String t = trimOrNull(s);
        return t != null ? t : def;
    }

    @NonNull
    private static List<String> nonEmptyList(String... items) {
        List<String> out = new ArrayList<>();
        if (items != null) {
            for (String item : items) {
                if (!TextUtils.isEmpty(item)) out.add(item);
            }
        }
        return out;
    }

    @Nullable
    private static String getStringOrNull(@NonNull Cursor c, @NonNull String col) {
        int idx = c.getColumnIndex(col);
        return (idx >= 0 && !c.isNull(idx)) ? c.getString(idx) : null;
    }

    @NonNull
    private static String getStringOrEmpty(@NonNull Cursor c, @NonNull String col) {
        String s = getStringOrNull(c, col);
        return s != null ? s : "";
    }

    private static int getIntOrZero(@NonNull Cursor c, @NonNull String col) {
        int idx = c.getColumnIndex(col);
        return (idx >= 0 && !c.isNull(idx)) ? c.getInt(idx) : 0;
    }

    private static long getLongOrZero(@NonNull Cursor c, @NonNull String col) {
        int idx = c.getColumnIndex(col);
        return (idx >= 0 && !c.isNull(idx)) ? c.getLong(idx) : 0L;
    }

    // =========================================================================================
    // 9. INTERNAL DATA HOLDERS
    // =========================================================================================

    private static final class PageHolder {
        final PdfDocument.Page page;
        final Canvas canvas;

        PageHolder(PdfDocument.Page page, Canvas canvas) {
            this.page = page;
            this.canvas = canvas;
        }
    }

    private static final class TutorProfile {
        String fullName;
        String phone;
        String email;
        String address;
    }

    private static final class StudentProfile {
        String id;
        String studentIdDisplay;
        String name = "Unknown Student";
        String grade;
        String school;
        String guardianName;
        String guardianPhone;
        String address;

        @NonNull
        String getFormattedName() {
            if (!TextUtils.isEmpty(studentIdDisplay)) {
                return name + " · " + studentIdDisplay;
            }
            return name;
        }

        @Nullable
        String gradeOrSchool() {
            if (!TextUtils.isEmpty(grade) && !TextUtils.isEmpty(school)) {
                return grade + " • " + school;
            }
            if (!TextUtils.isEmpty(grade)) return grade;
            if (!TextUtils.isEmpty(school)) return school;
            return null;
        }

        @Nullable
        String guardianSummary() {
            if (!TextUtils.isEmpty(guardianName) && !TextUtils.isEmpty(guardianPhone)) {
                return "Guardian: " + guardianName + " (" + guardianPhone + ")";
            }
            if (!TextUtils.isEmpty(guardianName)) return "Guardian: " + guardianName;
            if (!TextUtils.isEmpty(guardianPhone)) return "Guardian: " + guardianPhone;
            return null;
        }
    }

    private static final class LineItemRow {
        String id;
        String occurrenceId;
        String description;
        String billingModel;
        int quantity;
        int unitPriceMinor;
        int amountMinor;
        String classDatesSummary;
    }

    private static final class PaymentRow {
        String displayId;
        long timestamp;
        String method;
        String referenceNo;
        String notes;
        int amountMinor;
    }

    private static final class InvoiceData {
        String id;
        String accountId;
        String studentId;
        String invoiceDisplayId;
        String billingPeriod;
        long periodStart;
        long periodEnd;
        long issueDate;
        long dueDate;
        long createdAt;
        int subtotalMinor;
        int discountMinor;
        int totalAmountMinor;
        int paidAmountMinor;
        int previousDuesMinor;
        String previousDuesRefs;
        String status;
        String notes;
        boolean isVoid;

        TutorProfile tutor = new TutorProfile();
        StudentProfile student = new StudentProfile();
        List<LineItemRow> items = new ArrayList<>();
        List<PaymentRow> payments = new ArrayList<>();
    }

    private static final class ReceiptData {
        String receiptDisplayId;
        String accountId;
        String studentId;
        String type;
        int amountMinor;
        long paymentTimestamp;
        String method;
        String referenceNo;
        String notes;
        boolean isReversed;

        String invoiceDisplayId;
        String billingPeriod;
        int invoiceTotalMinor;
        int invoicePaidMinor;

        TutorProfile tutor = new TutorProfile();
        StudentProfile student = new StudentProfile();
    }

    private static final class StatementData {
        TutorProfile tutor = new TutorProfile();
        StudentProfile student = new StudentProfile();
        int totalBilledMinor;
        int totalPaidMinor;
        List<InvoiceData> invoices = new ArrayList<>();
        List<PaymentRow> transactions = new ArrayList<>();
    }

    private static final class FinancialReportData {
        TutorProfile tutor = new TutorProfile();
        String periodLabel;
        int totalBilledMinor;
        int totalCollectedMinor;
        List<InvoiceData> invoices = new ArrayList<>();
    }
}