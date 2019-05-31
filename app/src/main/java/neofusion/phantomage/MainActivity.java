package neofusion.phantomage;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final int READ_REQUEST_CODE = 1;

    private static final String SAVED_STATE_IMAGE_URI = "imageUri";

    public static final String ACTION_STOP_SERVICE = "neofusion.phantomage.STOP_SERVICE";

    private Uri mImageUri;
    private ImageView mImageView;
    private EditText mPaddingEdit;
    private SeekBar mSeekBar;
    private View mMainView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) {
            mImageUri = savedInstanceState.getParcelable(SAVED_STATE_IMAGE_URI);
        }
        mMainView = findViewById(R.id.main_layout);
        mImageView = findViewById(R.id.image_view);
        mImageView.setImageURI(mImageUri);
        mPaddingEdit = findViewById(R.id.edit_padding);
        mPaddingEdit.setText("0");
        mSeekBar = findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            TextView opaqueText = findViewById(R.id.text_opaque);

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opaqueText.setText(String.format(getResources().getString(R.string.text_opaque), seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mSeekBar.setProgress(50);
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
                mImageUri = null;
                mImageView.setImageDrawable(null);
            }
        });
        if (ACTION_STOP_SERVICE.equals(getIntent().getAction())) {
            stopOverlayService();
        }
    }

    private void startOverlayService() {
        if (mImageUri == null) {
            return;
        }
        Intent intent = new Intent(MainActivity.this, OverlayService.class);
        intent.setData(mImageUri);
        float alpha = mSeekBar.getProgress() / 100F;
        int padding = Integer.parseInt(mPaddingEdit.getText().toString());
        intent.putExtra(OverlayService.INTENT_EXTRA_ALPHA, alpha);
        intent.putExtra(OverlayService.INTENT_EXTRA_PADDING, padding);
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(SAVED_STATE_IMAGE_URI, mImageUri);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            mImageUri = data.getData();
            mImageView.setImageURI(mImageUri);
        }
    }
}
