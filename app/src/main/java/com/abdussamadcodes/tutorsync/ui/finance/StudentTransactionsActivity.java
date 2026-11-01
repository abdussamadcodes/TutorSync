package com.abdussamadcodes.tutorsync.ui.finance;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.account.LocalAccountStore;
import com.abdussamadcodes.tutorsync.model.Account;
import com.abdussamadcodes.tutorsync.model.FeeAgreement;
import com.abdussamadcodes.tutorsync.model.FinancialTransaction;
import com.abdussamadcodes.tutorsync.model.Invoice;
import com.abdussamadcodes.tutorsync.model.RecurringSchedule;
import com.abdussamadcodes.tutorsync.model.Student;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository;
import com.abdussamadcodes.tutorsync.repository.local.AttendanceRepository.UnbilledClassCharge;
import com.abdussamadcodes.tutorsync.repository.local.FeeAgreementRepository;
import com.abdussamadcodes.tutorsync.repository.local.InvoiceRepository;
import com.abdussamadcodes.tutorsync.repository.local.ScheduleRepository;
import com.abdussamadcodes.tutorsync.repository.local.StudentRepository;
import com.abdussamadcodes.tutorsync.repository.local.TransactionRepository;
import com.abdussamadcodes.tutorsync.security.SessionManager;
import com.abdussamadcodes.tutorsync.ui.common.BaseActivity;
import com.abdussamadcodes.tutorsync.util.AppPreferences;
import com.abdussamadcodes.tutorsync.util.FinancialPdfGenerator;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StudentTransactionsActivity extends BaseActivity {

    private static final int TAB_INVOICES = 0;
    private static final int TAB_PAYMENTS = 1;
    private static final int TAB_CLASSES = 2;

    private String studentId;
    private Student currentStudent;
    private SessionManager sessionManager;
    private LocalAccountStore accountStore;
    private StudentRepository studentRepo;
    private ScheduleRepository scheduleRepo;
    private FeeAgreementRepository feeRepo;
    private AttendanceRepository attendanceRepo;
    private InvoiceRepository invoiceRepo;
    private TransactionRepository txnRepo;

    private LinearLayout contentContainer;
    private MaterialToolbar toolbar;
    private int activeTab = TAB_INVOICES;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        studentId = getIntent().getStringExtra("student_id");
        sessionManager = new SessionManager(this);
        accountStore = new LocalAccountStore(this);
        studentRepo = new StudentRepository();
        scheduleRepo = new ScheduleRepository();
        feeRepo = new FeeAgreementRepository();
        attendanceRepo = new AttendanceRepository();
        invoiceRepo = new InvoiceRepository();
        txnRepo = new TransactionRepository();

        CoordinatorLayout root = new CoordinatorLayout(this);
        root.setBackgroundColor(resolveAttrColor(android.R.attr.colorBackground));

        AppBarLayout appBar = new AppBarLayout(this);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.ledger_title));
        TypedValue navIcon = new TypedValue();
        if (getTheme().resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, navIcon, true)) {
            toolbar.setNavigationIcon(navIcon.resourceId);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        appBar.addView(toolbar, new AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(appBar, new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setClipToPadding(false);
        CoordinatorLayout.LayoutParams scrollLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollLp.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        scrollView.setLayoutParams(scrollLp);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(16), dp(12), dp(16), dp(40));
        scrollView.addView(contentContainer);
        root.addView(scrollView);

        setContentView(root);
        applySystemBarInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHistory();
    }

    @NonNull
    private String getActiveTutorName() {
        String accountId = sessionManager.getActiveAccountId();
        if (!accountId.isEmpty()) {
            Account account = accountStore.getAccountById(accountId);
            if (account != null && !account.getFullName().trim().isEmpty()) {
                return account.getFullName().trim();
            }
        }
        return tr("Tutor", "টিউটর");
    }

    private String formatTimeMinutes(int startTimeMinutes) {
        int h = (startTimeMinutes / 60) % 12;
        if (h == 0) h = 12;
        String ampm = startTimeMinutes >= 720 ? "PM" : "AM";
        return String.format(Locale.getDefault(), "%02d:%02d %s", h, startTimeMinutes % 60, ampm);
    }

    private String formatBillingModelLabel(String billingModel) {
        return "PER_CLASS".equals(billingModel)
                ? getString(R.string.fee_type_per_class)
                : getString(R.string.fee_type_monthly);
    }

    private String formatCurrency(int minorUnits) {
        return String.format(Locale.getDefault(), "৳ %.2f", minorUnits / 100.0);
    }

    private String formatCount(int count) {
        return String.format(Locale.getDefault(), "%d", count);
    }

    private boolean isBangla() {
        if (appPreferences != null && AppPreferences.LANG_BANGLA.equals(appPreferences.getLanguageCode())) {
            return true;
        }
        return Locale.getDefault().getLanguage().startsWith("bn");
    }

    @NonNull
    private String tr(@NonNull String en, @NonNull String bn) {
        return isBangla() ? bn : en;
    }

    @NonNull
    private String localizeInvoiceStatus(@Nullable String status) {
        if (status == null) return "";
        if (Invoice.STATUS_PAID.equalsIgnoreCase(status)) {
            return tr("PAID", "পরিশোধিত");
        }
        if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(status)) {
            return tr("PARTIAL", "আংশিক");
        }
        if ("OVERDUE".equalsIgnoreCase(status)) {
            return tr("OVERDUE", "মেয়াদোত্তীর্ণ");
        }
        if ("UNPAID".equalsIgnoreCase(status)) {
            return tr("UNPAID", "অপরিশোধিত");
        }
        return status;
    }

    @NonNull
    private String localizeTxnType(@Nullable String type) {
        if (type == null) return "";
        if (FinancialTransaction.TYPE_REFUND.equalsIgnoreCase(type)) {
            return tr("REFUND", "রিফান্ড");
        }
        if (FinancialTransaction.TYPE_PAYMENT.equalsIgnoreCase(type)) {
            return tr("PAYMENT", "পেমেন্ট");
        }
        return type;
    }

    @NonNull
    private String localizePaymentMethod(@Nullable String method) {
        if (method == null) return "";
        String lower = method.toLowerCase(Locale.US);
        if (lower.contains("bkash")) return tr("bKash", "বিকাশ");
        if (lower.contains("nagad")) return tr("Nagad", "নগদ");
        if (lower.contains("bank")) return tr("Bank Transfer", "ব্যাংক ট্রান্সফার");
        if (lower.contains("cash")) return tr("Cash", "ক্যাশ");
        return method;
    }

    @NonNull
    private String localizeAttendanceStatus(@Nullable String status) {
        if (status == null) return "";
        switch (status.toUpperCase(Locale.US)) {
            case "PRESENT":
                return tr("PRESENT", "উপস্থিত");
            case "ABSENT":
            case "STUDENT_ABSENT":
                return tr("STUDENT ABSENT", "শিক্ষার্থী অনুপস্থিত");
            case "TUTOR_ABSENT":
                return tr("TUTOR ABSENT", "টিউটর অনুপস্থিত");
            case "CANCELLED_BY_STUDENT":
                return tr("CANCELLED BY STUDENT", "শিক্ষার্থী কর্তৃক বাতিল");
            case "CANCELLED_BY_TUTOR":
                return tr("CANCELLED BY TUTOR", "টিউটর কর্তৃক বাতিল");
            case "RESCHEDULED":
                return tr("RESCHEDULED", "পুনঃনির্ধারিত");
            default:
                return status;
        }
    }

    private void refreshHistory() {
        contentContainer.removeAllViews();
        String accountId = sessionManager.getActiveAccountId();
        if (accountId.isEmpty() || studentId == null || studentId.isEmpty()) {
            finish();
            return;
        }
        studentRepo.migrateLegacyStudentIds(accountId);
        currentStudent = studentRepo.getStudentById(accountId, studentId);
        if (currentStudent == null) {
            finish();
            return;
        }
        toolbar.setTitle(currentStudent.getFormattedDisplayName());
        if (currentStudent.getGrade() != null && !currentStudent.getGrade().trim().isEmpty()) {
            toolbar.setSubtitle(tr("Class / Grade: ", "শ্রেণী / ক্লাস: ") + currentStudent.getGrade().trim());
        } else {
            toolbar.setSubtitle(null);
        }

        List<RecurringSchedule> allSchedules = scheduleRepo.getSchedulesForAccount(accountId);
        Map<String, RecurringSchedule> studentScheduleGroups = new LinkedHashMap<>();
        for (RecurringSchedule rs : allSchedules) {
            if (rs.getStudentId().equals(studentId)) {
                String key = rs.getScheduleGroupId() != null ? rs.getScheduleGroupId() : ("TIME-" + rs.getStartTimeMinutes());
                if (!studentScheduleGroups.containsKey(key)) {
                    studentScheduleGroups.put(key, rs);
                }
            }
        }
        FeeAgreement fallbackAgreement = feeRepo.getAgreementForStudent(accountId, studentId);

        List<UnbilledClassCharge> classCharges = attendanceRepo.getPerClassAttendanceChargesForStudent(accountId, studentId);
        int unbilledClassTotalMinor = 0;
        int unbilledClassCount = 0;
        for (UnbilledClassCharge cc : classCharges) {
            if (!cc.isBilled) {
                unbilledClassTotalMinor += cc.feeMinor;
                unbilledClassCount++;
            }
        }

        List<Invoice> allAccountInvoices = invoiceRepo.getFilteredInvoicesForAccount(accountId, "ALL");
        List<Invoice> studentInvoices = new ArrayList<>();
        List<Invoice> studentOpenInvoices = new ArrayList<>();
        int totalInvoicedMinor = 0;
        int totalPaidMinor = 0;
        int totalInvoiceDueMinor = 0;

        for (Invoice inv : allAccountInvoices) {
            if (inv.getStudentId().equals(studentId)) {
                studentInvoices.add(inv);
                totalInvoicedMinor += inv.getTotalAmount();
                totalPaidMinor += inv.getPaidAmount();
                totalInvoiceDueMinor += inv.getOutstandingBalance();
                if (inv.getOutstandingBalance() > 0) {
                    studentOpenInvoices.add(inv);
                }
            }
        }
        Collections.reverse(studentOpenInvoices);

        List<FinancialTransaction> txns = txnRepo.getTransactionsForStudent(accountId, studentId);

        // 1. HERO FINANCIAL OVERVIEW & PRIMARY ACTIONS CARD
        buildHeroOverviewCard(
                accountId,
                totalInvoiceDueMinor,
                unbilledClassTotalMinor,
                unbilledClassCount,
                totalInvoicedMinor,
                totalPaidMinor,
                studentOpenInvoices
        );

        // 2. COMPACT FEE PLAN & SCHEDULE RATES CARD
        buildFeePlanCard(studentScheduleGroups, fallbackAgreement);

        // 3. SEGMENTED TABS FOR INVOICES, PAYMENTS, AND CLASS CHARGES
        buildLedgerTabsAndContent(
                accountId,
                studentInvoices,
                txns,
                classCharges,
                unbilledClassCount,
                unbilledClassTotalMinor
        );
    }

    private void buildHeroOverviewCard(
            String accountId,
            int totalInvoiceDueMinor,
            int unbilledClassTotalMinor,
            int unbilledClassCount,
            int totalInvoicedMinor,
            int totalPaidMinor,
            List<Invoice> studentOpenInvoices) {

        MaterialCardView heroCard = createSectionCard();
        LinearLayout body = createCardBody(heroCard);

        // Header Row: Label + Status Pill
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(tr("OUTSTANDING INVOICE DUE", "বকেয়া ইনভয়েস ডিউ"));
        tvLabel.setTextSize(11f);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLetterSpacing(0.06f);
        tvLabel.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topRow.addView(tvLabel, labelLp);

        String statusText;
        int badgeBgColor;
        int badgeTextColor;
        if (totalInvoiceDueMinor > 0) {
            int openCount = studentOpenInvoices.size();
            statusText = tr(
                    openCount + " OPEN INVOICE" + (openCount > 1 ? "S" : ""),
                    formatCount(openCount) + "টি বকেয়া ইনভয়েস"
            );
            badgeBgColor = Color.parseColor("#33FF9800");
            badgeTextColor = Color.parseColor("#FFB74D");
        } else if (unbilledClassCount > 0) {
            statusText = tr(
                    unbilledClassCount + " UNBILLED",
                    formatCount(unbilledClassCount) + "টি অবিলকৃত"
            );
            badgeBgColor = Color.parseColor("#3329B6F6");
            badgeTextColor = Color.parseColor("#4FC3F7");
        } else {
            statusText = tr("ALL SETTLED", "সব পরিশোধিত");
            badgeBgColor = Color.parseColor("#334CAF50");
            badgeTextColor = Color.parseColor("#81C784");
        }
        topRow.addView(createPillBadge(statusText, badgeBgColor, badgeTextColor));
        body.addView(topRow);

        // Big Hero Amount
        TextView tvHeroDue = new TextView(this);
        tvHeroDue.setText(formatCurrency(totalInvoiceDueMinor));
        tvHeroDue.setTextSize(30f);
        tvHeroDue.setTypeface(null, Typeface.BOLD);
        tvHeroDue.setTextColor(totalInvoiceDueMinor > 0
                ? Color.parseColor("#FFB74D")
                : resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
        tvHeroDue.setPadding(0, dp(4), 0, dp(14));
        body.addView(tvHeroDue);

        // 3-Column KPI Metric Grid
        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.setWeightSum(3f);

        metricsRow.addView(createMetricTile(
                tr("Unbilled (" + unbilledClassCount + ")", "অবিলকৃত (" + formatCount(unbilledClassCount) + ")"),
                formatCurrency(unbilledClassTotalMinor),
                unbilledClassCount > 0 ? Color.parseColor("#4FC3F7") : resolveAttrColor(com.google.android.material.R.attr.colorOnSurface)
        ));
        addHorizontalSpacer(metricsRow, dp(8));
        metricsRow.addView(createMetricTile(
                tr("Total Billed", "মোট বিল"),
                formatCurrency(totalInvoicedMinor),
                resolveAttrColor(com.google.android.material.R.attr.colorOnSurface)
        ));
        addHorizontalSpacer(metricsRow, dp(8));
        metricsRow.addView(createMetricTile(
                tr("Total Paid", "মোট পরিশোধিত"),
                formatCurrency(totalPaidMinor),
                Color.parseColor("#81C784")
        ));
        body.addView(metricsRow);

        // Quick Action Buttons Row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowLp.topMargin = dp(14);
        actionRow.setLayoutParams(actionRowLp);

        if (totalInvoiceDueMinor > 0) {
            MaterialButton btnPay = new MaterialButton(this);
            btnPay.setText(tr("Record Payment", "পেমেন্ট রেকর্ড করুন"));
            btnPay.setAllCaps(false);
            btnPay.setCornerRadius(dp(10));
            LinearLayout.LayoutParams payLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            btnPay.setLayoutParams(payLp);
            btnPay.setOnClickListener(v -> showMultiInvoicePaymentDialog(studentOpenInvoices, totalInvoiceDueMinor));
            actionRow.addView(btnPay);
        }

        MaterialButton btnInvoice = new MaterialButton(
                this,
                null,
                totalInvoiceDueMinor > 0
                        ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                        : com.google.android.material.R.attr.materialButtonStyle
        );
        btnInvoice.setAllCaps(false);
        btnInvoice.setCornerRadius(dp(10));
        btnInvoice.setText(unbilledClassCount > 0
                ? tr(
                String.format(Locale.getDefault(), "Bill Unbilled (৳%.0f)", unbilledClassTotalMinor / 100.0),
                String.format(Locale.getDefault(), "অবিলকৃত বিল করুন (৳%.0f)", unbilledClassTotalMinor / 100.0))
                : getString(R.string.ledger_btn_generate_due_invoice));
        LinearLayout.LayoutParams invLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (totalInvoiceDueMinor > 0) {
            invLp.leftMargin = dp(8);
        }
        btnInvoice.setLayoutParams(invLp);
        btnInvoice.setOnClickListener(v -> {
            int created = invoiceRepo.generateDueInvoicesForStudent(accountId, studentId);
            if (created > 0) {
                activeTab = TAB_INVOICES;
                showMessage(contentContainer, getString(R.string.invoice_generated_success));
                refreshHistory();
            } else {
                showMessage(contentContainer, getString(R.string.invoice_none_needed));
            }
        });
        actionRow.addView(btnInvoice);

        body.addView(actionRow);
        contentContainer.addView(heroCard);
    }

    private void buildFeePlanCard(
            Map<String, RecurringSchedule> studentScheduleGroups,
            @Nullable FeeAgreement fallbackAgreement) {

        MaterialCardView feeCard = createSectionCard();
        LinearLayout feeCol = createCardBody(feeCard);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(getString(R.string.ledger_fee_rate_header));
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        headerRow.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton btnEditFee = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        btnEditFee.setText(tr("Edit Rate", "রেট এডিট করুন"));
        btnEditFee.setAllCaps(false);
        btnEditFee.setTextSize(13f);
        btnEditFee.setMinimumHeight(0);
        btnEditFee.setMinimumWidth(0);
        btnEditFee.setPadding(dp(10), dp(4), dp(10), dp(4));
        btnEditFee.setOnClickListener(v -> showFeeRateBottomSheet(studentScheduleGroups, fallbackAgreement, null));
        headerRow.addView(btnEditFee);
        feeCol.addView(headerRow);

        if (!studentScheduleGroups.isEmpty()) {
            for (Map.Entry<String, RecurringSchedule> entry : studentScheduleGroups.entrySet()) {
                final String groupKey = entry.getKey();
                RecurringSchedule rs = entry.getValue();
                String timeStr = formatTimeMinutes(rs.getStartTimeMinutes());
                int feeVal = rs.getFeeAmountMinor();
                String modelCode = rs.getBillingModel();
                if (feeVal <= 0 && fallbackAgreement != null) {
                    feeVal = fallbackAgreement.getAmountMinorUnits();
                    modelCode = fallbackAgreement.getFeeType();
                }
                String modelStr = formatBillingModelLabel(modelCode);

                LinearLayout row = createSubtleRowContainer();
                row.setOnClickListener(v -> showFeeRateBottomSheet(studentScheduleGroups, fallbackAgreement, groupKey));

                LinearLayout leftCol = new LinearLayout(this);
                leftCol.setOrientation(LinearLayout.VERTICAL);

                TextView tvSchedule = new TextView(this);
                tvSchedule.setText(tr("Schedule · ", "রুটিন · ") + timeStr);
                tvSchedule.setTextSize(14f);
                tvSchedule.setTypeface(null, Typeface.BOLD);
                tvSchedule.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
                leftCol.addView(tvSchedule);

                TextView tvModel = new TextView(this);
                tvModel.setText(tr(
                        "Billing Model: " + modelStr + " (Tap to edit)",
                        "বিলিং মডেল: " + modelStr + " (এডিট করতে ট্যাপ করুন)"));
                tvModel.setTextSize(12f);
                tvModel.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
                leftCol.addView(tvModel);

                row.addView(leftCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvRate = new TextView(this);
                tvRate.setText(formatCurrency(feeVal));
                tvRate.setTextSize(15f);
                tvRate.setTypeface(null, Typeface.BOLD);
                tvRate.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
                row.addView(tvRate);

                feeCol.addView(row);
            }
        } else if (fallbackAgreement != null) {
            String modelStr = formatBillingModelLabel(fallbackAgreement.getFeeType());
            LinearLayout row = createSubtleRowContainer();
            row.setOnClickListener(v -> showFeeRateBottomSheet(studentScheduleGroups, fallbackAgreement, null));

            TextView tvLeft = new TextView(this);
            tvLeft.setText(tr("Default Plan (" + modelStr + ")", "ডিফল্ট প্ল্যান (" + modelStr + ")"));
            tvLeft.setTextSize(14f);
            tvLeft.setTypeface(null, Typeface.BOLD);
            tvLeft.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
            row.addView(tvLeft, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvRight = new TextView(this);
            tvRight.setText(formatCurrency(fallbackAgreement.getAmountMinorUnits()));
            tvRight.setTextSize(15f);
            tvRight.setTypeface(null, Typeface.BOLD);
            tvRight.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
            row.addView(tvRight);

            feeCol.addView(row);
        } else {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(getString(R.string.ledger_no_fee_configured));
            tvEmpty.setTextSize(13f);
            tvEmpty.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            tvEmpty.setPadding(0, dp(4), 0, dp(4));
            feeCol.addView(tvEmpty);
        }

        contentContainer.addView(feeCard);
    }

    private void buildLedgerTabsAndContent(
            String accountId,
            List<Invoice> studentInvoices,
            List<FinancialTransaction> txns,
            List<UnbilledClassCharge> classCharges,
            int unbilledClassCount,
            int unbilledClassTotalMinor) {

        // Segmented Filter Bar
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setWeightSum(3f);
        tabBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable tabBarBg = new GradientDrawable();
        tabBarBg.setColor(resolveAttrColor(com.google.android.material.R.attr.colorSurface));
        tabBarBg.setCornerRadius(dp(12));
        tabBarBg.setStroke(dp(1), resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant));
        tabBar.setBackground(tabBarBg);

        LinearLayout.LayoutParams tabBarLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabBarLp.bottomMargin = dp(12);
        tabBar.setLayoutParams(tabBarLp);

        tabBar.addView(createTabButton(
                tr("Invoices (" + studentInvoices.size() + ")", "ইনভয়েস (" + formatCount(studentInvoices.size()) + ")"),
                TAB_INVOICES));
        tabBar.addView(createTabButton(
                tr("Payments (" + txns.size() + ")", "পেমেন্ট (" + formatCount(txns.size()) + ")"),
                TAB_PAYMENTS));
        tabBar.addView(createTabButton(
                tr("Classes (" + classCharges.size() + ")", "ক্লাস (" + formatCount(classCharges.size()) + ")"),
                TAB_CLASSES));
        contentContainer.addView(tabBar);

        if (activeTab == TAB_INVOICES) {
            buildInvoicesListSection(studentInvoices);
        } else if (activeTab == TAB_PAYMENTS) {
            buildPaymentsListSection(txns);
        } else {
            buildClassesListSection(accountId, classCharges, unbilledClassCount, unbilledClassTotalMinor);
        }
    }

    private View createTabButton(String title, int tabIndex) {
        boolean selected = (activeTab == tabIndex);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(13f);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setPadding(dp(8), dp(10), dp(8), dp(10));

        if (selected) {
            GradientDrawable selBg = new GradientDrawable();
            selBg.setColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
            selBg.setCornerRadius(dp(9));
            tv.setBackground(selBg);
            tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnPrimary));
        } else {
            tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            if (activeTab != tabIndex) {
                activeTab = tabIndex;
                refreshHistory();
            }
        });
        return tv;
    }

    private void buildInvoicesListSection(List<Invoice> studentInvoices) {
        if (studentInvoices.isEmpty()) {
            contentContainer.addView(createEmptyStateCard(getString(R.string.invoice_empty)));
            return;
        }

        for (Invoice inv : studentInvoices) {
            MaterialCardView card = createSectionCard();
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, InvoiceDetailActivity.class);
                intent.putExtra("invoice_id", inv.getId());
                startActivity(intent);
            });

            LinearLayout col = createCardBody(card);

            // Row 1: Invoice ID + Status Pill
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvId = new TextView(this);
            tvId.setText(inv.getInvoiceIdDisplay());
            tvId.setTextSize(15f);
            tvId.setTypeface(null, Typeface.BOLD);
            tvId.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
            topRow.addView(tvId, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            int badgeBg;
            int badgeFg;
            if (Invoice.STATUS_PAID.equalsIgnoreCase(inv.getStatus())) {
                badgeBg = Color.parseColor("#334CAF50");
                badgeFg = Color.parseColor("#81C784");
            } else if (Invoice.STATUS_PARTIAL.equalsIgnoreCase(inv.getStatus())) {
                badgeBg = Color.parseColor("#33FF9800");
                badgeFg = Color.parseColor("#FFB74D");
            } else {
                badgeBg = Color.parseColor("#33EF5350");
                badgeFg = Color.parseColor("#E57373");
            }
            topRow.addView(createPillBadge(localizeInvoiceStatus(inv.getStatus()), badgeBg, badgeFg));
            col.addView(topRow);

            // Row 2: Billing period + Tap cue
            LinearLayout subRow = new LinearLayout(this);
            subRow.setOrientation(LinearLayout.HORIZONTAL);
            subRow.setPadding(0, dp(4), 0, dp(10));

            TextView tvPeriod = new TextView(this);
            tvPeriod.setText(tr("Billing Period: ", "বিলিং সময়কাল: ") + inv.getBillingPeriod());
            tvPeriod.setTextSize(12f);
            tvPeriod.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            subRow.addView(tvPeriod, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvOpen = new TextView(this);
            tvOpen.setText(tr("View Invoice ›", "ইনভয়েস দেখুন ›"));
            tvOpen.setTextSize(12f);
            tvOpen.setTypeface(null, Typeface.BOLD);
            tvOpen.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
            subRow.addView(tvOpen);
            col.addView(subRow);

            // Row 3: 3-Column Breakdown (Total, Paid, Due)
            LinearLayout amountsRow = new LinearLayout(this);
            amountsRow.setOrientation(LinearLayout.HORIZONTAL);
            amountsRow.setWeightSum(3f);

            amountsRow.addView(createMetricTile(
                    tr("Total", "মোট"),
                    formatCurrency(inv.getTotalAmount()),
                    resolveAttrColor(com.google.android.material.R.attr.colorOnSurface)
            ));
            addHorizontalSpacer(amountsRow, dp(6));
            amountsRow.addView(createMetricTile(
                    tr("Paid", "পরিশোধিত"),
                    formatCurrency(inv.getPaidAmount()),
                    Color.parseColor("#81C784")
            ));
            addHorizontalSpacer(amountsRow, dp(6));
            amountsRow.addView(createMetricTile(
                    tr("Balance Due", "বকেয়া"),
                    formatCurrency(inv.getOutstandingBalance()),
                    inv.getOutstandingBalance() > 0
                            ? Color.parseColor("#FFB74D")
                            : resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            ));
            col.addView(amountsRow);

            contentContainer.addView(card);
        }
    }

    private void buildPaymentsListSection(List<FinancialTransaction> txns) {
        if (txns.isEmpty()) {
            contentContainer.addView(createEmptyStateCard(getString(R.string.invoice_no_payments)));
            return;
        }

        SimpleDateFormat dtf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        for (FinancialTransaction txn : txns) {
            MaterialCardView card = createSectionCard();
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> showTransactionOptions(txn));

            LinearLayout col = createCardBody(card);

            boolean isRefund = FinancialTransaction.TYPE_REFUND.equalsIgnoreCase(txn.getType());

            // Top row: Txn ID + Amount
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTxnId = new TextView(this);
            tvTxnId.setText(txn.getTxnIdDisplay());
            tvTxnId.setTextSize(15f);
            tvTxnId.setTypeface(null, Typeface.BOLD);
            tvTxnId.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
            topRow.addView(tvTxnId, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvAmt = new TextView(this);
            tvAmt.setText((isRefund ? "- " : "+ ") + formatCurrency(txn.getAmountMinor()));
            tvAmt.setTextSize(16f);
            tvAmt.setTypeface(null, Typeface.BOLD);
            tvAmt.setTextColor(isRefund ? Color.parseColor("#E57373") : Color.parseColor("#81C784"));
            topRow.addView(tvAmt);
            col.addView(topRow);

            // Middle row: Date + Method & Type badge
            LinearLayout midRow = new LinearLayout(this);
            midRow.setOrientation(LinearLayout.HORIZONTAL);
            midRow.setGravity(Gravity.CENTER_VERTICAL);
            midRow.setPadding(0, dp(6), 0, dp(6));

            TextView tvDate = new TextView(this);
            tvDate.setText(dtf.format(new Date(txn.getTimestamp())) + " · " + localizePaymentMethod(txn.getMethod()));
            tvDate.setTextSize(12f);
            tvDate.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            midRow.addView(tvDate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            midRow.addView(createPillBadge(
                    localizeTxnType(txn.getType()),
                    isRefund ? Color.parseColor("#33EF5350") : Color.parseColor("#334CAF50"),
                    isRefund ? Color.parseColor("#E57373") : Color.parseColor("#81C784")
            ));
            col.addView(midRow);

            // Bottom row: Linked Invoices + Receipt / Refund action cue
            String invoicesStr = txn.getRelatedInvoiceDisplayIds().isEmpty()
                    ? tr("Unallocated", "অবন্টিত")
                    : String.join(", ", txn.getRelatedInvoiceDisplayIds());

            LinearLayout botRow = new LinearLayout(this);
            botRow.setOrientation(LinearLayout.HORIZONTAL);
            botRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvInv = new TextView(this);
            tvInv.setText(tr("Invoice: ", "ইনভয়েস: ") + invoicesStr);
            tvInv.setTextSize(12f);
            tvInv.setEllipsize(TextUtils.TruncateAt.END);
            tvInv.setSingleLine(true);
            tvInv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            botRow.addView(tvInv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvAction = new TextView(this);
            tvAction.setText(tr("Receipt / Refund ›", "রসিদ / রিফান্ড ›"));
            tvAction.setTextSize(12f);
            tvAction.setTypeface(null, Typeface.BOLD);
            tvAction.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
            botRow.addView(tvAction);
            col.addView(botRow);

            contentContainer.addView(card);
        }
    }

    private void buildClassesListSection(
            String accountId,
            List<UnbilledClassCharge> classCharges,
            int unbilledClassCount,
            int unbilledClassTotalMinor) {

        MaterialCardView card = createSectionCard();
        LinearLayout col = createCardBody(card);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(8));

        TextView tvHeader = new TextView(this);
        tvHeader.setText(getString(R.string.ledger_attended_classes_header));
        tvHeader.setTextSize(15f);
        tvHeader.setTypeface(null, Typeface.BOLD);
        tvHeader.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        headerRow.addView(tvHeader, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (unbilledClassCount > 0) {
            headerRow.addView(createPillBadge(
                    tr(
                            unbilledClassCount + " Unbilled (" + formatCurrency(unbilledClassTotalMinor) + ")",
                            formatCount(unbilledClassCount) + "টি অবিলকৃত (" + formatCurrency(unbilledClassTotalMinor) + ")"),
                    Color.parseColor("#3329B6F6"),
                    Color.parseColor("#4FC3F7")
            ));
        }
        col.addView(headerRow);

        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        if (classCharges.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(getString(R.string.ledger_no_per_class_sessions));
            tvEmpty.setTextSize(13f);
            tvEmpty.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            tvEmpty.setPadding(0, dp(8), 0, dp(8));
            col.addView(tvEmpty);
        } else {
            for (UnbilledClassCharge cc : classCharges) {
                LinearLayout row = createSubtleRowContainer();

                LinearLayout leftCol = new LinearLayout(this);
                leftCol.setOrientation(LinearLayout.VERTICAL);

                TextView tvDate = new TextView(this);
                tvDate.setText(df.format(new Date(cc.dateMillis)) + " · " + formatTimeMinutes(cc.startTimeMinutes));
                tvDate.setTextSize(14f);
                tvDate.setTypeface(null, Typeface.BOLD);
                tvDate.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
                leftCol.addView(tvDate);

                TextView tvStatus = new TextView(this);
                tvStatus.setText(tr("Attendance: ", "উপস্থিতি: ") + localizeAttendanceStatus(cc.status));
                tvStatus.setTextSize(12f);
                tvStatus.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
                leftCol.addView(tvStatus);

                row.addView(leftCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout rightCol = new LinearLayout(this);
                rightCol.setOrientation(LinearLayout.VERTICAL);
                rightCol.setGravity(Gravity.END);

                TextView tvFee = new TextView(this);
                tvFee.setText(formatCurrency(cc.feeMinor));
                tvFee.setTextSize(14f);
                tvFee.setTypeface(null, Typeface.BOLD);
                tvFee.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurface));
                rightCol.addView(tvFee);

                View badge = createPillBadge(
                        cc.isBilled ? tr("BILLED", "বিলকৃত") : tr("UNBILLED", "অবিলকৃত"),
                        cc.isBilled ? Color.parseColor("#334CAF50") : Color.parseColor("#33FF9800"),
                        cc.isBilled ? Color.parseColor("#81C784") : Color.parseColor("#FFB74D")
                );
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeLp.topMargin = dp(4);
                rightCol.addView(badge, badgeLp);

                row.addView(rightCol);
                col.addView(row);
            }
        }

        if (unbilledClassCount > 0) {
            MaterialButton btnGen = new MaterialButton(this);
            btnGen.setAllCaps(false);
            btnGen.setText(tr(
                    String.format(Locale.getDefault(),
                            "Generate Invoice for %d Unbilled Class(es) (%s)",
                            unbilledClassCount, formatCurrency(unbilledClassTotalMinor)),
                    String.format(Locale.getDefault(),
                            "%dটি অবিলকৃত ক্লাসের ইনভয়েস তৈরি করুন (%s)",
                            unbilledClassCount, formatCurrency(unbilledClassTotalMinor))));
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.topMargin = dp(10);
            btnGen.setLayoutParams(btnLp);
            btnGen.setOnClickListener(v -> {
                int created = invoiceRepo.generateDueInvoicesForStudent(accountId, studentId);
                if (created > 0) {
                    activeTab = TAB_INVOICES;
                    showMessage(contentContainer, getString(R.string.invoice_generated_success));
                    refreshHistory();
                } else {
                    showMessage(contentContainer, getString(R.string.invoice_none_needed));
                }
            });
            col.addView(btnGen);
        }

        contentContainer.addView(card);
    }

    private void showFeeRateBottomSheet(
            Map<String, RecurringSchedule> scheduleGroups,
            @Nullable FeeAgreement existing,
            @Nullable String preselectedGroupKey) {

        BottomSheetDialog feeDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_fee_agreement, null);
        feeDialog.setContentView(view);

        View layoutScheduleSelector = view.findViewById(R.id.layoutScheduleSelector);
        ChipGroup chipGroupSchedules = view.findViewById(R.id.chipGroupSchedules);
        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.toggleGroupFeeType);
        TextInputEditText etAmount = view.findViewById(R.id.etFeeAmount);

        final List<RecurringSchedule> scheduleList = new ArrayList<>(scheduleGroups.values());
        final int[] selectedIndex = {0};

        if (!scheduleList.isEmpty()) {
            layoutScheduleSelector.setVisibility(View.VISIBLE);
            chipGroupSchedules.removeAllViews();

            for (int i = 0; i < scheduleList.size(); i++) {
                RecurringSchedule rs = scheduleList.get(i);
                String key = rs.getScheduleGroupId() != null ? rs.getScheduleGroupId() : ("TIME-" + rs.getStartTimeMinutes());
                if (preselectedGroupKey != null && preselectedGroupKey.equals(key)) {
                    selectedIndex[0] = i;
                }

                Chip chip = new Chip(this);
                chip.setId(View.generateViewId());
                chip.setCheckable(true);
                chip.setClickable(true);
                int feeVal = rs.getFeeAmountMinor();
                if (feeVal <= 0 && existing != null) feeVal = existing.getAmountMinorUnits();
                chip.setText(String.format(Locale.getDefault(), "%s (৳%.0f)",
                        formatTimeMinutes(rs.getStartTimeMinutes()), feeVal / 100.0));

                final int idx = i;
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedIndex[0] = idx;
                        RecurringSchedule chosen = scheduleList.get(idx);
                        String model = chosen.getBillingModel();
                        int minor = chosen.getFeeAmountMinor();
                        if (minor <= 0 && existing != null) {
                            model = existing.getFeeType();
                            minor = existing.getAmountMinorUnits();
                        }
                        if (FeeAgreement.TYPE_PER_CLASS.equals(model)) {
                            toggleGroup.check(R.id.btnFeePerClass);
                        } else {
                            toggleGroup.check(R.id.btnFeeMonthly);
                        }
                        etAmount.setText(minor > 0 ? String.valueOf(minor / 100) : "");
                    }
                });

                chipGroupSchedules.addView(chip);
                if (i == selectedIndex[0]) {
                    chip.setChecked(true);
                }
            }
        } else {
            layoutScheduleSelector.setVisibility(View.GONE);
            if (existing != null) {
                if (FeeAgreement.TYPE_PER_CLASS.equals(existing.getFeeType())) {
                    toggleGroup.check(R.id.btnFeePerClass);
                } else {
                    toggleGroup.check(R.id.btnFeeMonthly);
                }
                etAmount.setText(String.valueOf(existing.getAmountMinorUnits() / 100));
            } else {
                toggleGroup.check(R.id.btnFeeMonthly);
            }
        }

        view.findViewById(R.id.btnSaveFee).setOnClickListener(v -> {
            String amtStr = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
            if (amtStr.isEmpty()) {
                etAmount.setError(getString(R.string.auth_err_empty));
                return;
            }
            int amountMinor;
            try {
                amountMinor = (int) Math.round(Double.parseDouble(amtStr) * 100);
            } catch (NumberFormatException e) {
                etAmount.setError(getString(R.string.invoice_err_invalid_amount));
                return;
            }

            String type = toggleGroup.getCheckedButtonId() == R.id.btnFeePerClass
                    ? FeeAgreement.TYPE_PER_CLASS : FeeAgreement.TYPE_MONTHLY;
            String accountId = sessionManager.getActiveAccountId();

            if (!scheduleList.isEmpty()) {
                RecurringSchedule chosenSchedule = scheduleList.get(selectedIndex[0]);
                int anchorDay = chosenSchedule.getAnchorDay() > 0
                        ? chosenSchedule.getAnchorDay()
                        : Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                if (chosenSchedule.getScheduleGroupId() != null) {
                    scheduleRepo.updateScheduleGroupFee(
                            accountId, studentId, chosenSchedule.getScheduleGroupId(),
                            type, amountMinor, anchorDay);
                }
            } else {
                FeeAgreement agreement = new FeeAgreement(
                        existing != null ? existing.getId() : UUID.randomUUID().toString(),
                        accountId, studentId, type, amountMinor, System.currentTimeMillis()
                );
                feeRepo.saveAgreement(agreement);
            }

            feeDialog.dismiss();
            showMessage(contentContainer, getString(R.string.fee_saved_success));
            refreshHistory();
        });

        feeDialog.show();
    }

    private void showMultiInvoicePaymentDialog(List<Invoice> openInvoicesChronological, int totalDueMinor) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, dp(12), pad, dp(4));

        TextView tvSummary = new TextView(this);
        tvSummary.setText(tr(
                String.format(Locale.getDefault(),
                        "Open invoices: %d  ·  Total Due: %s",
                        openInvoicesChronological.size(), formatCurrency(totalDueMinor)),
                String.format(Locale.getDefault(),
                        "বকেয়া ইনভয়েস: %dটি  ·  মোট বকেয়া: %s",
                        openInvoicesChronological.size(), formatCurrency(totalDueMinor))));
        tvSummary.setTextSize(13f);
        tvSummary.setTypeface(null, Typeface.BOLD);
        tvSummary.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorPrimary));
        tvSummary.setPadding(0, 0, 0, dp(10));
        layout.addView(tvSummary);

        EditText etAmount = new EditText(this);
        etAmount.setHint(getString(R.string.invoice_payment_amount));
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(String.format(Locale.US, "%.2f", totalDueMinor / 100.0));
        layout.addView(etAmount);

        TextView tvMethodLabel = new TextView(this);
        tvMethodLabel.setText(tr("Payment Method", "পেমেন্টের মাধ্যম"));
        tvMethodLabel.setTextSize(12f);
        tvMethodLabel.setPadding(dp(4), dp(10), 0, dp(2));
        tvMethodLabel.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        layout.addView(tvMethodLabel);

        Spinner spMethod = new Spinner(this);
        final String[] methodsCanonical = {"Cash", "bKash", "Nagad", "Bank Transfer"};
        String[] methodsDisplay = isBangla()
                ? new String[]{"ক্যাশ (Cash)", "বিকাশ (bKash)", "নগদ (Nagad)", "ব্যাংক ট্রান্সফার"}
                : methodsCanonical;
        spMethod.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, methodsDisplay));
        layout.addView(spMethod);

        EditText etRef = new EditText(this);
        etRef.setHint(getString(R.string.invoice_ref_hint));
        layout.addView(etRef);

        EditText etNotes = new EditText(this);
        etNotes.setHint(getString(R.string.invoice_notes_hint));
        layout.addView(etNotes);

        new MaterialAlertDialogBuilder(this)
                .setTitle(tr("Record Payment", "পেমেন্ট রেকর্ড করুন"))
                .setView(layout)
                .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                    try {
                        double bdt = Double.parseDouble(etAmount.getText().toString().trim());
                        int amountMinor = (int) Math.round(bdt * 100);
                        if (amountMinor <= 0 || amountMinor > totalDueMinor) {
                            showMessage(contentContainer, getString(R.string.invoice_err_invalid_amount));
                            return;
                        }
                        int methodIdx = spMethod.getSelectedItemPosition();
                        String selectedMethod = (methodIdx >= 0 && methodIdx < methodsCanonical.length)
                                ? methodsCanonical[methodIdx]
                                : "Cash";
                        FinancialTransaction created = txnRepo.recordStudentLumpSumPaymentFIFO(
                                sessionManager.getActiveAccountId(),
                                studentId,
                                amountMinor,
                                selectedMethod,
                                etRef.getText().toString().trim(),
                                etNotes.getText().toString().trim(),
                                openInvoicesChronological
                        );
                        if (created != null) {
                            activeTab = TAB_PAYMENTS;
                            showMessage(contentContainer, getString(R.string.invoice_payment_saved_fmt, created.getTxnIdDisplay()));
                            refreshHistory();
                        }
                    } catch (NumberFormatException e) {
                        showMessage(contentContainer, getString(R.string.invoice_err_invalid_amount));
                    }
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private void showTransactionOptions(FinancialTransaction txn) {
        boolean isPayment = FinancialTransaction.TYPE_PAYMENT.equals(txn.getType());
        String[] items = isPayment
                ? new String[]{getString(R.string.invoice_share_receipt_pdf), getString(R.string.invoice_issue_refund)}
                : new String[]{getString(R.string.invoice_share_receipt_pdf)};

        new MaterialAlertDialogBuilder(this)
                .setTitle(txn.getTxnIdDisplay() + " (" + formatCurrency(txn.getAmountMinor()) + ")")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        try {
                            File pdf = FinancialPdfGenerator.generatePaymentReceiptPdf(
                                    this, getActiveTutorName(), currentStudent, txn);
                            FinancialPdfGenerator.sharePdf(this, pdf, getString(R.string.invoice_share_receipt_pdf));
                        } catch (Exception e) {
                            showMessage(contentContainer, e.getMessage());
                        }
                    } else if (which == 1 && isPayment) {
                        showRefundDialog(txn);
                    }
                })
                .show();
    }

    private void showRefundDialog(FinancialTransaction txn) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, dp(12), pad, dp(4));

        EditText etRefundAmt = new EditText(this);
        etRefundAmt.setHint(tr("Refund Amount (৳)", "রিফান্ডের পরিমাণ (৳)"));
        etRefundAmt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etRefundAmt.setText(String.format(Locale.US, "%.2f", txn.getAmountMinor() / 100.0));
        layout.addView(etRefundAmt);

        EditText etReason = new EditText(this);
        etReason.setHint(tr("Reason for refund", "রিফান্ডের কারণ"));
        etReason.setText(tr("Tutor refund", "টিউটর রিফান্ড"));
        layout.addView(etReason);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.invoice_issue_refund))
                .setView(layout)
                .setPositiveButton(getString(R.string.action_save), (d, w) -> {
                    try {
                        double bdt = Double.parseDouble(etRefundAmt.getText().toString().trim());
                        int refundMinor = (int) Math.round(bdt * 100);
                        String reason = etReason.getText().toString().trim();
                        if (reason.isEmpty()) reason = "Tutor refund";

                        boolean ok = txnRepo.recordRefundForTransaction(
                                sessionManager.getActiveAccountId(), txn.getId(), refundMinor, reason);
                        if (ok) {
                            showMessage(contentContainer, getString(R.string.invoice_refund_saved_msg));
                            refreshHistory();
                        } else {
                            showMessage(contentContainer, getString(R.string.invoice_err_invalid_amount));
                        }
                    } catch (NumberFormatException e) {
                        showMessage(contentContainer, getString(R.string.invoice_err_invalid_amount));
                    }
                })
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show();
    }

    private View createMetricTile(String label, String value, int valueColor) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#18FFFFFF"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant));
        tile.setBackground(bg);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(11f);
        tvLabel.setSingleLine(true);
        tvLabel.setEllipsize(TextUtils.TruncateAt.END);
        tvLabel.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        tile.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(14f);
        tvValue.setTypeface(null, Typeface.BOLD);
        tvValue.setSingleLine(true);
        tvValue.setEllipsize(TextUtils.TruncateAt.END);
        tvValue.setTextColor(valueColor);
        tvValue.setPadding(0, dp(3), 0, 0);
        tile.addView(tvValue);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tile.setLayoutParams(lp);
        return tile;
    }

    private LinearLayout createSubtleRowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#14FFFFFF"));
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private View createPillBadge(String text, int bgColor, int textColor) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(textColor);
        badge.setPadding(dp(10), dp(3), dp(10), dp(3));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(50));
        badge.setBackground(bg);
        return badge;
    }

    private MaterialCardView createEmptyStateCard(String message) {
        MaterialCardView card = createSectionCard();
        LinearLayout col = createCardBody(card);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(14f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(resolveAttrColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        col.addView(tv);
        return card;
    }

    private void addHorizontalSpacer(LinearLayout parent, int widthPx) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(widthPx, dp(1)));
        parent.addView(spacer);
    }

    private MaterialCardView createSectionCard() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        card.setRadius(dp(14));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ColorStateList.valueOf(resolveAttrColor(com.google.android.material.R.attr.colorOutlineVariant)));
        card.setCardBackgroundColor(resolveAttrColor(com.google.android.material.R.attr.colorSurface));
        return card;
    }

    private LinearLayout createCardBody(MaterialCardView card) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(col);
        return col;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int resolveAttrColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}