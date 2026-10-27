package com.abdussamadcodes.tutorsync.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
    private static final float MARGIN_H = 36f;
    private static final float MARGIN_TOP = 36f;
    private static final float FOOTER_RESERVE = 64f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2f);

    // Unified Brand & Print-Safe Color Palette
    private static final int COLOR_BRAND_DARK = Color.parseColor("#0F172A");     // Slate 900
    private static final int COLOR_BRAND_PRIMARY = Color.parseColor("#1E3A8A");  // Deep Royal Blue
    private static final int COLOR_BRAND_ACCENT = Color.parseColor("#2563EB");   // Royal Blue 600
    private static final int COLOR_BRAND_SOFT = Color.parseColor("#EFF6FF");     // Blue 50
    private static final int COLOR_SURFACE_CARD = Color.parseColor("#F8FAFC");   // Slate 50
    private static final int COLOR_SURFACE_ZEBRA = Color.parseColor("#F8FAFC");  // Alternate Row
    private static final int COLOR_BORDER = Color.parseColor("#E2E8F0");         // Slate 200
    private static final int COLOR_BORDER_STRONG = Color.parseColor("#CBD5E1");  // Slate 300

    private static final int COLOR_INK_PRIMARY = Color.parseColor("#0F172A");    // Main text
    private static final int COLOR_INK_SECONDARY = Color.parseColor("#475569");  // Subtitles
    private static final int COLOR_INK_MUTED = Color.parseColor("#64748B");      // Captions/Labels

    // Status Colors
    private static final int COLOR_PAID_BG = Color.parseColor("#DCFCE7");
    private static final int COLOR_PAID_BORDER = Color.parseColor("#86EFAC");
    private static final int COLOR_PAID_INK = Color.parseColor("#15803D");

    private static final int COLOR_PARTIAL_BG = Color.parseColor("#FEF3C7");
    private static final int COLOR_PARTIAL_BORDER = Color.parseColor("#FCD34D");
    private static final int COLOR_PARTIAL_INK = Color.parseColor("#B45309");

    private static final int COLOR_UNPAID_BG = Color.parseColor("#FEE2E2");
    private static final int COLOR_UNPAID_BORDER = Color.parseColor("#FCA5A5");
    private static final int COLOR_UNPAID_INK = Color.parseColor("#B91C1C");

    private static final int COLOR_VOID_BG = Color.parseColor("#F1F5F9");
    private static final int COLOR_VOID_BORDER = Color.parseColor("#CBD5E1");
    private static final int COLOR_VOID_INK = Color.parseColor("#475569");

    private FinancialPdfGenerator() {}

    // =========================================================================================
    // 1. PUBLIC ENTRY POINTS (Flexible signatures for all Activities)
    // =========================================================================================

    /**
     * Generates a redesigned, print-ready Invoice PDF.
     * Supports any call signature, e.g.:
     * - generateInvoicePdf(context, invoiceId)
     * - generateInvoicePdf(context, "Tutor", student, invoice, items, transactions)
     */
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

    /**
     * Generates a redesigned Payment Receipt PDF.
     * Supports any call signature, e.g.:
     * - generatePaymentReceiptPdf(context, "Tutor", currentStudent, txn)
     * - generatePaymentReceiptPdf(context, txnId)
     */
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

    /**
     * Generates a comprehensive Student Financial Statement & Transaction Ledger PDF.
     */
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

    /**
     * Generates an Account-level Financial Summary Report PDF across all students.
     */
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

    /**
     * Saves a generated PDF to the public Downloads/TutorSync directory and returns its Uri.
     */
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

    /**
     * Shares a generated PDF file safely using FileProvider (with MediaStore fallback).
     */
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

    /**
     * Opens a generated PDF file in the device's default PDF viewer.
     */
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
    // 2. INVOICE PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderInvoiceDocument(@NonNull Context context, @NonNull InvoiceData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoWatermark = loadLogoBitmap(context, 240, COLOR_BRAND_PRIMARY);
        Bitmap logoHeader = loadLogoBitmap(context, 48, COLOR_BRAND_ACCENT);

        try {
            PageHolder current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
            pages.add(current);
            Canvas canvas = current.canvas;

            // 1. Top Header (Tutor Profile + Document Title & Status Badge)
            String statusLabel = resolveStatusLabel(data.status, data.isVoid);
            float y = drawDocumentHeader(
                    canvas,
                    logoHeader,
                    data.tutor,
                    "INVOICE",
                    data.invoiceDisplayId,
                    statusLabel,
                    data.status,
                    data.isVoid
            );

            y += 14f;

            // 2. Student Information Card (Left) & Invoice Metadata Card (Right)
            y = drawStudentAndInvoiceMetaCards(canvas, y, data);
            y += 14f;

            // 3. Prominent Financial Summary Strip (Total Amount | Amount Paid | Amount Due [+ Previous Dues])
            y = drawProminentInvoiceSummaryStrip(canvas, y, data);
            y += 18f;

            // 4. Section Title: Breakdown of Billable Classes & Fees
            y = drawSectionHeader(canvas, y, "BILLABLE CLASSES & FEE BREAKDOWN");
            y += 6f;

            // Table Header
            y = drawInvoiceTableHeader(canvas, y);

            // Table Rows (with automatic multi-page flow)
            for (int i = 0; i < data.items.size(); i++) {
                LineItemRow item = data.items.get(i);
                float rowHeight = measureInvoiceItemRowHeight(item);

                if (y + rowHeight > PAGE_HEIGHT - FOOTER_RESERVE) {
                    current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                    pages.add(current);
                    canvas = current.canvas;
                    y = drawContinuationHeader(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                    y += 12f;
                    y = drawInvoiceTableHeader(canvas, y);
                }

                y = drawInvoiceItemRow(canvas, y, i + 1, item, i % 2 == 1);
            }

            // Bottom border of items table
            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setColor(COLOR_BORDER_STRONG);
            linePaint.setStrokeWidth(1f);
            canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, linePaint);
            y += 14f;

            // 5. Totals Breakdown (Subtotal, Discount, Previous Dues, Payments, Remaining Balance) + Notes/Status Seal
            float totalsNeededHeight = 148f;
            if (y + totalsNeededHeight > PAGE_HEIGHT - FOOTER_RESERVE) {
                current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                pages.add(current);
                canvas = current.canvas;
                y = drawContinuationHeader(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                y += 16f;
            }
            y = drawTotalsAndNotesSection(canvas, y, data);
            y += 18f;

            // 6. Payments Breakdown Table (Payments logged against this invoice)
            float paymentsHeaderHeight = data.payments.isEmpty() ? 52f : 72f;
            if (y + paymentsHeaderHeight > PAGE_HEIGHT - FOOTER_RESERVE) {
                current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                pages.add(current);
                canvas = current.canvas;
                y = drawContinuationHeader(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                y += 16f;
            }

            y = drawSectionHeader(canvas, y, "PAYMENTS & ALLOCATIONS BREAKDOWN");
            y += 6f;

            if (data.payments.isEmpty()) {
                y = drawEmptyPaymentsBox(canvas, y, "No payments have been recorded against this invoice yet.");
            } else {
                y = drawPaymentsTableHeader(canvas, y);
                for (int i = 0; i < data.payments.size(); i++) {
                    PaymentRow p = data.payments.get(i);
                    float rowH = 24f;
                    if (y + rowH > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationHeader(canvas, "INVOICE " + data.invoiceDisplayId, data.student.getFormattedName());
                        y += 12f;
                        y = drawPaymentsTableHeader(canvas, y);
                    }
                    y = drawPaymentTableRow(canvas, y, p, i % 2 == 1);
                }
                canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, linePaint);
            }

            // 7. Signature & Footer on all pages
            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                PageHolder holder = pages.get(i);
                boolean isLastPage = (i == totalPages - 1);
                drawPageFooter(holder.canvas, data.tutor, i + 1, totalPages, isLastPage);
                pdfDocument.finishPage(holder.page);
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
    // 3. PAYMENT RECEIPT PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderReceiptDocument(@NonNull Context context, @NonNull ReceiptData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoWatermark = loadLogoBitmap(context, 240, COLOR_BRAND_PRIMARY);
        Bitmap logoHeader = loadLogoBitmap(context, 48, COLOR_BRAND_ACCENT);

        try {
            PageHolder current = startNewPage(pdfDocument, 1, logoWatermark);
            pages.add(current);
            Canvas canvas = current.canvas;

            String badgeText = data.isReversed ? "REVERSED" : "OFFICIAL RECEIPT";
            String statusCode = data.isReversed ? "VOID" : Invoice.STATUS_PAID;

            float y = drawDocumentHeader(
                    canvas,
                    logoHeader,
                    data.tutor,
                    "PAYMENT RECEIPT",
                    data.receiptDisplayId,
                    badgeText,
                    statusCode,
                    data.isReversed
            );
            y += 16f;

            // Prominent Amount Received Banner
            RectF heroRect = new RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 66f);
            Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            cardPaint.setColor(data.isReversed ? COLOR_VOID_BG : COLOR_PAID_BG);
            canvas.drawRoundRect(heroRect, 8f, 8f, cardPaint);

            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.2f);
            borderPaint.setColor(data.isReversed ? COLOR_VOID_BORDER : COLOR_PAID_BORDER);
            canvas.drawRoundRect(heroRect, 8f, 8f, borderPaint);

            String heroLabel = "DISCOUNT".equalsIgnoreCase(data.type) ? "DISCOUNT / WAIVER APPLIED"
                    : ("REFUND".equalsIgnoreCase(data.type) ? "AMOUNT REFUNDED" : "AMOUNT RECEIVED");
            TextPaint labelPaint = createTextPaint(9f, Typeface.BOLD, data.isReversed ? COLOR_VOID_INK : COLOR_PAID_INK);
            canvas.drawText(heroLabel, MARGIN_H + 16f, y + 22f, labelPaint);

            TextPaint amountPaint = createTextPaint(22f, Typeface.BOLD, data.isReversed ? COLOR_VOID_INK : COLOR_PAID_INK);
            canvas.drawText(formatCurrency(data.amountMinor), MARGIN_H + 16f, y + 50f, amountPaint);

            TextPaint rightMetaPaint = createTextPaint(9.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            rightMetaPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Date: " + formatDateTime(data.paymentTimestamp), PAGE_WIDTH - MARGIN_H - 16f, y + 26f, rightMetaPaint);
            canvas.drawText("Method: " + nonEmptyOrDefault(data.method, "Cash"), PAGE_WIDTH - MARGIN_H - 16f, y + 44f, rightMetaPaint);

            y += 82f;

            // Student & Payment Details Card
            RectF detailsCard = new RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 124f);
            Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            surfacePaint.setColor(COLOR_SURFACE_CARD);
            canvas.drawRoundRect(detailsCard, 8f, 8f, surfacePaint);
            borderPaint.setColor(COLOR_BORDER);
            borderPaint.setStrokeWidth(1f);
            canvas.drawRoundRect(detailsCard, 8f, 8f, borderPaint);

            float leftX = MARGIN_H + 16f;
            float rightX = MARGIN_H + (CONTENT_WIDTH / 2f) + 12f;
            float rowY = y + 22f;

            TextPaint secHeader = createTextPaint(8.5f, Typeface.BOLD, COLOR_BRAND_ACCENT);
            canvas.drawText("STUDENT INFORMATION", leftX, rowY, secHeader);
            canvas.drawText("TRANSACTION SUMMARY", rightX, rowY, secHeader);

            rowY += 18f;
            TextPaint boldInk = createTextPaint(11f, Typeface.BOLD, COLOR_INK_PRIMARY);
            canvas.drawText(ellipsize(data.student.getFormattedName(), boldInk, (CONTENT_WIDTH / 2f) - 24f), leftX, rowY, boldInk);
            drawKeyValueRow(canvas, rightX, PAGE_WIDTH - MARGIN_H - 16f, rowY, "Receipt No:", data.receiptDisplayId);

            rowY += 16f;
            TextPaint subInk = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            if (!TextUtils.isEmpty(data.student.gradeOrSchool())) {
                canvas.drawText(ellipsize(data.student.gradeOrSchool(), subInk, (CONTENT_WIDTH / 2f) - 24f), leftX, rowY, subInk);
                rowY += 14f;
            }
            if (!TextUtils.isEmpty(data.student.guardianSummary())) {
                canvas.drawText(ellipsize(data.student.guardianSummary(), subInk, (CONTENT_WIDTH / 2f) - 24f), leftX, rowY, subInk);
                rowY += 14f;
            }
            if (!TextUtils.isEmpty(data.student.address)) {
                canvas.drawText(ellipsize(data.student.address, subInk, (CONTENT_WIDTH / 2f) - 28f), leftX, rowY, subInk);
            }

            float rightRowY = y + 56f;
            if (!TextUtils.isEmpty(data.type)) {
                drawKeyValueRow(canvas, rightX, PAGE_WIDTH - MARGIN_H - 16f, rightRowY, "Transaction Type:", data.type);
                rightRowY += 16f;
            }
            if (!TextUtils.isEmpty(data.referenceNo)) {
                drawKeyValueRow(canvas, rightX, PAGE_WIDTH - MARGIN_H - 16f, rightRowY, "Reference:", data.referenceNo);
                rightRowY += 16f;
            }
            if (!TextUtils.isEmpty(data.invoiceDisplayId)) {
                drawKeyValueRow(canvas, rightX, PAGE_WIDTH - MARGIN_H - 16f, rightRowY, "Applied to Invoice:", data.invoiceDisplayId);
                rightRowY += 16f;
            }
            if (!TextUtils.isEmpty(data.billingPeriod)) {
                drawKeyValueRow(canvas, rightX, PAGE_WIDTH - MARGIN_H - 16f, rightRowY, "Billing Period:", data.billingPeriod);
            }

            y += 142f;

            // Linked Invoice Balance Summary (if linked to an invoice)
            if (data.invoiceTotalMinor > 0) {
                y = drawSectionHeader(canvas, y, "INVOICE SETTLEMENT SUMMARY (" + nonEmptyOrDefault(data.invoiceDisplayId, "Linked Invoice") + ")");
                y += 8f;

                RectF invBox = new RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 76f);
                Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                whitePaint.setColor(Color.WHITE);
                canvas.drawRoundRect(invBox, 8f, 8f, whitePaint);
                canvas.drawRoundRect(invBox, 8f, 8f, borderPaint);

                float colW = CONTENT_WIDTH / 4f;
                int remainingOnInvoice = Math.max(0, data.invoiceTotalMinor - data.invoicePaidMinor);
                drawMiniMetric(canvas, MARGIN_H, y + 16f, colW, "INVOICE TOTAL", formatCurrency(data.invoiceTotalMinor), COLOR_INK_PRIMARY);
                drawMiniMetric(canvas, MARGIN_H + colW, y + 16f, colW, "THIS AMOUNT", formatCurrency(data.amountMinor), COLOR_PAID_INK);
                drawMiniMetric(canvas, MARGIN_H + colW * 2, y + 16f, colW, "TOTAL PAID TO DATE", formatCurrency(data.invoicePaidMinor), COLOR_BRAND_ACCENT);
                drawMiniMetric(canvas, MARGIN_H + colW * 3, y + 16f, colW, "REMAINING DUE", formatCurrency(remainingOnInvoice),
                        remainingOnInvoice > 0 ? COLOR_UNPAID_INK : COLOR_PAID_INK);

                y += 94f;
            }

            if (!TextUtils.isEmpty(data.notes)) {
                y = drawSectionHeader(canvas, y, "TRANSACTION NOTES");
                y += 6f;
                RectF noteBox = new RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 44f);
                canvas.drawRoundRect(noteBox, 6f, 6f, surfacePaint);
                canvas.drawRoundRect(noteBox, 6f, 6f, borderPaint);
                TextPaint notePaint = createTextPaint(9.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
                canvas.drawText(ellipsize(data.notes, notePaint, CONTENT_WIDTH - 24f), MARGIN_H + 12f, y + 26f, notePaint);
            }

            drawPageFooter(canvas, data.tutor, 1, 1, true);
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
    // 4. STUDENT STATEMENT / LEDGER PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderStudentStatementDocument(@NonNull Context context, @NonNull StatementData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoWatermark = loadLogoBitmap(context, 240, COLOR_BRAND_PRIMARY);
        Bitmap logoHeader = loadLogoBitmap(context, 48, COLOR_BRAND_ACCENT);

        try {
            PageHolder current = startNewPage(pdfDocument, 1, logoWatermark);
            pages.add(current);
            Canvas canvas = current.canvas;

            int totalDue = Math.max(0, data.totalBilledMinor - data.totalPaidMinor);
            String status = totalDue == 0 ? Invoice.STATUS_PAID : (data.totalPaidMinor > 0 ? Invoice.STATUS_PARTIAL : Invoice.STATUS_UNPAID);
            String badgeLabel = totalDue == 0 ? "SETTLED" : "BALANCE DUE";

            float y = drawDocumentHeader(
                    canvas,
                    logoHeader,
                    data.tutor,
                    "STUDENT STATEMENT",
                    data.student.studentIdDisplay != null ? data.student.studentIdDisplay : "LEDGER",
                    badgeLabel,
                    status,
                    false
            );
            y += 14f;

            // Student Info Bar
            RectF studentBox = new RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 60f);
            Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            surfacePaint.setColor(COLOR_SURFACE_CARD);
            canvas.drawRoundRect(studentBox, 8f, 8f, surfacePaint);
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(COLOR_BORDER);
            borderPaint.setStrokeWidth(1f);
            canvas.drawRoundRect(studentBox, 8f, 8f, borderPaint);

            TextPaint namePaint = createTextPaint(12f, Typeface.BOLD, COLOR_INK_PRIMARY);
            canvas.drawText(data.student.getFormattedName(), MARGIN_H + 14f, y + 24f, namePaint);

            TextPaint subPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            String metaLine = TextUtils.join("  •  ", nonEmptyList(data.student.gradeOrSchool(), data.student.guardianSummary(), data.student.address));
            if (!TextUtils.isEmpty(metaLine)) {
                canvas.drawText(ellipsize(metaLine, subPaint, CONTENT_WIDTH - 28f), MARGIN_H + 14f, y + 44f, subPaint);
            }
            y += 74f;

            // 3-Box Summary Strip
            y = drawThreeBoxSummaryStrip(
                    canvas,
                    y,
                    "TOTAL INVOICED", formatCurrency(data.totalBilledMinor),
                    "TOTAL PAID", formatCurrency(data.totalPaidMinor),
                    "OUTSTANDING BALANCE", formatCurrency(totalDue),
                    totalDue > 0
            );
            y += 18f;

            y = drawSectionHeader(canvas, y, "INVOICE & BILLING HISTORY");
            y += 6f;

            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setColor(COLOR_BORDER);
            linePaint.setStrokeWidth(0.8f);

            if (data.invoices.isEmpty()) {
                y = drawEmptyPaymentsBox(canvas, y, "No invoices recorded for this student yet.");
            } else {
                y = drawStatementTableHeader(canvas, y);
                for (int i = 0; i < data.invoices.size(); i++) {
                    InvoiceData inv = data.invoices.get(i);
                    if (y + 26f > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationHeader(canvas, "STUDENT STATEMENT", data.student.getFormattedName());
                        y += 12f;
                        y = drawStatementTableHeader(canvas, y);
                    }
                    y = drawStatementInvoiceRow(canvas, y, inv, i % 2 == 1);
                }
                canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, linePaint);
            }

            y += 16f;
            if (y + 60f > PAGE_HEIGHT - FOOTER_RESERVE) {
                current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                pages.add(current);
                canvas = current.canvas;
                y = drawContinuationHeader(canvas, "STUDENT STATEMENT", data.student.getFormattedName());
                y += 12f;
            }

            y = drawSectionHeader(canvas, y, "PAYMENTS & TRANSACTIONS HISTORY");
            y += 6f;
            if (data.transactions.isEmpty()) {
                y = drawEmptyPaymentsBox(canvas, y, "No payment transactions recorded for this student yet.");
            } else {
                y = drawPaymentsTableHeader(canvas, y);
                for (int i = 0; i < data.transactions.size(); i++) {
                    PaymentRow p = data.transactions.get(i);
                    if (y + 24f > PAGE_HEIGHT - FOOTER_RESERVE) {
                        current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                        pages.add(current);
                        canvas = current.canvas;
                        y = drawContinuationHeader(canvas, "STUDENT STATEMENT", data.student.getFormattedName());
                        y += 12f;
                        y = drawPaymentsTableHeader(canvas, y);
                    }
                    y = drawPaymentTableRow(canvas, y, p, i % 2 == 1);
                }
                canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, linePaint);
            }

            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                drawPageFooter(pages.get(i).canvas, data.tutor, i + 1, totalPages, i == totalPages - 1);
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
    // 5. ACCOUNT FINANCIAL SUMMARY REPORT PDF RENDERER
    // =========================================================================================

    @Nullable
    private static File renderFinancialReportDocument(@NonNull Context context, @NonNull FinancialReportData data) {
        PdfDocument pdfDocument = new PdfDocument();
        List<PageHolder> pages = new ArrayList<>();
        Bitmap logoWatermark = loadLogoBitmap(context, 240, COLOR_BRAND_PRIMARY);
        Bitmap logoHeader = loadLogoBitmap(context, 48, COLOR_BRAND_ACCENT);

        try {
            PageHolder current = startNewPage(pdfDocument, 1, logoWatermark);
            pages.add(current);
            Canvas canvas = current.canvas;

            int totalDue = Math.max(0, data.totalBilledMinor - data.totalCollectedMinor);
            float y = drawDocumentHeader(
                    canvas,
                    logoHeader,
                    data.tutor,
                    "FINANCIAL REPORT",
                    data.periodLabel,
                    "SUMMARY",
                    totalDue == 0 ? Invoice.STATUS_PAID : Invoice.STATUS_PARTIAL,
                    false
            );
            y += 16f;

            y = drawThreeBoxSummaryStrip(
                    canvas,
                    y,
                    "TOTAL INVOICED", formatCurrency(data.totalBilledMinor),
                    "PAYMENTS COLLECTED", formatCurrency(data.totalCollectedMinor),
                    "OUTSTANDING DUES", formatCurrency(totalDue),
                    totalDue > 0
            );
            y += 18f;

            y = drawSectionHeader(canvas, y, "INVOICE BREAKDOWN (" + data.invoices.size() + " INVOICES)");
            y += 6f;
            y = drawReportTableHeader(canvas, y);

            for (int i = 0; i < data.invoices.size(); i++) {
                InvoiceData inv = data.invoices.get(i);
                if (y + 26f > PAGE_HEIGHT - FOOTER_RESERVE) {
                    current = startNewPage(pdfDocument, pages.size() + 1, logoWatermark);
                    pages.add(current);
                    canvas = current.canvas;
                    y = drawContinuationHeader(canvas, "FINANCIAL REPORT", data.periodLabel);
                    y += 12f;
                    y = drawReportTableHeader(canvas, y);
                }
                y = drawReportInvoiceRow(canvas, y, inv, i % 2 == 1);
            }

            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                drawPageFooter(pages.get(i).canvas, data.tutor, i + 1, totalPages, i == totalPages - 1);
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
    // 6. SHARED DRAWING COMPONENTS (Header, Watermark, Cards, Tables, Totals, Footer)
    // =========================================================================================

    private static PageHolder startNewPage(@NonNull PdfDocument doc, int pageNumber, @Nullable Bitmap watermarkBitmap) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        canvas.drawColor(Color.WHITE);

        // Top decorative accent band
        Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bandPaint.setColor(COLOR_BRAND_DARK);
        canvas.drawRect(0, 0, PAGE_WIDTH, 6f, bandPaint);
        bandPaint.setColor(COLOR_BRAND_ACCENT);
        canvas.drawRect(PAGE_WIDTH * 0.68f, 0, PAGE_WIDTH, 6f, bandPaint);

        // Subtle Center TutorSync Logo Watermark
        drawSubtleWatermark(canvas, watermarkBitmap);

        return new PageHolder(page, canvas);
    }

    private static void drawSubtleWatermark(@NonNull Canvas canvas, @Nullable Bitmap watermarkBitmap) {
        canvas.save();
        float centerX = PAGE_WIDTH / 2f;
        float centerY = PAGE_HEIGHT / 2f + 20f;
        canvas.rotate(-18f, centerX, centerY);

        if (watermarkBitmap != null) {
            Paint wmPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            wmPaint.setAlpha(11); // ~4.3% opacity so it never interferes with readability
            float size = 210f;
            RectF dst = new RectF(centerX - size / 2f, centerY - size / 2f - 15f, centerX + size / 2f, centerY + size / 2f - 15f);
            canvas.drawBitmap(watermarkBitmap, null, dst, wmPaint);
        }

        TextPaint wmText = createTextPaint(34f, Typeface.BOLD, COLOR_BRAND_PRIMARY);
        wmText.setAlpha(11);
        wmText.setTextAlign(Paint.Align.CENTER);
        wmText.setLetterSpacing(0.12f);
        canvas.drawText("TUTORSYNC", centerX, centerY + 115f, wmText);
        canvas.restore();
    }

    private static float drawDocumentHeader(@NonNull Canvas canvas,
                                            @Nullable Bitmap logoHeader,
                                            @NonNull TutorProfile tutor,
                                            @NonNull String documentTypeTitle,
                                            @NonNull String documentRefId,
                                            @NonNull String statusLabel,
                                            @NonNull String statusCode,
                                            boolean isVoid) {
        float y = MARGIN_TOP - 8f;
        float leftX = MARGIN_H;

        if (logoHeader != null) {
            RectF iconBg = new RectF(leftX, y, leftX + 34f, y + 34f);
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(COLOR_BRAND_SOFT);
            canvas.drawRoundRect(iconBg, 8f, 8f, bgPaint);

            RectF iconDst = new RectF(leftX + 5f, y + 5f, leftX + 29f, y + 29f);
            Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(logoHeader, null, iconDst, bmpPaint);
            leftX += 42f;
        }

        String displayTutorName = !TextUtils.isEmpty(tutor.fullName) ? tutor.fullName : "TutorSync Educator";
        TextPaint tutorNamePaint = createTextPaint(15f, Typeface.BOLD, COLOR_INK_PRIMARY);
        canvas.drawText(displayTutorName, leftX, y + 15f, tutorNamePaint);

        TextPaint roleBadgePaint = createTextPaint(8.5f, Typeface.BOLD, COLOR_BRAND_ACCENT);
        canvas.drawText("TUTORSYNC EDUCATOR PROFILE", leftX, y + 27f, roleBadgePaint);

        float contactY = y + 42f;
        TextPaint contactPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);

        // Gracefully show teacher's mobile number, email, and address when available in profile
        if (!TextUtils.isEmpty(tutor.phone)) {
            canvas.drawText("Mobile: " + tutor.phone, MARGIN_H, contactY, contactPaint);
            contactY += 13f;
        }
        if (!TextUtils.isEmpty(tutor.email)) {
            canvas.drawText("Email: " + tutor.email, MARGIN_H, contactY, contactPaint);
            contactY += 13f;
        }
        if (!TextUtils.isEmpty(tutor.address)) {
            canvas.drawText(ellipsize("Address: " + tutor.address, contactPaint, CONTENT_WIDTH * 0.54f), MARGIN_H, contactY, contactPaint);
            contactY += 13f;
        }

        // Right Column: Document Type Title, Reference Number & Status Pill
        float rightEdge = PAGE_WIDTH - MARGIN_H;
        TextPaint docTitlePaint = createTextPaint(20f, Typeface.BOLD, COLOR_BRAND_DARK);
        docTitlePaint.setTextAlign(Paint.Align.RIGHT);
        docTitlePaint.setLetterSpacing(0.04f);
        canvas.drawText(documentTypeTitle, rightEdge, y + 18f, docTitlePaint);

        TextPaint refPaint = createTextPaint(11f, Typeface.BOLD, COLOR_BRAND_ACCENT);
        refPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(documentRefId, rightEdge, y + 34f, refPaint);

        drawStatusBadge(canvas, rightEdge, y + 43f, statusLabel, statusCode, isVoid);

        float headerBottom = Math.max(contactY + 2f, y + 72f);

        Paint divPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        divPaint.setColor(COLOR_BORDER);
        divPaint.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_H, headerBottom, PAGE_WIDTH - MARGIN_H, headerBottom, divPaint);

        return headerBottom;
    }

    private static void drawStatusBadge(@NonNull Canvas canvas,
                                        float rightEdge,
                                        float topY,
                                        @NonNull String statusLabel,
                                        @NonNull String statusCode,
                                        boolean isVoid) {
        int bgColor;
        int borderColor;
        int inkColor;

        if (isVoid || "VOID".equalsIgnoreCase(statusCode)) {
            bgColor = COLOR_VOID_BG;
            borderColor = COLOR_VOID_BORDER;
            inkColor = COLOR_VOID_INK;
        } else if (Invoice.STATUS_PAID.equalsIgnoreCase(statusCode)) {
            bgColor = COLOR_PAID_BG;
            borderColor = COLOR_PAID_BORDER;
            inkColor = COLOR_PAID_INK;
        } else if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(statusCode)) {
            bgColor = COLOR_PARTIAL_BG;
            borderColor = COLOR_PARTIAL_BORDER;
            inkColor = COLOR_PARTIAL_INK;
        } else {
            bgColor = COLOR_UNPAID_BG;
            borderColor = COLOR_UNPAID_BORDER;
            inkColor = COLOR_UNPAID_INK;
        }

        TextPaint badgeTextPaint = createTextPaint(8.5f, Typeface.BOLD, inkColor);
        float textW = badgeTextPaint.measureText(statusLabel);
        float pillW = textW + 26f;
        float pillH = 20f;
        RectF pillRect = new RectF(rightEdge - pillW, topY, rightEdge, topY + pillH);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(bgColor);
        canvas.drawRoundRect(pillRect, 10f, 10f, fillPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1f);
        strokePaint.setColor(borderColor);
        canvas.drawRoundRect(pillRect, 10f, 10f, strokePaint);

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(inkColor);
        canvas.drawCircle(pillRect.left + 9f, pillRect.centerY(), 2.8f, dotPaint);

        canvas.drawText(statusLabel, pillRect.left + 16f, pillRect.top + 13.5f, badgeTextPaint);
    }

    private static float drawStudentAndInvoiceMetaCards(@NonNull Canvas canvas, float topY, @NonNull InvoiceData data) {
        float gap = 12f;
        float cardW = (CONTENT_WIDTH - gap) / 2f;

        List<String> studentSubLines = new ArrayList<>();
        if (!TextUtils.isEmpty(data.student.studentIdDisplay)) {
            studentSubLines.add("Student ID: " + data.student.studentIdDisplay);
        }
        String gradeSchool = data.student.gradeOrSchool();
        if (!TextUtils.isEmpty(gradeSchool)) {
            studentSubLines.add(gradeSchool);
        }
        String guardian = data.student.guardianSummary();
        if (!TextUtils.isEmpty(guardian)) {
            studentSubLines.add(guardian);
        }
        if (!TextUtils.isEmpty(data.student.address)) {
            studentSubLines.add(data.student.address);
        }

        int rightLineCount = 3; // Invoice No, Billing Period, Issue Date
        if (data.periodStart > 0 && data.periodEnd > 0) rightLineCount++;
        if (data.dueDate > 0) rightLineCount++;

        float leftH = 44f + (studentSubLines.size() * 13.5f);
        float rightH = 28f + (rightLineCount * 15f);
        float cardH = Math.max(88f, Math.max(leftH, rightH));

        RectF leftCard = new RectF(MARGIN_H, topY, MARGIN_H + cardW, topY + cardH);
        RectF rightCard = new RectF(MARGIN_H + cardW + gap, topY, PAGE_WIDTH - MARGIN_H, topY + cardH);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(COLOR_SURFACE_CARD);
        canvas.drawRoundRect(leftCard, 8f, 8f, fillPaint);
        canvas.drawRoundRect(rightCard, 8f, 8f, fillPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(COLOR_BORDER);
        borderPaint.setStrokeWidth(1f);
        canvas.drawRoundRect(leftCard, 8f, 8f, borderPaint);
        canvas.drawRoundRect(rightCard, 8f, 8f, borderPaint);

        // Left Card Content: Student Information
        float lx = leftCard.left + 12f;
        float ly = leftCard.top + 18f;
        TextPaint cardHeaderPaint = createTextPaint(8f, Typeface.BOLD, COLOR_BRAND_ACCENT);
        cardHeaderPaint.setLetterSpacing(0.06f);
        canvas.drawText("BILLED TO (STUDENT)", lx, ly, cardHeaderPaint);

        ly += 17f;
        TextPaint studentNamePaint = createTextPaint(12f, Typeface.BOLD, COLOR_INK_PRIMARY);
        canvas.drawText(ellipsize(data.student.getFormattedName(), studentNamePaint, cardW - 24f), lx, ly, studentNamePaint);

        ly += 15f;
        TextPaint subLinePaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        for (String line : studentSubLines) {
            canvas.drawText(ellipsize(line, subLinePaint, cardW - 24f), lx, ly, subLinePaint);
            ly += 13.5f;
        }

        // Right Card Content: Invoice Metadata
        float rx = rightCard.left + 12f;
        float rEdge = rightCard.right - 12f;
        float ry = rightCard.top + 18f;
        canvas.drawText("INVOICE DETAILS", rx, ry, cardHeaderPaint);

        ry += 17f;
        drawKeyValueRow(canvas, rx, rEdge, ry, "Invoice Number:", data.invoiceDisplayId);
        ry += 15f;
        drawKeyValueRow(canvas, rx, rEdge, ry, "Billing Period:", data.billingPeriod);
        if (data.periodStart > 0 && data.periodEnd > 0) {
            ry += 15f;
            String cycle = formatDateShort(data.periodStart) + " – " + formatDateShort(data.periodEnd);
            drawKeyValueRow(canvas, rx, rEdge, ry, "Billing Cycle:", cycle);
        }
        ry += 15f;
        drawKeyValueRow(canvas, rx, rEdge, ry, "Issue Date:", formatDate(data.issueDate));
        if (data.dueDate > 0) {
            ry += 15f;
            drawKeyValueRow(canvas, rx, rEdge, ry, "Due Date:", formatDate(data.dueDate));
        }

        return topY + cardH;
    }

    private static float drawProminentInvoiceSummaryStrip(@NonNull Canvas canvas, float topY, @NonNull InvoiceData data) {
        int currentDue = Math.max(0, data.totalAmountMinor - data.paidAmountMinor);
        boolean hasPrevDues = data.previousDuesMinor > 0;
        int cols = hasPrevDues ? 4 : 3;
        float gap = 10f;
        float boxW = (CONTENT_WIDTH - (gap * (cols - 1))) / cols;
        float boxH = 54f;

        drawSummaryCardBox(
                canvas,
                MARGIN_H,
                topY,
                boxW,
                boxH,
                "TOTAL AMOUNT",
                formatCurrency(data.totalAmountMinor),
                hasPrevDues ? "Current invoice charges" : "Billed for " + data.billingPeriod,
                COLOR_SURFACE_CARD,
                COLOR_BORDER,
                COLOR_INK_PRIMARY
        );

        drawSummaryCardBox(
                canvas,
                MARGIN_H + (boxW + gap),
                topY,
                boxW,
                boxH,
                "AMOUNT PAID",
                formatCurrency(data.paidAmountMinor),
                data.paidAmountMinor >= data.totalAmountMinor ? "Paid in full" : "Payments received",
                COLOR_PAID_BG,
                COLOR_PAID_BORDER,
                COLOR_PAID_INK
        );

        boolean isDuePositive = currentDue > 0;
        drawSummaryCardBox(
                canvas,
                MARGIN_H + (boxW + gap) * 2,
                topY,
                boxW,
                boxH,
                "AMOUNT DUE",
                formatCurrency(currentDue),
                isDuePositive ? "Remaining on this invoice" : "Settled (No due)",
                isDuePositive ? COLOR_UNPAID_BG : COLOR_PAID_BG,
                isDuePositive ? COLOR_UNPAID_BORDER : COLOR_PAID_BORDER,
                isDuePositive ? COLOR_UNPAID_INK : COLOR_PAID_INK
        );

        if (hasPrevDues) {
            int totalAccountBalance = currentDue + data.previousDuesMinor;
            drawSummaryCardBox(
                    canvas,
                    MARGIN_H + (boxW + gap) * 3,
                    topY,
                    boxW,
                    boxH,
                    "TOTAL BALANCE",
                    formatCurrency(totalAccountBalance),
                    "Incl. " + formatCurrency(data.previousDuesMinor) + " prev. due",
                    COLOR_PARTIAL_BG,
                    COLOR_PARTIAL_BORDER,
                    COLOR_PARTIAL_INK
            );
        }

        return topY + boxH;
    }

    private static float drawThreeBoxSummaryStrip(@NonNull Canvas canvas,
                                                  float topY,
                                                  @NonNull String label1, @NonNull String val1,
                                                  @NonNull String label2, @NonNull String val2,
                                                  @NonNull String label3, @NonNull String val3,
                                                  boolean highlightThirdAsDue) {
        float gap = 10f;
        float boxW = (CONTENT_WIDTH - (gap * 2)) / 3f;
        float boxH = 52f;

        drawSummaryCardBox(canvas, MARGIN_H, topY, boxW, boxH, label1, val1, null,
                COLOR_SURFACE_CARD, COLOR_BORDER, COLOR_INK_PRIMARY);
        drawSummaryCardBox(canvas, MARGIN_H + boxW + gap, topY, boxW, boxH, label2, val2, null,
                COLOR_PAID_BG, COLOR_PAID_BORDER, COLOR_PAID_INK);
        drawSummaryCardBox(canvas, MARGIN_H + (boxW + gap) * 2, topY, boxW, boxH, label3, val3, null,
                highlightThirdAsDue ? COLOR_UNPAID_BG : COLOR_PAID_BG,
                highlightThirdAsDue ? COLOR_UNPAID_BORDER : COLOR_PAID_BORDER,
                highlightThirdAsDue ? COLOR_UNPAID_INK : COLOR_PAID_INK);

        return topY + boxH;
    }

    private static void drawSummaryCardBox(@NonNull Canvas canvas,
                                           float left, float top, float width, float height,
                                           @NonNull String title, @NonNull String value, @Nullable String caption,
                                           int bgColor, int borderColor, int valueColor) {
        RectF rect = new RectF(left, top, left + width, top + height);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(bgColor);
        canvas.drawRoundRect(rect, 7f, 7f, bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);
        border.setColor(borderColor);
        canvas.drawRoundRect(rect, 7f, 7f, border);

        TextPaint titlePaint = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_SECONDARY);
        titlePaint.setLetterSpacing(0.04f);
        canvas.drawText(title, left + 10f, top + 15f, titlePaint);

        TextPaint valPaint = createTextPaint(13f, Typeface.BOLD, valueColor);
        canvas.drawText(ellipsize(value, valPaint, width - 16f), left + 10f, top + 34f, valPaint);

        if (!TextUtils.isEmpty(caption)) {
            TextPaint capPaint = createTextPaint(7.5f, Typeface.NORMAL, COLOR_INK_MUTED);
            canvas.drawText(ellipsize(caption, capPaint, width - 16f), left + 10f, top + 46f, capPaint);
        }
    }

    private static float drawSectionHeader(@NonNull Canvas canvas, float topY, @NonNull String title) {
        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(COLOR_BRAND_ACCENT);
        canvas.drawRoundRect(new RectF(MARGIN_H, topY, MARGIN_H + 3.5f, topY + 12f), 2f, 2f, barPaint);

        TextPaint titlePaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_BRAND_DARK);
        titlePaint.setLetterSpacing(0.05f);
        canvas.drawText(title, MARGIN_H + 9f, topY + 10f, titlePaint);
        return topY + 14f;
    }

    private static float drawInvoiceTableHeader(@NonNull Canvas canvas, float topY) {
        float h = 24f;
        RectF headerRect = new RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + h);
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(COLOR_BRAND_DARK);
        canvas.drawRoundRect(headerRect, 5f, 5f, bgPaint);
        canvas.drawRect(MARGIN_H, topY + 12f, PAGE_WIDTH - MARGIN_H, topY + h, bgPaint);

        TextPaint thPaint = createTextPaint(8.5f, Typeface.BOLD, Color.WHITE);
        float baseline = topY + 15.5f;

        canvas.drawText("#", MARGIN_H + 10f, baseline, thPaint);
        canvas.drawText("DESCRIPTION / ROUTINE & CLASSES", MARGIN_H + 32f, baseline, thPaint);
        canvas.drawText("BILLING TYPE", MARGIN_H + 278f, baseline, thPaint);

        thPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("QTY", MARGIN_H + 368f, baseline, thPaint);

        thPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("RATE", MARGIN_H + 442f, baseline, thPaint);
        canvas.drawText("AMOUNT", PAGE_WIDTH - MARGIN_H - 10f, baseline, thPaint);

        return topY + h;
    }

    private static float measureInvoiceItemRowHeight(@NonNull LineItemRow item) {
        TextPaint descPaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_PRIMARY);
        StaticLayout descLayout = buildStaticLayout(item.description, descPaint, 236);
        float height = 10f + descLayout.getHeight();
        if (!TextUtils.isEmpty(item.classDatesSummary)) {
            TextPaint subPaint = createTextPaint(8f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            StaticLayout subLayout = buildStaticLayout(item.classDatesSummary, subPaint, 236);
            height += 3f + subLayout.getHeight();
        }
        return Math.max(26f, height + 6f);
    }

    private static float drawInvoiceItemRow(@NonNull Canvas canvas,
                                            float topY,
                                            int index,
                                            @NonNull LineItemRow item,
                                            boolean isZebra) {
        float rowH = measureInvoiceItemRowHeight(item);

        if (isZebra) {
            Paint zebraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            zebraPaint.setColor(COLOR_SURFACE_ZEBRA);
            canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + rowH, zebraPaint);
        }

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(COLOR_BORDER);
        borderPaint.setStrokeWidth(0.7f);
        canvas.drawLine(MARGIN_H, topY + rowH, PAGE_WIDTH - MARGIN_H, topY + rowH, borderPaint);

        float textTop = topY + 7f;
        TextPaint idxPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(String.valueOf(index), MARGIN_H + 10f, textTop + 9f, idxPaint);

        TextPaint descPaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_PRIMARY);
        StaticLayout descLayout = buildStaticLayout(item.description, descPaint, 236);
        canvas.save();
        canvas.translate(MARGIN_H + 32f, textTop);
        descLayout.draw(canvas);
        canvas.restore();

        if (!TextUtils.isEmpty(item.classDatesSummary)) {
            TextPaint datesPaint = createTextPaint(8f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            StaticLayout datesLayout = buildStaticLayout(item.classDatesSummary, datesPaint, 236);
            canvas.save();
            canvas.translate(MARGIN_H + 32f, textTop + descLayout.getHeight() + 2f);
            datesLayout.draw(canvas);
            canvas.restore();
        }

        String modelText = "PER_CLASS".equalsIgnoreCase(item.billingModel) ? "Per Class" : "Monthly";
        TextPaint modelPaint = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(modelText, MARGIN_H + 278f, textTop + 9f, modelPaint);

        String qtyLabel = "PER_CLASS".equalsIgnoreCase(item.billingModel)
                ? item.quantity + (item.quantity == 1 ? " class" : " classes")
                : String.valueOf(item.quantity);
        TextPaint qtyPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_PRIMARY);
        qtyPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(qtyLabel, MARGIN_H + 368f, textTop + 9f, qtyPaint);

        TextPaint numPaint = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        numPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(item.unitPriceMinor), MARGIN_H + 442f, textTop + 9f, numPaint);

        TextPaint amountPaint = createTextPaint(9.5f, Typeface.BOLD, COLOR_INK_PRIMARY);
        amountPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(item.amountMinor), PAGE_WIDTH - MARGIN_H - 10f, textTop + 9f, amountPaint);

        return topY + rowH;
    }

    private static float drawTotalsAndNotesSection(@NonNull Canvas canvas, float topY, @NonNull InvoiceData data) {
        float leftWidth = CONTENT_WIDTH * 0.46f;
        float rightWidth = CONTENT_WIDTH * 0.50f;
        float rightLeft = PAGE_WIDTH - MARGIN_H - rightWidth;

        float leftY = topY;
        if (!TextUtils.isEmpty(data.notes)) {
            TextPaint noteLabel = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_SECONDARY);
            canvas.drawText("NOTES / REMARKS", MARGIN_H, leftY + 10f, noteLabel);
            leftY += 15f;

            TextPaint noteBody = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
            StaticLayout noteLayout = buildStaticLayout(data.notes, noteBody, (int) (leftWidth - 20f));
            float boxH = Math.max(36f, noteLayout.getHeight() + 14f);

            RectF noteRect = new RectF(MARGIN_H, leftY, MARGIN_H + leftWidth, leftY + boxH);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(COLOR_SURFACE_CARD);
            canvas.drawRoundRect(noteRect, 6f, 6f, fill);

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setColor(COLOR_BORDER);
            border.setStrokeWidth(1f);
            canvas.drawRoundRect(noteRect, 6f, 6f, border);

            canvas.save();
            canvas.translate(MARGIN_H + 10f, leftY + 7f);
            noteLayout.draw(canvas);
            canvas.restore();

            leftY += boxH + 12f;
        }

        drawOfficialStatusSeal(canvas, MARGIN_H, leftY + 4f, data);

        float ry = topY;
        float rLabelX = rightLeft + 12f;
        float rValX = PAGE_WIDTH - MARGIN_H - 12f;

        int currentInvoiceDue = Math.max(0, data.totalAmountMinor - data.paidAmountMinor);
        int overallRemainingBalance = currentInvoiceDue + data.previousDuesMinor;

        List<SummaryLine> lines = new ArrayList<>();
        lines.add(new SummaryLine("Billable Fees Subtotal:", formatCurrency(data.subtotalMinor), COLOR_INK_PRIMARY, false));
        if (data.discountMinor > 0) {
            lines.add(new SummaryLine("Discount Applied:", "- " + formatCurrency(data.discountMinor), COLOR_PAID_INK, false));
        }
        lines.add(new SummaryLine("Current Invoice Total:", formatCurrency(data.totalAmountMinor), COLOR_INK_PRIMARY, true));
        lines.add(new SummaryLine("Payments Received (This Invoice):", "- " + formatCurrency(data.paidAmountMinor), COLOR_PAID_INK, false));
        lines.add(new SummaryLine("Current Invoice Due:", formatCurrency(currentInvoiceDue),
                currentInvoiceDue > 0 ? COLOR_UNPAID_INK : COLOR_PAID_INK, true));

        if (data.previousDuesMinor > 0) {
            String prevLabel = !TextUtils.isEmpty(data.previousDuesRefs)
                    ? "Previous Dues (" + data.previousDuesRefs + "):"
                    : "Previous Unpaid Dues:";
            lines.add(new SummaryLine(prevLabel, "+ " + formatCurrency(data.previousDuesMinor), COLOR_PARTIAL_INK, false));
        }

        float boxHeight = (lines.size() * 18f) + 46f;
        RectF summaryCard = new RectF(rightLeft, ry, PAGE_WIDTH - MARGIN_H, ry + boxHeight);
        Paint cardBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBg.setColor(COLOR_SURFACE_CARD);
        canvas.drawRoundRect(summaryCard, 8f, 8f, cardBg);

        Paint cardBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorder.setStyle(Paint.Style.STROKE);
        cardBorder.setColor(COLOR_BORDER_STRONG);
        cardBorder.setStrokeWidth(1f);
        canvas.drawRoundRect(summaryCard, 8f, 8f, cardBorder);

        float lineY = ry + 18f;
        for (SummaryLine line : lines) {
            TextPaint lp = createTextPaint(9f, line.isBold ? Typeface.BOLD : Typeface.NORMAL, COLOR_INK_SECONDARY);
            TextPaint vp = createTextPaint(9.5f, line.isBold ? Typeface.BOLD : Typeface.NORMAL, line.valueColor);
            vp.setTextAlign(Paint.Align.RIGHT);

            canvas.drawText(ellipsize(line.label, lp, rightWidth - 95f), rLabelX, lineY, lp);
            canvas.drawText(line.value, rValX, lineY, vp);
            lineY += 18f;
        }

        RectF netBar = new RectF(rightLeft, ry + boxHeight - 32f, PAGE_WIDTH - MARGIN_H, ry + boxHeight);
        Paint netBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        netBg.setColor(overallRemainingBalance > 0 ? COLOR_BRAND_DARK : COLOR_PAID_INK);
        canvas.drawRoundRect(netBar, 8f, 8f, netBg);
        canvas.drawRect(rightLeft, ry + boxHeight - 32f, PAGE_WIDTH - MARGIN_H, ry + boxHeight - 16f, netBg);

        TextPaint netLabelPaint = createTextPaint(9.5f, Typeface.BOLD, Color.WHITE);
        String finalBalanceTitle = data.previousDuesMinor > 0 ? "TOTAL REMAINING BALANCE" : "REMAINING BALANCE DUE";
        canvas.drawText(finalBalanceTitle, rLabelX, ry + boxHeight - 11.5f, netLabelPaint);

        TextPaint netValPaint = createTextPaint(12f, Typeface.BOLD, Color.WHITE);
        netValPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(overallRemainingBalance), rValX, ry + boxHeight - 11f, netValPaint);

        return Math.max(leftY + 46f, ry + boxHeight);
    }

    private static void drawOfficialStatusSeal(@NonNull Canvas canvas, float leftX, float topY, @NonNull InvoiceData data) {
        int borderColor;
        int inkColor;
        String stampTitle;
        String stampSub;

        if (data.isVoid) {
            borderColor = COLOR_VOID_BORDER;
            inkColor = COLOR_VOID_INK;
            stampTitle = "VOIDED INVOICE";
            stampSub = "No balance payable on this document";
        } else if (Invoice.STATUS_PAID.equalsIgnoreCase(data.status)) {
            borderColor = COLOR_PAID_BORDER;
            inkColor = COLOR_PAID_INK;
            stampTitle = "PAID IN FULL";
            stampSub = "Thank you for your timely payment";
        } else if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(data.status)) {
            borderColor = COLOR_PARTIAL_BORDER;
            inkColor = COLOR_PARTIAL_INK;
            stampTitle = "PARTIALLY PAID";
            stampSub = "Remaining due: " + formatCurrency(Math.max(0, data.totalAmountMinor - data.paidAmountMinor));
        } else {
            borderColor = COLOR_UNPAID_BORDER;
            inkColor = COLOR_UNPAID_INK;
            stampTitle = "PAYMENT DUE";
            stampSub = data.dueDate > 0 ? "Please settle by " + formatDate(data.dueDate) : "Awaiting payment settlement";
        }

        RectF sealRect = new RectF(leftX, topY, leftX + 195f, topY + 42f);
        Paint sealBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        sealBorder.setStyle(Paint.Style.STROKE);
        sealBorder.setStrokeWidth(1.5f);
        sealBorder.setColor(borderColor);
        sealBorder.setPathEffect(new DashPathEffect(new float[]{4f, 2f}, 0f));
        canvas.drawRoundRect(sealRect, 6f, 6f, sealBorder);

        TextPaint t1 = createTextPaint(10f, Typeface.BOLD, inkColor);
        t1.setLetterSpacing(0.06f);
        canvas.drawText("STATUS: " + stampTitle, leftX + 10f, topY + 17f, t1);

        TextPaint t2 = createTextPaint(8f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(stampSub, leftX + 10f, topY + 31f, t2);
    }

    private static float drawPaymentsTableHeader(@NonNull Canvas canvas, float topY) {
        float h = 22f;
        RectF rect = new RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + h);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(COLOR_BRAND_PRIMARY);
        canvas.drawRoundRect(rect, 5f, 5f, bg);
        canvas.drawRect(MARGIN_H, topY + 10f, PAGE_WIDTH - MARGIN_H, topY + h, bg);

        TextPaint th = createTextPaint(8f, Typeface.BOLD, Color.WHITE);
        float base = topY + 14.5f;
        canvas.drawText("RECEIPT / TXN ID", MARGIN_H + 10f, base, th);
        canvas.drawText("PAYMENT DATE", MARGIN_H + 115f, base, th);
        canvas.drawText("METHOD", MARGIN_H + 245f, base, th);
        canvas.drawText("REFERENCE / NOTES", MARGIN_H + 325f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("AMOUNT PAID", PAGE_WIDTH - MARGIN_H - 10f, base, th);
        return topY + h;
    }

    private static float drawPaymentTableRow(@NonNull Canvas canvas, float topY, @NonNull PaymentRow p, boolean isZebra) {
        float rowH = 22f;
        if (isZebra) {
            Paint z = new Paint(Paint.ANTI_ALIAS_FLAG);
            z.setColor(COLOR_SURFACE_ZEBRA);
            canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + rowH, z);
        }

        Paint div = new Paint(Paint.ANTI_ALIAS_FLAG);
        div.setColor(COLOR_BORDER);
        div.setStrokeWidth(0.6f);
        canvas.drawLine(MARGIN_H, topY + rowH, PAGE_WIDTH - MARGIN_H, topY + rowH, div);

        float base = topY + 14.5f;
        TextPaint idPaint = createTextPaint(8.5f, Typeface.BOLD, COLOR_BRAND_ACCENT);
        canvas.drawText(ellipsize(p.displayId, idPaint, 98f), MARGIN_H + 10f, base, idPaint);

        TextPaint regPaint = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(formatDateTimeShort(p.timestamp), MARGIN_H + 115f, base, regPaint);
        canvas.drawText(ellipsize(nonEmptyOrDefault(p.method, "Cash"), regPaint, 72f), MARGIN_H + 245f, base, regPaint);

        String refNote = buildRefAndNote(p.referenceNo, p.notes);
        canvas.drawText(ellipsize(refNote, regPaint, 115f), MARGIN_H + 325f, base, regPaint);

        TextPaint amtPaint = createTextPaint(9f, Typeface.BOLD, COLOR_PAID_INK);
        amtPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(p.amountMinor), PAGE_WIDTH - MARGIN_H - 10f, base, amtPaint);

        return topY + rowH;
    }

    private static float drawEmptyPaymentsBox(@NonNull Canvas canvas, float topY, @NonNull String message) {
        RectF box = new RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 28f);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(COLOR_SURFACE_CARD);
        canvas.drawRoundRect(box, 5f, 5f, bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(COLOR_BORDER);
        border.setStrokeWidth(0.8f);
        canvas.drawRoundRect(box, 5f, 5f, border);

        TextPaint tp = createTextPaint(8.5f, Typeface.ITALIC, COLOR_INK_MUTED);
        canvas.drawText(message, MARGIN_H + 12f, topY + 17.5f, tp);
        return topY + 32f;
    }

    private static float drawStatementTableHeader(@NonNull Canvas canvas, float topY) {
        float h = 22f;
        RectF rect = new RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + h);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(COLOR_BRAND_DARK);
        canvas.drawRoundRect(rect, 5f, 5f, bg);
        canvas.drawRect(MARGIN_H, topY + 10f, PAGE_WIDTH - MARGIN_H, topY + h, bg);

        TextPaint th = createTextPaint(8f, Typeface.BOLD, Color.WHITE);
        float base = topY + 14.5f;
        canvas.drawText("INVOICE ID", MARGIN_H + 10f, base, th);
        canvas.drawText("BILLING PERIOD", MARGIN_H + 95f, base, th);
        canvas.drawText("ISSUE DATE", MARGIN_H + 210f, base, th);
        canvas.drawText("STATUS", MARGIN_H + 290f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("TOTAL", MARGIN_H + 405f, base, th);
        canvas.drawText("PAID", MARGIN_H + 465f, base, th);
        canvas.drawText("DUE", PAGE_WIDTH - MARGIN_H - 10f, base, th);
        return topY + h;
    }

    private static float drawStatementInvoiceRow(@NonNull Canvas canvas, float topY, @NonNull InvoiceData inv, boolean isZebra) {
        float rowH = 22f;
        if (isZebra) {
            Paint z = new Paint(Paint.ANTI_ALIAS_FLAG);
            z.setColor(COLOR_SURFACE_ZEBRA);
            canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + rowH, z);
        }

        float base = topY + 14.5f;
        TextPaint idP = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_PRIMARY);
        canvas.drawText(inv.invoiceDisplayId, MARGIN_H + 10f, base, idP);

        TextPaint reg = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(ellipsize(inv.billingPeriod, reg, 108f), MARGIN_H + 95f, base, reg);
        canvas.drawText(formatDateShort(inv.issueDate), MARGIN_H + 210f, base, reg);
        canvas.drawText(resolveStatusLabel(inv.status, inv.isVoid), MARGIN_H + 290f, base, reg);

        int due = Math.max(0, inv.totalAmountMinor - inv.paidAmountMinor);
        reg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(inv.totalAmountMinor), MARGIN_H + 405f, base, reg);
        canvas.drawText(formatCurrency(inv.paidAmountMinor), MARGIN_H + 465f, base, reg);

        TextPaint dueP = createTextPaint(8.5f, Typeface.BOLD, due > 0 ? COLOR_UNPAID_INK : COLOR_PAID_INK);
        dueP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(due), PAGE_WIDTH - MARGIN_H - 10f, base, dueP);
        return topY + rowH;
    }

    private static float drawReportTableHeader(@NonNull Canvas canvas, float topY) {
        float h = 22f;
        RectF rect = new RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + h);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(COLOR_BRAND_DARK);
        canvas.drawRoundRect(rect, 5f, 5f, bg);
        canvas.drawRect(MARGIN_H, topY + 10f, PAGE_WIDTH - MARGIN_H, topY + h, bg);

        TextPaint th = createTextPaint(8f, Typeface.BOLD, Color.WHITE);
        float base = topY + 14.5f;
        canvas.drawText("INVOICE", MARGIN_H + 8f, base, th);
        canvas.drawText("STUDENT (NAME · ID)", MARGIN_H + 78f, base, th);
        canvas.drawText("PERIOD", MARGIN_H + 235f, base, th);
        canvas.drawText("STATUS", MARGIN_H + 315f, base, th);

        th.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("TOTAL", MARGIN_H + 412f, base, th);
        canvas.drawText("PAID", MARGIN_H + 468f, base, th);
        canvas.drawText("DUE", PAGE_WIDTH - MARGIN_H - 8f, base, th);
        return topY + h;
    }

    private static float drawReportInvoiceRow(@NonNull Canvas canvas, float topY, @NonNull InvoiceData inv, boolean isZebra) {
        float rowH = 22f;
        if (isZebra) {
            Paint z = new Paint(Paint.ANTI_ALIAS_FLAG);
            z.setColor(COLOR_SURFACE_ZEBRA);
            canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + rowH, z);
        }

        float base = topY + 14.5f;
        TextPaint idP = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_PRIMARY);
        canvas.drawText(ellipsize(inv.invoiceDisplayId, idP, 66f), MARGIN_H + 8f, base, idP);

        TextPaint reg = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        canvas.drawText(ellipsize(inv.student.getFormattedName(), reg, 150f), MARGIN_H + 78f, base, reg);
        canvas.drawText(ellipsize(inv.billingPeriod, reg, 75f), MARGIN_H + 235f, base, reg);
        canvas.drawText(resolveStatusLabel(inv.status, inv.isVoid), MARGIN_H + 315f, base, reg);

        int due = Math.max(0, inv.totalAmountMinor - inv.paidAmountMinor);
        reg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(inv.totalAmountMinor), MARGIN_H + 412f, base, reg);
        canvas.drawText(formatCurrency(inv.paidAmountMinor), MARGIN_H + 468f, base, reg);

        TextPaint dueP = createTextPaint(8.5f, Typeface.BOLD, due > 0 ? COLOR_UNPAID_INK : COLOR_PAID_INK);
        dueP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatCurrency(due), PAGE_WIDTH - MARGIN_H - 8f, base, dueP);
        return topY + rowH;
    }

    private static float drawContinuationHeader(@NonNull Canvas canvas, @NonNull String docTitle, @NonNull String subtitle) {
        float y = MARGIN_TOP - 4f;
        TextPaint leftP = createTextPaint(10f, Typeface.BOLD, COLOR_BRAND_DARK);
        canvas.drawText(docTitle + " (Continued)", MARGIN_H, y + 10f, leftP);

        TextPaint rightP = createTextPaint(9f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        rightP.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(subtitle, PAGE_WIDTH - MARGIN_H, y + 10f, rightP);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(COLOR_BORDER);
        line.setStrokeWidth(1f);
        canvas.drawLine(MARGIN_H, y + 16f, PAGE_WIDTH - MARGIN_H, y + 16f, line);
        return y + 16f;
    }

    private static void drawPageFooter(@NonNull Canvas canvas,
                                       @NonNull TutorProfile tutor,
                                       int pageNum,
                                       int totalPages,
                                       boolean includeSignatures) {
        float footerTop = PAGE_HEIGHT - 46f;

        if (includeSignatures) {
            float sigLineY = footerTop - 14f;
            Paint sigLine = new Paint(Paint.ANTI_ALIAS_FLAG);
            sigLine.setColor(COLOR_BORDER_STRONG);
            sigLine.setStrokeWidth(0.9f);

            canvas.drawLine(MARGIN_H, sigLineY, MARGIN_H + 130f, sigLineY, sigLine);
            canvas.drawLine(PAGE_WIDTH - MARGIN_H - 130f, sigLineY, PAGE_WIDTH - MARGIN_H, sigLineY, sigLine);

            TextPaint sigLabel = createTextPaint(7.5f, Typeface.NORMAL, COLOR_INK_MUTED);
            canvas.drawText("Student / Guardian Signature", MARGIN_H, sigLineY + 10f, sigLabel);

            sigLabel.setTextAlign(Paint.Align.RIGHT);
            String tutorSig = !TextUtils.isEmpty(tutor.fullName) ? tutor.fullName + " (Tutor)" : "Authorized Tutor Signature";
            canvas.drawText(tutorSig, PAGE_WIDTH - MARGIN_H, sigLineY + 10f, sigLabel);
        }

        Paint divPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        divPaint.setColor(COLOR_BORDER);
        divPaint.setStrokeWidth(0.9f);
        canvas.drawLine(MARGIN_H, footerTop, PAGE_WIDTH - MARGIN_H, footerTop, divPaint);

        TextPaint leftFooter = createTextPaint(8f, Typeface.BOLD, COLOR_BRAND_PRIMARY);
        canvas.drawText("TutorSync  •  Smart Offline Tuition & Fee Management", MARGIN_H, footerTop + 14f, leftFooter);

        TextPaint subFooter = createTextPaint(7.5f, Typeface.NORMAL, COLOR_INK_MUTED);
        canvas.drawText("Generated on " + formatDateTime(System.currentTimeMillis()), MARGIN_H, footerTop + 25f, subFooter);

        TextPaint pagePaint = createTextPaint(8.5f, Typeface.BOLD, COLOR_INK_SECONDARY);
        pagePaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Page " + pageNum + " of " + totalPages, PAGE_WIDTH - MARGIN_H, footerTop + 18f, pagePaint);
    }

    // =========================================================================================
    // 7. DATABASE QUERIES (SQLite Data Extraction with Full Backward Compatibility)
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
        return "Billable Class Dates: " + TextUtils.join(", ", dates);
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
                    .setLineSpacing(1.5f, 1.0f)
                    .setIncludePad(false)
                    .build();
        } else {
            return new StaticLayout(text, paint, safeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 1.5f, false);
        }
    }

    private static void drawKeyValueRow(@NonNull Canvas canvas, float leftX, float rightX, float baselineY, @NonNull String key, @NonNull String value) {
        TextPaint keyPaint = createTextPaint(8.5f, Typeface.NORMAL, COLOR_INK_SECONDARY);
        TextPaint valPaint = createTextPaint(9f, Typeface.BOLD, COLOR_INK_PRIMARY);
        valPaint.setTextAlign(Paint.Align.RIGHT);

        canvas.drawText(key, leftX, baselineY, keyPaint);
        float maxValWidth = Math.max(40f, (rightX - leftX) - keyPaint.measureText(key) - 8f);
        canvas.drawText(ellipsize(value, valPaint, maxValWidth), rightX, baselineY, valPaint);
    }

    private static void drawMiniMetric(@NonNull Canvas canvas, float leftX, float topY, float width, @NonNull String label, @NonNull String value, int valColor) {
        TextPaint lp = createTextPaint(7.5f, Typeface.BOLD, COLOR_INK_MUTED);
        canvas.drawText(label, leftX + 12f, topY + 10f, lp);

        TextPaint vp = createTextPaint(11.5f, Typeface.BOLD, valColor);
        canvas.drawText(ellipsize(value, vp, width - 20f), leftX + 12f, topY + 30f, vp);
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
    private static String formatDateTime(long millis) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    @NonNull
    private static String formatDateTimeShort(long millis) {
        if (millis <= 0) return "-";
        return new SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    @NonNull
    private static String resolveStatusLabel(@Nullable String status, boolean isVoid) {
        if (isVoid) return "VOID";
        if (Invoice.STATUS_PAID.equalsIgnoreCase(status)) return "PAID";
        if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(status)) return "PARTIALLY PAID";
        return "UNPAID";
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
            if (!TextUtils.isEmpty(guardianPhone)) return "Guardian Phone: " + guardianPhone;
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

    private static final class SummaryLine {
        final String label;
        final String value;
        final int valueColor;
        final boolean isBold;

        SummaryLine(String label, String value, int valueColor, boolean isBold) {
            this.label = label;
            this.value = value;
            this.valueColor = valueColor;
            this.isBold = isBold;
        }
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