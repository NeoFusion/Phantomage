package neofusion.phantomage;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MainActivity extends Activity {
    private static final int READ_REQUEST_CODE = 1;

    private static final String SETTINGS_IMAGE_URI = "imageUri";
    private static final String SETTINGS_IMAGE_KEY = "imageKey";
    private static final String SETTINGS_ALPHA = "alpha";
    private static final String SETTINGS_ANGLE = "angle";
    private static final String SETTINGS_PADDING = "padding";

    private static final String IMAGE_FILENAME = "image.data";

    public static final String ACTION_STOP_SERVICE = "neofusion.phantomage.STOP_SERVICE";

    private SharedPreferences mSettings;
    private Uri mImageUri;
    private ImageView mImageView;
    private EditText mPaddingEdit;
    private SeekBar mSeekBar;
    private View mMainView;
    private float mAngle = 0F;
    private long mImageKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        mMainView = findViewById(R.id.main_layout);
        mImageView = findViewById(R.id.image_view);
        mImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageUri != null) {
                    mAngle = mAngle == 270F ? 0 : mAngle + 90F;
                    loadImage();
                }
            }
        });
        mPaddingEdit = findViewById(R.id.edit_padding);
        mSeekBar = findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            TextView opacityText = findViewById(R.id.text_opacity);

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityText.setText(String.format(getResources().getString(R.string.text_opacity), seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        Button startButton = findViewById(R.id.button_start);
        Button stopButton = findViewById(R.id.button_stop);
        Button selectImageButton = findViewById(R.id.button_select_image);
        Button clearImageButton = findViewById(R.id.button_clear_image);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startOverlayService();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopOverlayService();
            }
        });
        selectImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });
        clearImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearImage();
            }
        });
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                startActivity(intent);
            }
        });
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                processImage(imageUri);
            }
            restoreSettings(false);
            stopOverlayService();
        } else {
            restoreSettings(true);
        }
        loadImage();
        if (ACTION_STOP_SERVICE.equals(getIntent().getAction())) {
            stopOverlayService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    private void startOverlayService() {
        if (mImageUri == null) {
            return;
        }
        Intent intent = new Intent(MainActivity.this, OverlayService.class);
        intent.setData(mImageUri);
        float alpha = mSeekBar.getProgress() / 100F;
        intent.putExtra(OverlayService.INTENT_EXTRA_ALPHA, alpha);
        intent.putExtra(OverlayService.INTENT_EXTRA_ANGLE, mAngle);
        intent.putExtra(OverlayService.INTENT_EXTRA_PADDING, getPadding());
        if (Settings.canDrawOverlays(getApplicationContext())) {
            startForegroundService(intent);
        } else {
            Snackbar.make(mMainView, R.string.permission_overlay, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.snackbar_settings, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        }
                    })
                    .show();
        }
    }

    private void stopOverlayService() {
        stopService(new Intent(MainActivity.this, OverlayService.class));
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = mSettings.edit();
        if (mImageUri != null) {
            editor.putString(SETTINGS_IMAGE_URI, mImageUri.toString());
        } else {
            editor.remove(SETTINGS_IMAGE_URI);
        }
        editor.putLong(SETTINGS_IMAGE_KEY, mImageKey);
        editor.putInt(SETTINGS_ALPHA, mSeekBar.getProgress());
        editor.putFloat(SETTINGS_ANGLE, mAngle);
        editor.putInt(SETTINGS_PADDING, getPadding());
        editor.apply();
    }

    private void restoreSettings(boolean restoreImage) {
        if (restoreImage && mSettings.contains(SETTINGS_IMAGE_URI)) {
            String imageUriString = mSettings.getString(SETTINGS_IMAGE_URI, null);
            if (imageUriString != null) {
                mImageUri = Uri.parse(imageUriString);
            }
        }
        if (restoreImage && mSettings.contains(SETTINGS_IMAGE_KEY)) {
            mImageKey = mSettings.getLong(SETTINGS_IMAGE_KEY, System.currentTimeMillis());
        }
        if (restoreImage && mSettings.contains(SETTINGS_ANGLE)) {
            mAngle = mSettings.getFloat(SETTINGS_ANGLE, 0F);
        }
        if (mSettings.contains(SETTINGS_PADDING)) {
            mPaddingEdit.setText(String.valueOf(mSettings.getInt(SETTINGS_PADDING, 0)));
        } else {
            mPaddingEdit.setText("0");
        }
        if (mSettings.contains(SETTINGS_ALPHA)) {
            mSeekBar.setProgress(mSettings.getInt(SETTINGS_ALPHA, 50));
        } else {
            mSeekBar.setProgress(50);
        }
    }

    private void processImage(Uri imageUri) {
        try {
            mImageUri = saveImage(imageUri);
            mImageKey = System.currentTimeMillis();
        } catch (FileNotFoundException e) {
            Snackbar.make(mMainView, getString(R.string.error_file_not_found), Snackbar.LENGTH_SHORT).show();
        } catch (IOException e) {
            Snackbar.make(mMainView, getString(R.string.error_io), Snackbar.LENGTH_SHORT).show();
        }
    }

    private Uri saveImage(Uri data) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(data);
        if (inputStream == null) {
            throw new IOException();
        }
        File file = new File(getCacheDir(), IMAGE_FILENAME);
        Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return Uri.fromFile(file);
    }

    private void loadImage() {
        if (mImageUri != null) {
            ObjectKey objectKey = new ObjectKey(mImageKey);
            if (mAngle == 0) {
                Glide.with(this).load(mImageUri).signature(objectKey).into(mImageView);
            } else {
                Glide.with(this).load(mImageUri).signature(objectKey).transform(new RotateTransformation(mAngle)).into(mImageView);
            }
        }
    }

    private void clearImage() {
        mAngle = 0F;
        mImageUri = null;
        mImageKey = System.currentTimeMillis();
        Glide.with(this).clear(mImageView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            Snackbar.make(mMainView, getString(R.string.text_about), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.button_ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                        }
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            clearImage();
            if (data.getData() != null) {
                processImage(data.getData());
                loadImage();
            }
        }
    }

    private int getPadding() {
        try {
            return Integer.parseInt(mPaddingEdit.getText().toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
