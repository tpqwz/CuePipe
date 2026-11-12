package app.cuepipe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;

/**
 * Keeps the player alive after the screen is gone. A kept file and a stream
 * both play here. The activity only attaches a view.
 */
@androidx.media3.common.util.UnstableApi
public final class PlayService extends Service {
    static final String PAUSE = "app.cuepipe.PAUSE";
    static final String PLAY = "app.cuepipe.PLAY";
    static final String STOP = "app.cuepipe.STOP";

    private static final String CHANNEL = "play";
    private static final int NOTE = 7;

    private static ExoPlayer shared;
    private static PlayService live;

    private MediaSession session;
    private boolean foreground;

    static ExoPlayer player(Context context) {
        if (shared == null) {
            Context app = context.getApplicationContext();
            androidx.media3.datasource.DefaultHttpDataSource.Factory http =
                    new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                            .setUserAgent(OkDownloader.USER_AGENT);
            shared = new ExoPlayer.Builder(app)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(app).setDataSourceFactory(http))
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(), true)
                    .setHandleAudioBecomingNoisy(true)
                    .build();
            shared.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    PlayService service = live;
                    if (service == null) {
                        return;
                    }
                    if (isPlaying) {
                        service.note();
                    } else {
                        service.note();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    PlayService service = live;
                    if (service != null && state == Player.STATE_ENDED) {
                        service.note();
                    }
                }
            });
        }
        return shared;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        live = this;
        player(this);
        session = new MediaSession.Builder(this, shared).setId("cuepipe").build();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (PAUSE.equals(action)) {
            shared.pause();
        } else if (PLAY.equals(action)) {
            shared.play();
        } else if (STOP.equals(action)) {
            shared.pause();
            shared.clearMediaItems();
            hide();
            stopSelf();
        } else if (shared.isPlaying()) {
            note();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (live == this) {
            live = null;
        }
        if (session != null) {
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void note() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title())
                .setContentText(shared.isPlaying() ? "Playing" : "Paused")
                .setContentIntent(PendingIntent.getActivity(
                        this, 0, new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(shared.isPlaying())
                .addAction(shared.isPlaying() ? pauseAction() : playAction())
                .addAction(stopAction())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.getSessionCompatToken())
                        .setShowActionsInCompactView(0, 1))
                .build();
        if (!foreground) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTE, notification);
                }
                foreground = true;
            } catch (RuntimeException ignored) {
                // Notification permission can be denied. Playback still runs.
            }
        } else {
            getSystemService(NotificationManager.class).notify(NOTE, notification);
        }
    }

    private void hide() {
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
    }

    private String title() {
        MediaMetadata meta = shared.getMediaMetadata();
        if (meta.title != null && meta.title.length() > 0) {
            return meta.title.toString();
        }
        return "CuePipe";
    }

    private NotificationCompat.Action playAction() {
        return new NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play", pending(PLAY));
    }

    private NotificationCompat.Action pauseAction() {
        return new NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause", pending(PAUSE));
    }

    private NotificationCompat.Action stopAction() {
        return new NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Stop", pending(STOP));
    }

    private PendingIntent pending(String action) {
        Intent intent = new Intent(this, PlayService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
    }
}
