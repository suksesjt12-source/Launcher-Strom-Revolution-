package com.winlator.cmod;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Game-specific home screen for Storm Revolution Launcher.
 *
 * This UI launches the selected Windows executable inside the embedded Wine/Box64 runtime.
 * It is not a native Android port of the game engine.
 */
public class StormHomeFragment extends Fragment {
    private static final int BG = Color.rgb(8, 10, 16);
    private static final int PANEL = Color.rgb(18, 22, 32);
    private static final int RED = Color.rgb(211, 45, 55);
    private static final int GOLD = Color.rgb(235, 174, 76);
    private static final String PREFS = "storm_revolution_launcher";
    private static final String KEY_GAME_PATH = "game_exe_path";
    private static final String KEY_CONTAINER_ID = "container_id";

    private final ActivityResultLauncher<String[]> gamePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onGamePicked);

    private final ArrayList<Container> containers = new ArrayList<>();
    private Spinner containerSpinner;
    private TextView gamePathText;
    private TextView runtimeStatus;
    private TextView containerStatus;
    private Button playButton;
    private Button setupButton;
    private ContainerManager containerManager;
    private String gamePath = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = text("STORM REVOLUTION  /  ANDROID", 11, GOLD, true);
        eyebrow.setLetterSpacing(0.16f);
        page.addView(eyebrow);

        TextView title = text("NARUTO SHIPPUDEN", 27, Color.WHITE, true);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setLetterSpacing(0.025f);
        LinearLayout.LayoutParams titleParams = params();
        titleParams.topMargin = dp(8);
        page.addView(title, titleParams);

        TextView subtitle = text("ULTIMATE NINJA STORM REVOLUTION", 15, GOLD, true);
        subtitle.setLetterSpacing(0.065f);
        page.addView(subtitle);

        TextView description = text(
                "GAME LAUNCHER  •  WINE + BOX64 RUNTIME TERTANAM", 10,
                Color.rgb(159, 169, 187), true);
        LinearLayout.LayoutParams descriptionParams = params();
        descriptionParams.topMargin = dp(10);
        page.addView(description, descriptionParams);

        LinearLayout hero = panel(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams heroParams = params();
        heroParams.topMargin = dp(22);
        page.addView(hero, heroParams);

        TextView heroKicker = text("GAME PROFILE", 10, GOLD, true);
        heroKicker.setLetterSpacing(0.15f);
        hero.addView(heroKicker);

        TextView gameName = text("Naruto Shippuden\nUltimate Ninja Storm Revolution", 20,
                Color.WHITE, true);
        gameName.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        LinearLayout.LayoutParams gameNameParams = params();
        gameNameParams.topMargin = dp(8);
        hero.addView(gameName, gameNameParams);

        gamePathText = text("File game belum dipilih. Pilih NSUNSR.exe dari folder game.", 12,
                Color.rgb(174, 183, 198), false);
        LinearLayout.LayoutParams pathParams = params();
        pathParams.topMargin = dp(12);
        hero.addView(gamePathText, pathParams);

        Button chooseButton = actionButton(context, "PILIH NSUNSR.EXE", PANEL, GOLD, Color.WHITE);
        LinearLayout.LayoutParams chooseParams = params();
        chooseParams.topMargin = dp(16);
        hero.addView(chooseButton, chooseParams);
        chooseButton.setOnClickListener(v -> gamePicker.launch(new String[]{"*/*"}));

        LinearLayout setup = panel(context);
        setup.setOrientation(LinearLayout.VERTICAL);
        setup.setPadding(dp(18), dp(17), dp(18), dp(17));
        LinearLayout.LayoutParams setupParams = params();
        setupParams.topMargin = dp(14);
        page.addView(setup, setupParams);

        TextView section = text("LINGKUNGAN WINDOWS", 12, Color.WHITE, true);
        section.setLetterSpacing(0.1f);
        setup.addView(section);

        runtimeStatus = text("Memeriksa runtime...", 11, Color.rgb(174, 183, 198), false);
        LinearLayout.LayoutParams runtimeParams = params();
        runtimeParams.topMargin = dp(8);
        setup.addView(runtimeStatus, runtimeParams);

        containerStatus = text("Container belum dipilih.", 11, Color.rgb(174, 183, 198), false);
        LinearLayout.LayoutParams cStatusParams = params();
        cStatusParams.topMargin = dp(12);
        setup.addView(containerStatus, cStatusParams);

        containerSpinner = new Spinner(context);
        containerSpinner.setBackground(roundRect(Color.rgb(28, 33, 46), 10, Color.rgb(65, 75, 94)));
        LinearLayout.LayoutParams spinnerParams = params();
        spinnerParams.topMargin = dp(8);
        setup.addView(containerSpinner, spinnerParams);

        setupButton = actionButton(context, "BUAT / ATUR CONTAINER", Color.rgb(42, 48, 62),
                Color.rgb(100, 116, 139), Color.WHITE);
        LinearLayout.LayoutParams setupButtonParams = params();
        setupButtonParams.topMargin = dp(12);
        setup.addView(setupButton, setupButtonParams);
        setupButton.setOnClickListener(v -> openMainSection(R.id.main_menu_containers));

        playButton = actionButton(context, "▶  MAIN SEKARANG", RED, RED, Color.WHITE);
        LinearLayout.LayoutParams playParams = params();
        playParams.topMargin = dp(18);
        page.addView(playButton, playParams);
        playButton.setOnClickListener(v -> launchGame());

        TextView note = text(
                "Catatan: aplikasi ini menjalankan versi Windows melalui runtime kompatibilitas internal. "
                        + "Ini bukan engine native Android.", 10,
                Color.rgb(132, 143, 161), false);
        LinearLayout.LayoutParams noteParams = params();
        noteParams.topMargin = dp(16);
        page.addView(note, noteParams);

        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsParams = params();
        toolsParams.topMargin = dp(20);
        page.addView(tools, toolsParams);
        addToolButton(tools, "KONTROL", R.id.main_menu_input_controls);
        addToolButton(tools, "DRIVER / RUNTIME", R.id.main_menu_contents);
        addToolButton(tools, "FILE", R.id.main_menu_file_manager);

        refreshState();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshState();
    }

    private void addToolButton(LinearLayout row, String label, int menuId) {
        Button button = actionButton(requireContext(), label, PANEL, Color.rgb(62, 74, 95),
                Color.rgb(222, 228, 238));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, p);
        button.setTextSize(9);
        button.setOnClickListener(v -> openMainSection(menuId));
    }

    private void refreshState() {
        if (!isAdded() || gamePathText == null) return;
        Context context = requireContext();
        gamePath = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_GAME_PATH, "");

        if (gamePath.isEmpty()) {
            gamePathText.setText("File game belum dipilih. Pilih NSUNSR.exe dari folder game.");
        } else {
            gamePathText.setText(gamePath);
        }

        boolean imageFsReady = false;
        try {
            imageFsReady = ImageFs.find(context).isValid();
        } catch (Exception ignored) {}

        containers.clear();
        if (imageFsReady) {
            runtimeStatus.setText("Runtime image tersedia. Wine / Box64 berjalan di dalam aplikasi ini.");
            try {
                containerManager = new ContainerManager(context);
                containers.addAll(containerManager.getContainers());
            } catch (Exception e) {
                runtimeStatus.setText("Runtime ditemukan, tetapi daftar container gagal dibaca: " + e.getMessage());
            }
        } else {
            runtimeStatus.setText("Runtime belum siap. Selesaikan ekstraksi / setup pertama kali.");
            containerManager = null;
        }

        ArrayList<String> names = new ArrayList<>();
        if (containers.isEmpty()) {
            names.add("Belum ada container Windows");
            containerStatus.setText("Buat satu container Wine/Proton untuk game ini.");
        } else {
            for (Container container : containers) names.add(container.getName());
            containerStatus.setText("Pilih container yang akan digunakan untuk menjalankan game.");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        containerSpinner.setAdapter(adapter);
        containerSpinner.setEnabled(!containers.isEmpty());

        int selectedId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_CONTAINER_ID, -1);
        int selectedIndex = 0;
        for (int i = 0; i < containers.size(); i++) {
            if (containers.get(i).id == selectedId) selectedIndex = i;
        }
        if (!containers.isEmpty()) containerSpinner.setSelection(selectedIndex);
        containerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < containers.size()) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putInt(KEY_CONTAINER_ID, containers.get(position).id).apply();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        boolean validGame = !gamePath.isEmpty() && new File(gamePath).isFile()
                && gamePath.toLowerCase(Locale.ROOT).endsWith(".exe");
        playButton.setEnabled(validGame && !containers.isEmpty());
        playButton.setAlpha(validGame && !containers.isEmpty() ? 1f : 0.48f);
        setupButton.setEnabled(imageFsReady);
        setupButton.setAlpha(imageFsReady ? 1f : 0.55f);
    }

    private void onGamePicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}

        String path = resolveFilesystemPath(uri);
        if (path == null || !new File(path).isFile()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Lokasi file tidak terbaca")
                    .setMessage("Pilih NSUNSR.exe dari penyimpanan internal bersama, misalnya Internal storage/Naruto Storm Revolution/NSUNSR.exe. Provider file ini tidak memberikan path yang dapat dipakai Wine.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        if (!path.toLowerCase(Locale.ROOT).endsWith(".exe")
                || !new File(path).getName().equalsIgnoreCase("NSUNSR.exe")) {
            Toast.makeText(requireContext(), "Pilih file NSUNSR.exe, bukan installer atau file lain.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        gamePath = path;
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_GAME_PATH, path).apply();
        refreshState();
        Toast.makeText(requireContext(), "NSUNSR.exe tersimpan.", Toast.LENGTH_SHORT).show();
    }

    private String resolveFilesystemPath(Uri uri) {
        Context context = requireContext();
        if ("file".equalsIgnoreCase(uri.getScheme())) return uri.getPath();

        if (DocumentsContract.isDocumentUri(context, uri)
                && "com.android.externalstorage.documents".equals(uri.getAuthority())) {
            String documentId = DocumentsContract.getDocumentId(uri);
            String[] parts = documentId.split(":", 2);
            if (parts.length == 2) {
                File volume = "primary".equalsIgnoreCase(parts[0])
                        ? Environment.getExternalStorageDirectory()
                        : new File("/storage/" + parts[0]);
                return new File(volume, parts[1]).getAbsolutePath();
            }
        }

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private void launchGame() {
        if (!isAdded()) return;
        if (gamePath.isEmpty() || !new File(gamePath).isFile()) {
            Toast.makeText(requireContext(), "Pilih NSUNSR.exe terlebih dahulu.", Toast.LENGTH_LONG).show();
            return;
        }
        if (containerManager == null || containers.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Container belum tersedia")
                    .setMessage("Buat container Wine/Proton terlebih dahulu. Setelah selesai, kembali ke Beranda Storm dan tekan Main Sekarang.")
                    .setPositiveButton("Buka pengaturan container",
                            (dialog, which) -> openMainSection(R.id.main_menu_containers))
                    .setNegativeButton("Batal", null)
                    .show();
            return;
        }

        int position = containerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= containers.size()) position = 0;
        Container selected = containers.get(position);
        Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
        intent.putExtra("container_id", selected.id);
        intent.putExtra("shortcut_path", gamePath);
        intent.putExtra("shortcut_name", "Naruto Shippuden Ultimate Ninja Storm Revolution");
        startActivity(intent);
    }

    private void openMainSection(int menuId) {
        if (!(requireActivity() instanceof MainActivity)) return;
        NavigationView navigationView = requireActivity().findViewById(R.id.NavigationView);
        if (navigationView != null && navigationView.getMenu().findItem(menuId) != null) {
            ((MainActivity) requireActivity()).onNavigationItemSelected(
                    navigationView.getMenu().findItem(menuId));
        }
    }

    private LinearLayout panel(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setBackground(roundRect(PANEL, 16, Color.rgb(43, 52, 69)));
        return layout;
    }

    private Button actionButton(Context context, String label, int fill, int stroke, int textColor) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(12);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        button.setBackground(roundRect(fill, 12, stroke));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable roundRect(int color, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
