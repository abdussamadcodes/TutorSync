package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.InvoiceItem;
import com.abdussamadcodes.tutorsync.model.Student;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FinancialPdfGenerator {

    @NonNull
    public static File generateInvoicePdf(
            @NonNull Context context,
            @NonNull String tutorName,
            @NonNull Student student,
            @NonNull Invoice invoice,
            @NonNull List<InvoiceItem> items,
            @NonNull List<FinancialTransaction> payments) throws IOException {

        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(20f);
        titlePaint.setColor(Color.rgb(26, 35, 126));

        Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boldPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        boldPaint.setTextSize(11f);
        boldPaint.setColor(Color.BLACK);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(10.5f);
        textPaint.setColor(Color.DKGRAY);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(1f);

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        int y = 50;

        // Header
        canvas.drawText("TUTORSYNC INVOICE", 40, y, titlePaint);
        canvas.drawText("Invoice ID: " + invoice.getInvoiceIdDisplay(), 390, y, boldPaint);
        y += 22;
        canvas.drawText("Tutor: " + tutorName, 40, y, textPaint);
        canvas.drawText("Status: " + invoice.getStatus(), 390, y, boldPaint);
        y += 16;
        canvas.drawText("Student: " + student.getFormattedDisplayName(), 40, y, boldPaint);
        canvas.drawText("Issue Date: " + df.format(new Date(invoice.getIssueDate())), 390, y, textPaint);
        y += 16;
        canvas.drawText("Billing Period: " + invoice.getBillingPeriod(), 40, y, textPaint);
        canvas.drawText("Due Date: " + df.format(new Date(invoice.getDueDate())), 390, y, textPaint);
        y += 20;
        canvas.drawLine(40, y, 555, y, linePaint);
        y += 20;

        // Itemized Charges Table Header
        canvas.drawText("Description", 40, y, boldPaint);
        canvas.drawText("Model", 360, y, boldPaint);
        canvas.drawText("Amount (BDT)", 465, y, boldPaint);
        y += 10;
        canvas.drawLine(40, y, 555, y, linePaint);
        y += 18;

        for (InvoiceItem item : items) {
            canvas.drawText(item.getDescription(), 40, y, textPaint);
            canvas.drawText(item.getBillingModel(), 360, y, textPaint);
            canvas.drawText(formatBdt(item.getAmountMinor()), 465, y, textPaint);
            y += 18;
        }

        y += 6;
        canvas.drawLine(40, y, 555, y, linePaint);
        y += 20;

        // Totals Summary
        canvas.drawText("Total Billed:", 360, y, boldPaint);
        canvas.drawText(formatBdt(invoice.getTotalAmount()), 465, y, boldPaint);
        y += 18;
        canvas.drawText("Payments Applied:", 360, y, textPaint);
        canvas.drawText(formatBdt(invoice.getPaidAmount()), 465, y, textPaint);
        y += 18;
        canvas.drawText("Outstanding Balance:", 360, y, boldPaint);
        canvas.drawText(formatBdt(invoice.getOutstandingBalance()), 465, y, boldPaint);
        y += 30;

        // Applied Payments Section
        if (!payments.isEmpty()) {
            canvas.drawText("Related Transactions", 40, y, boldPaint);
            y += 16;
            for (FinancialTransaction txn : payments) {
                String sign = FinancialTransaction.TYPE_REFUND.equals(txn.getType()) ? "-" : "+";
                String line = String.format(Locale.US, "%s · %s · %s (%s): %s%s",
                        txn.getTxnIdDisplay(), df.format(new Date(txn.getTimestamp())),
                        txn.getMethod(), txn.getType(), sign, formatBdt(txn.getAmountMinor()));
                canvas.drawText(line, 40, y, textPaint);
                y += 16;
            }
        }

        pdf.finishPage(page);

        File dir = new File(context.getCacheDir(), "documents");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, invoice.getInvoiceIdDisplay() + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            pdf.writeTo(fos);
        } finally {
            pdf.close();
        }
        return file;
    }

    @NonNull
    public static File generatePaymentReceiptPdf(
            @NonNull Context context,
            @NonNull String tutorName,
            @NonNull Student student,
            @NonNull FinancialTransaction transaction) throws IOException {

        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 420, 1).create(); // A5 Landscape Receipt
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(18f);
        titlePaint.setColor(Color.rgb(27, 94, 32));

        Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boldPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        boldPaint.setTextSize(11f);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(11f);

        SimpleDateFormat dtf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        int y = 45;

        canvas.drawText("OFFICIAL PAYMENT RECEIPT", 40, y, titlePaint);
        canvas.drawText("Receipt #: " + transaction.getTxnIdDisplay(), 380, y, boldPaint);
        y += 28;
        canvas.drawText("Received From: " + student.getFormattedDisplayName(), 40, y, boldPaint);
        y += 20;
        canvas.drawText("Date & Time: " + dtf.format(new Date(transaction.getTimestamp())), 40, y, textPaint);
        y += 20;
        canvas.drawText("Amount: " + formatBdt(transaction.getAmountMinor()) + " (" + transaction.getType() + ")", 40, y, boldPaint);
        y += 20;
        canvas.drawText("Payment Method: " + transaction.getMethod(), 40, y, textPaint);
        if (transaction.getReferenceNo() != null && !transaction.getReferenceNo().isEmpty()) {
            y += 20;
            canvas.drawText("Reference No: " + transaction.getReferenceNo(), 40, y, textPaint);
        }
        if (!transaction.getRelatedInvoiceDisplayIds().isEmpty()) {
            y += 20;
            canvas.drawText("Applied To Invoice(s): " + String.join(", ", transaction.getRelatedInvoiceDisplayIds()), 40, y, boldPaint);
        }
        y += 35;
        canvas.drawText("Issued By: " + tutorName + " · Generated offline via TutorSync", 40, y, textPaint);

        pdf.finishPage(page);

        File dir = new File(context.getCacheDir(), "documents");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, transaction.getTxnIdDisplay() + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            pdf.writeTo(fos);
        } finally {
            pdf.close();
        }
        return file;
    }

    public static void sharePdf(@NonNull Context context, @NonNull File pdfFile, @NonNull String title) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, title));
    }

    private static String formatBdt(int minorUnits) {
        return String.format(Locale.US, "৳ %.2f", minorUnits / 100.0);
    }
}