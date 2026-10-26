package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PaymentReminderHelper {

    @NonNull
    public static String buildEnglishTemplate(@NonNull Student student, @NonNull Invoice invoice) {
        String dueStr = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date(invoice.getDueDate()));
        return String.format(Locale.ENGLISH,
                "Assalamu Alaikum,\nThis is a gentle reminder for the tuition fee of %s.\n" +
                        "Invoice ID: %s\nBilling Period: %s\nTotal Amount: ৳%.2f\nPaid: ৳%.2f\n" +
                        "Outstanding Due: ৳%.2f (Due by %s).\nThank you!",
                student.getFormattedDisplayName(),
                invoice.getInvoiceIdDisplay(),
                invoice.getBillingPeriod(),
                invoice.getTotalAmount() / 100.0,
                invoice.getPaidAmount() / 100.0,
                invoice.getOutstandingBalance() / 100.0,
                dueStr);
    }

    @NonNull
    public static String buildBanglaTemplate(@NonNull Student student, @NonNull Invoice invoice) {
        String dueStr = new SimpleDateFormat("dd MMM yyyy", new Locale("bn", "BD")).format(new Date(invoice.getDueDate()));
        return String.format(Locale.US,
                "আসসালামু আলাইকুম,\n%s-এর টিউশন ফি সংক্রান্ত রিমাইন্ডার:\n" +
                        "ইনভয়েস আইডি: %s\nবিলিং সময়কাল: %s\nমোট বিল: ৳%.2f\nপরিশোধিত: ৳%.2f\n" +
                        "বকেয়া পরিমাণ: ৳%.2f (পরিশোধের শেষ তারিখ: %s)।\nধন্যবাদ।",
                student.getFormattedDisplayName(),
                invoice.getInvoiceIdDisplay(),
                invoice.getBillingPeriod(),
                invoice.getTotalAmount() / 100.0,
                invoice.getPaidAmount() / 100.0,
                invoice.getOutstandingBalance() / 100.0,
                dueStr);
    }

    public static void showEditableReminderDialog(
            @NonNull Context context,
            @NonNull Student student,
            @NonNull Invoice invoice,
            boolean defaultBangla) {

        EditText input = new EditText(context);
        input.setMinLines(5);
        input.setText(defaultBangla ? buildBanglaTemplate(student, invoice) : buildEnglishTemplate(student, invoice));

        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(context);
        container.setPadding(pad, pad, pad, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Send Payment Reminder (EN / বাংলা)")
                .setView(container)
                .setNeutralButton("Switch EN/বাংলা", null)
                .setNegativeButton("SMS", (d, w) -> {
                    String phone = student.getGuardianPhone() != null ? student.getGuardianPhone() : "";
                    Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
                    smsIntent.putExtra("sms_body", input.getText().toString());
                    context.startActivity(smsIntent);
                })
                .setPositiveButton("WhatsApp / Share", (d, w) -> {
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, input.getText().toString());
                    context.startActivity(Intent.createChooser(sendIntent, "Send Reminder"));
                })
                .create();

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Payment Reminder")
                .setView(container)
                .setNeutralButton("বাংলা / EN", null)
                .setNegativeButton("SMS", (d, w) -> {
                    String phone = student.getGuardianPhone() != null ? student.getGuardianPhone() : "";
                    Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
                    smsIntent.putExtra("sms_body", input.getText().toString());
                    context.startActivity(smsIntent);
                })
                .setPositiveButton("WhatsApp / Share", (d, w) -> {
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("text/plain");
                    sendIntent.putExtra(Intent.EXTRA_TEXT, input.getText().toString());
                    context.startActivity(Intent.createChooser(sendIntent, "Share Reminder"));
                })
                .show();

        final boolean[] isBn = {defaultBangla};
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            isBn[0] = !isBn[0];
            input.setText(isBn[0] ? buildBanglaTemplate(student, invoice) : buildEnglishTemplate(student, invoice));
        });
    }
}