package neofusion.phantomage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;

public class OverlayService extends Service {
    public static final String INTENT_EXTRA_ALPHA = "alpha";
    public static final String CHANNEL_MAIN = "channel_main";
    public static final int NOTIFICATION_ID = 1;

    private NotificationManager mNotificationManager;
    private WindowManager mWindowManager;
    private View mOverlayView;
    private ImageView mImageView;

    private boolean isRunning = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mOverlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null);
        mImageView = mOverlayView.findViewById(R.id.image);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Uri uri = intent.getData();
        float alpha = intent.getFloatExtra(INTENT_EXTRA_ALPHA, 1F);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.alpha = alpha;
        mImageView.setImageURI(uri);
        if (!isRunning) {
            mWindowManager.addView(mOverlayView, layoutParams);
            createNotification();
            isRunning = true;
        } else {
            mWindowManager.updateViewLayout(mOverlayView, layoutParams);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void createNotification() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_MAIN, getString(R.string.channel_main), NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(channel);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_STOP_SERVICE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_MAIN)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOverlayView != null) {
            mWindowManager.removeView(mOverlayView);
            stopForeground(true);
            isRunning = false;
        }
    }
}
