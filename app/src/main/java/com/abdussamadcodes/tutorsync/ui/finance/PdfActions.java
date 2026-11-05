package com.abdussamadcodes.tutorsync.ui.finance;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.abdussamadcodes.tutorsync.R;
import com.abdussamadcodes.tutorsync.util.AppExecutors;
import com.abdussamadcodes.tutorsync.util.FinancialPdfGenerator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;

/**
 * M7: the one place that creates PDFs for the screens.
 *
 * - the PDF is built in the BACKGROUND (a progress dialog is shown, the screen never freezes),
 * - nothing is written to the public Downloads folder unless the tutor chooses "Save to Downloads",
 * - the tutor can Preview, Share or Save the finished PDF.
 */
public final class PdfActions {

    /** Builds the PDF. Runs on a background thread. Return null when it could not be created. */
    public interface PdfJob {
        @Nullable
        File create(@NonNull Context appContext) throws Exception;
    }

    private PdfActions() {
    }

    public static void generateAndShowOptions(@NonNull Activity activity,
                                              @StringRes int shareTitleRes,
                                              @NonNull PdfJob job) {
        final Context appContext = activity.getApplicationContext();
        final String shareTitle = activity.getString(shareTitleRes);
        final AlertDialog progress = buildProgressDialog(activity);
        progress.show();

        AppExecutors.getInstance().diskIO().execute(() -> {
            File pdf = null;
            try {
                pdf = job.create(appContext);
            } catch (Exception ignored) {
                // reported below as a generic failure
            }
            final File result = pdf;
            AppExecutors.getInstance().mainThread().execute(() -> {
                try {
                    progress.dismiss();
                } catch (Exception ignored) {
                }
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (result == null || !result.exists()) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.pdf_err_title)
                            .setMessage(R.string.pdf_err_failed)
                            .setPositiveButton(R.string.refund_ok, null)
                            .show();
                    return;
                }
                showOptions(activity, result, shareTitle);
            });
        });
    }

    private static void showOptions(@NonNull Activity activity, @NonNull File pdf, @NonNull String shareTitle) {
        String[] items = new String[]{
                activity.getString(R.string.pdf_action_preview),
                activity.getString(R.string.pdf_action_share),
                activity.getString(R.string.pdf_action_save_downloads)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.pdf_ready_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        FinancialPdfGenerator.openPdf(activity, pdf);
                    } else if (which == 1) {
                        FinancialPdfGenerator.sharePdf(activity, pdf, shareTitle);
                    } else {
                        saveToDownloads(activity, pdf);
                    }
                })
                .setNegativeButton(R.string.pdf_close, null)
                .show();
    }

    /** Only runs when the tutor explicitly chose "Save to Downloads". */
    private static void saveToDownloads(@NonNull Activity activity, @NonNull File pdf) {
        final Context appContext = activity.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            Uri saved = FinancialPdfGenerator.savePdfToDownloads(appContext, pdf);
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Toast.makeText(activity,
                        saved != null ? R.string.pdf_saved_downloads : R.string.pdf_save_failed,
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    @NonNull
    private static AlertDialog buildProgressDialog(@NonNull Activity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (24 * density);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, pad, pad, pad);

        CircularProgressIndicator indicator = new CircularProgressIndicator(activity);
        indicator.setIndeterminate(true);
        row.addView(indicator);

        TextView label = new TextView(activity);
        label.setText(R.string.pdf_preparing);
        label.setTextSize(16f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart((int) (20 * density));
        row.addView(label, lp);

        return new MaterialAlertDialogBuilder(activity)
                .setView(row)
                .setCancelable(false)
                .create();
    }
}
