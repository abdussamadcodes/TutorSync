package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.database.DatabaseManager;
import com.abdussamadcodes.tutorsync.database.TutorSyncContract.AccountEntry;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PaymentReminderHelper {

    private static final class TutorContactInfo {
        String name;
        String mobile;
        String email;
        String address;
    }

    @NonNull
    public static String buildEnglishTemplate(@NonNull Student student, @NonNull Invoice invoice) {
        return buildEnglishTemplate(student, invoice, loadTutorInfoFromDb(invoice.getAccountId()));
    }

    @NonNull
    public static String buildEnglishTemplate(
            @NonNull Student student,
            @NonNull Invoice invoice,
            @Nullable Account tutorAccount) {
        TutorContactInfo info = extractTutorInfo(tutorAccount, invoice.getAccountId());
        return buildEnglishTemplate(student, invoice, info);
    }

    @NonNull
    private static String buildEnglishTemplate(
            @NonNull Student student,
            @NonNull Invoice invoice,
            @Nullable TutorContactInfo tutor) {
        String dueStr = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                .format(new Date(invoice.getDueDate()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH,
                "Assalamu Alaikum,\n" +
                        "This is a gentle reminder for the tuition fee of %s.\n" +
                        "Invoice ID: %s\n" +
                        "Billing Period: %s\n" +
                        "Total Amount: ৳%.2f\n" +
                        "Paid: ৳%.2f\n" +
                        "Outstanding Due: ৳%.2f (Due by %s).\n" +
                        "Thank you!",
                student.getFormattedDisplayName(),
                invoice.getInvoiceIdDisplay(),
                invoice.getBillingPeriod(),
                invoice.getTotalAmount() / 100.0,
                invoice.getPaidAmount() / 100.0,
                invoice.getOutstandingBalance() / 100.0,
                dueStr));

        String tutorBlock = formatTutorBlockEnglish(tutor);
        if (!tutorBlock.isEmpty()) {
            sb.append("\n\n").append(tutorBlock);
        }
        return sb.toString();
    }

    @NonNull
    public static String buildBanglaTemplate(@NonNull Student student, @NonNull Invoice invoice) {
        return buildBanglaTemplate(student, invoice, loadTutorInfoFromDb(invoice.getAccountId()));
    }

    @NonNull
    public static String buildBanglaTemplate(
            @NonNull Student student,
            @NonNull Invoice invoice,
            @Nullable Account tutorAccount) {
        TutorContactInfo info = extractTutorInfo(tutorAccount, invoice.getAccountId());
        return buildBanglaTemplate(student, invoice, info);
    }

    @NonNull
    private static String buildBanglaTemplate(
            @NonNull Student student,
            @NonNull Invoice invoice,
            @Nullable TutorContactInfo tutor) {
        Locale bnLocale = Locale.forLanguageTag("bn-BD");
        String dueStr = new SimpleDateFormat("dd MMM yyyy", bnLocale)
                .format(new Date(invoice.getDueDate()));

        String totalStr = formatAmountBangla(invoice.getTotalAmount());
        String paidStr = formatAmountBangla(invoice.getPaidAmount());
        String dueAmountStr = formatAmountBangla(invoice.getOutstandingBalance());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "আসসালামু আলাইকুম,\n" +
                        "%s-এর টিউশন ফি সংক্রান্ত রিমাইন্ডার:\n" +
                        "ইনভয়েস আইডি: %s\n" +
                        "বিলিং সময়কাল: %s\n" +
                        "মোট বিল: ৳%s\n" +
                        "পরিশোধিত: ৳%s\n" +
                        "বকেয়া পরিমাণ: ৳%s (পরিশোধের শেষ তারিখ: %s)।\n" +
                        "ধন্যবাদ।",
                student.getFormattedDisplayName(),
                invoice.getInvoiceIdDisplay(),
                invoice.getBillingPeriod(),
                totalStr,
                paidStr,
                dueAmountStr,
                dueStr));

        String tutorBlock = formatTutorBlockBangla(tutor);
        if (!tutorBlock.isEmpty()) {
            sb.append("\n\n").append(tutorBlock);
        }
        return sb.toString();
    }

    public static void showEditableReminderDialog(
            @NonNull Context context,
            @NonNull Student student,
            @NonNull Invoice invoice,
            boolean defaultBangla) {

        Account tutorAccount = resolveTutorAccount(context, invoice);
        TutorContactInfo tutorInfo = extractTutorInfo(tutorAccount, invoice.getAccountId());

        final boolean[] isBangla = {defaultBangla};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(context, 20);
        int padV = dp(context, 16);
        root.setPadding(padH, padV, padH, padV);

        // 1. Header Row: Title + Language Toggle Button (বাংলা / EN)
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(isBangla[0] ? "পেমেন্ট রিমাইন্ডার" : "Payment Reminder");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnSurface));
        headerRow.addView(tvTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton btnLangToggle = new MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnLangToggle.setText("বাংলা / EN");
        btnLangToggle.setAllCaps(false);
        btnLangToggle.setTextSize(12f);
        btnLangToggle.setMinHeight(0);
        btnLangToggle.setMinimumHeight(0);
        btnLangToggle.setMinWidth(0);
        btnLangToggle.setMinimumWidth(0);
        btnLangToggle.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        btnLangToggle.setCornerRadius(dp(context, 8));
        headerRow.addView(btnLangToggle);

        root.addView(headerRow);

        // 2. Compact Scrollable EditText Box
        EditText input = new EditText(context);
        input.setTextSize(13.5f);
        input.setMinLines(5);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setVerticalScrollBarEnabled(true);
        input.setMovementMethod(new ScrollingMovementMethod());
        input.setText(isBangla[0]
                ? buildBanglaTemplate(student, invoice, tutorInfo)
                : buildEnglishTemplate(student, invoice, tutorInfo));

        // Allow smooth scrolling inside EditText without parent intercepting touch
        input.setOnTouchListener((v, event) -> {
            if (v.hasFocus()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(dp(context, 12));
        inputBg.setStroke(dp(context, 1), resolveAttrColor(context, com.google.android.material.R.attr.colorOutlineVariant));
        inputBg.setColor(resolveAttrColor(context, com.google.android.material.R.attr.colorSurfaceVariant));
        input.setBackground(inputBg);
        int boxPad = dp(context, 12);
        input.setPadding(boxPad, boxPad, boxPad, boxPad);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(context, 12);
        inputLp.bottomMargin = dp(context, 14);
        root.addView(input, inputLp);

        // 3. Fixed Horizontal Action Row: SMS + WhatsApp / Share
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton btnSms = new MaterialButton(
                context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnSms.setText("SMS");
        btnSms.setAllCaps(false);
        btnSms.setCornerRadius(dp(context, 10));
        LinearLayout.LayoutParams smsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        smsLp.setMarginEnd(dp(context, 8));
        btnSms.setLayoutParams(smsLp);

        MaterialButton btnShare = new MaterialButton(context);
        btnShare.setText(isBangla[0] ? "WhatsApp / শেয়ার" : "WhatsApp / Share");
        btnShare.setAllCaps(false);
        btnShare.setCornerRadius(dp(context, 10));
        btnShare.setBackgroundTintList(ColorStateList.valueOf(
                resolveAttrColor(context, com.google.android.material.R.attr.colorPrimary)));
        btnShare.setTextColor(resolveAttrColor(context, com.google.android.material.R.attr.colorOnPrimary));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        btnShare.setLayoutParams(shareLp);

        actionRow.addView(btnSms);
        actionRow.addView(btnShare);
        root.addView(actionRow);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(root)
                .create();

        btnLangToggle.setOnClickListener(v -> {
            isBangla[0] = !isBangla[0];
            tvTitle.setText(isBangla[0] ? "পেমেন্ট রিমাইন্ডার" : "Payment Reminder");
            btnShare.setText(isBangla[0] ? "WhatsApp / শেয়ার" : "WhatsApp / Share");
            input.setText(isBangla[0]
                    ? buildBanglaTemplate(student, invoice, tutorInfo)
                    : buildEnglishTemplate(student, invoice, tutorInfo));
            input.setSelection(0);
        });

        btnSms.setOnClickListener(v -> {
            dialog.dismiss();
            String phone = student.getGuardianPhone() != null ? student.getGuardianPhone() : "";
            Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
            smsIntent.putExtra("sms_body", input.getText().toString());
            context.startActivity(smsIntent);
        });

        btnShare.setOnClickListener(v -> {
            dialog.dismiss();
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_TEXT, input.getText().toString());
            context.startActivity(Intent.createChooser(
                    sendIntent,
                    isBangla[0] ? "রিমাইন্ডার শেয়ার করুন" : "Share Reminder"));
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.show();
    }

    @NonNull
    private static String formatTutorBlockEnglish(@Nullable TutorContactInfo tutor) {
        if (tutor == null) return "";
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(tutor.name)) {
            sb.append("Tutor: ").append(tutor.name.trim());
        }
        if (!TextUtils.isEmpty(tutor.mobile)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Mobile: ").append(tutor.mobile.trim());
        }
        if (!TextUtils.isEmpty(tutor.email)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Email: ").append(tutor.email.trim());
        }
        if (!TextUtils.isEmpty(tutor.address)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Address: ").append(tutor.address.trim());
        }
        return sb.toString();
    }

    @NonNull
    private static String formatTutorBlockBangla(@Nullable TutorContactInfo tutor) {
        if (tutor == null) return "";
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(tutor.name)) {
            sb.append("টিউটর: ").append(tutor.name.trim());
        }
        if (!TextUtils.isEmpty(tutor.mobile)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("মোবাইল: ").append(tutor.mobile.trim());
        }
        if (!TextUtils.isEmpty(tutor.email)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("ইমেইল: ").append(tutor.email.trim());
        }
        if (!TextUtils.isEmpty(tutor.address)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("ঠিকানা: ").append(tutor.address.trim());
        }
        return sb.toString();
    }

    @Nullable
    private static Account resolveTutorAccount(@NonNull Context context, @NonNull Invoice invoice) {
        try {
            LocalAccountStore store = new LocalAccountStore(context);
            String accountId = invoice.getAccountId();
            if (TextUtils.isEmpty(accountId)) {
                accountId = new SessionManager(context).getActiveAccountId();
            }
            if (!TextUtils.isEmpty(accountId)) {
                return store.getAccountById(accountId);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @NonNull
    private static TutorContactInfo extractTutorInfo(@Nullable Account account, @Nullable String accountId) {
        TutorContactInfo info = new TutorContactInfo();
        if (account != null) {
            info.name = invokeStringGetter(account, "getFullName", "getName");
            info.mobile = invokeStringGetter(account, "getPhone", "getPhoneNumber", "getMobile");
            info.email = invokeStringGetter(account, "getEmail");
            info.address = invokeStringGetter(account, "getAddress");
        }
        if (TextUtils.isEmpty(info.name)
                && TextUtils.isEmpty(info.mobile)
                && TextUtils.isEmpty(info.email)
                && TextUtils.isEmpty(info.address)) {
            TutorContactInfo dbInfo = loadTutorInfoFromDb(accountId);
            if (dbInfo != null) {
                return dbInfo;
            }
        }
        return info;
    }

    @Nullable
    private static TutorContactInfo loadTutorInfoFromDb(@Nullable String accountId) {
        if (TextUtils.isEmpty(accountId)) return null;
        try {
            SQLiteDatabase db = DatabaseManager.getInstance().openDatabase();
            try (Cursor c = db.query(
                    AccountEntry.TABLE_NAME,
                    null,
                    AccountEntry.COLUMN_ID + " = ?",
                    new String[]{accountId},
                    null, null, null, "1")) {
                if (c.moveToFirst()) {
                    TutorContactInfo info = new TutorContactInfo();
                    info.name = getCursorString(c, "full_name", "name");
                    info.mobile = getCursorString(c, "phone", "mobile", "phone_number");
                    info.email = getCursorString(c, "email");
                    info.address = getCursorString(c, "address");
                    return info;
                }
            } finally {
                DatabaseManager.getInstance().closeDatabase();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static String getCursorString(@NonNull Cursor c, @NonNull String... columnNames) {
        for (String col : columnNames) {
            int idx = c.getColumnIndex(col);
            if (idx != -1 && !c.isNull(idx)) {
                String val = c.getString(idx);
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        return null;
    }

    @Nullable
    private static String invokeStringGetter(@NonNull Object target, @NonNull String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                Object res = m.invoke(target);
                if (res instanceof String && !((String) res).trim().isEmpty()) {
                    return ((String) res).trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @NonNull
    private static String formatAmountBangla(int amountMinor) {
        String ascii = String.format(Locale.US, "%.2f", amountMinor / 100.0);
        StringBuilder sb = new StringBuilder(ascii.length());
        for (int i = 0; i < ascii.length(); i++) {
            char ch = ascii.charAt(i);
            if (ch >= '0' && ch <= '9') {
                sb.append((char) ('\u09E6' + (ch - '0')));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static int dp(@NonNull Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private static int resolveAttrColor(@NonNull Context context, int attr) {
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}

