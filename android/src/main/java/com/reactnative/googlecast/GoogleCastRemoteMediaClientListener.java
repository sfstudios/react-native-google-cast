package com.reactnative.googlecast;

import android.support.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.util.List;

import static com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE;
import static com.google.android.gms.cast.MediaMetadata.KEY_TITLE;

public class GoogleCastRemoteMediaClientListener
    implements RemoteMediaClient.Listener, RemoteMediaClient.ProgressListener {
  private GoogleCastModule module;
  private boolean playbackStarted;
  private boolean playbackEnded;
  private int currentItemId;

  public GoogleCastRemoteMediaClientListener(GoogleCastModule module) {
    this.module = module;
  }

  @Override
  public void onStatusUpdated() {
    module.runOnUiQueueThread(new Runnable() {
      @Override
      public void run() {
        MediaStatus mediaStatus = module.getMediaStatus();
        if (mediaStatus == null) {
          return;
        }

        if (currentItemId != mediaStatus.getCurrentItemId()) {
          // reset item status
          currentItemId = mediaStatus.getCurrentItemId();
          playbackStarted = false;
          playbackEnded = false;
        }

        module.emitMessageToRN(GoogleCastModule.MEDIA_STATUS_UPDATED,
                               prepareMessage(mediaStatus));

        if (!playbackStarted &&
            mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING) {
          module.emitMessageToRN(GoogleCastModule.MEDIA_PLAYBACK_STARTED,
                                 prepareMessage(mediaStatus));
          playbackStarted = true;
        }

        if (!playbackEnded &&
            mediaStatus.getIdleReason() == MediaStatus.IDLE_REASON_FINISHED) {
          module.emitMessageToRN(GoogleCastModule.MEDIA_PLAYBACK_ENDED,
                                 prepareMessage(mediaStatus));
          playbackEnded = true;
        }
      }
    });
  }

  @NonNull
  private WritableMap prepareMessage(MediaStatus mediaStatus) {
    // needs to be constructed for every message from scratch because reusing a
    // message fails with "Map already consumed"
    WritableMap map = Arguments.createMap();
    map.putInt("playerState", mediaStatus.getPlayerState());
    map.putInt("idleReason", mediaStatus.getIdleReason());
    map.putBoolean("muted", mediaStatus.isMute());
    map.putInt("streamPosition", (int)(mediaStatus.getStreamPosition() / 1000));

    MediaInfo info = mediaStatus.getMediaInfo();

    final CastSession session = module.getCastSession();
    if(session != null){
        CastDevice device = session.getCastDevice();
        String deviceName = device.getFriendlyName();
        map.putString("deviceName", deviceName);
    }

    if (info != null) {
        // Figure out selected subtitle and audio language
        List<MediaTrack> mediaTracks = info.getMediaTracks();
        long[] selectedTrackIds = mediaStatus.getActiveTrackIds();

        if (mediaTracks != null && selectedTrackIds != null) {
            for (int i = 0; i <= mediaTracks.size() - 1; i += 1) {
                MediaTrack thisMediaTrack = mediaTracks.get(i);

                if (thisMediaTrack != null) {
                    for (long id : selectedTrackIds) {
                        if (thisMediaTrack.getId() == id) {
                            if (thisMediaTrack.getType() == MediaTrack.TYPE_TEXT) {
                                map.putString("selectedSubtitleLanguage", thisMediaTrack.getLanguage());
                            } else if (thisMediaTrack.getType() == MediaTrack.TYPE_AUDIO) {
                                map.putString("selectedAudioLanguage", thisMediaTrack.getLanguage());
                            }
                        }
                    }
                }
            }
        }

        map.putInt("streamDuration", (int) (info.getStreamDuration() / 1000));
        MediaMetadata metadata = info.getMetadata();
        if (metadata == null) {
            WritableMap message = Arguments.createMap();
            message.putMap("mediaStatus", map);
            return message;
        }
        String title = metadata.getString(KEY_TITLE);
        if(title != null) map.putString("title", title);
        String subtitle = metadata.getString(KEY_SUBTITLE);
        if(subtitle != null) map.putString("subtitle", subtitle);
        if(metadata.getImages().size() > 0){
            String imageUrl = metadata.getImages().get(0).getUrl().toString();
            map.putString("imageUrl", imageUrl);
        }
    }
    WritableMap message = Arguments.createMap();
    message.putMap("mediaStatus", map);
    return message;
  }

  @Override
  public void onMetadataUpdated() {}

  @Override
  public void onQueueStatusUpdated() {}

  @Override
  public void onPreloadStatusUpdated() {}

  @Override
  public void onSendingRemoteMediaRequest() {}

  @Override
  public void onAdBreakStatusUpdated() {}

  @Override
  public void onProgressUpdated(final long progressMs, final long durationMs) {
    module.runOnUiQueueThread(new Runnable() {
      @Override
      public void run() {
        MediaStatus mediaStatus = module.getMediaStatus();
        if (mediaStatus == null) {
          return;
        }

        if (mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING) {
          module.emitMessageToRN(
              GoogleCastModule.MEDIA_PROGRESS_UPDATED,
              prepareProgressMessage(progressMs, durationMs));
        }
      }
    });
  }

  @NonNull
  private WritableMap prepareProgressMessage(long progressMs, long durationMs) {
    // needs to be constructed for every message from scratch because reusing a
    // message fails with "Map already consumed"
    WritableMap map = Arguments.createMap();
    map.putInt("progress", (int)progressMs / 1000);
    map.putInt("duration", (int)durationMs / 1000);

    WritableMap message = Arguments.createMap();
    message.putMap("mediaProgress", map);
    return message;
  }
}
