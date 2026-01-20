package com.podcastplayer.app.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\r\u001a\u00020\u000eH\u0002J\b\u0010\u000f\u001a\u00020\u000eH\u0002J\b\u0010\u0010\u001a\u00020\u000eH\u0016J\b\u0010\u0011\u001a\u00020\u000eH\u0016J\u0012\u0010\u0012\u001a\u0004\u0018\u00010\b2\u0006\u0010\u0013\u001a\u00020\u0014H\u0016J\u0006\u0010\u0015\u001a\u00020\u000eJ\u0018\u0010\u0016\u001a\u00020\u000e2\u0006\u0010\u0017\u001a\u00020\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\u0004J\u000e\u0010\u001a\u001a\u00020\u000e2\u0006\u0010\u001b\u001a\u00020\u001cJ\u000e\u0010\u001d\u001a\u00020\u000e2\u0006\u0010\u001e\u001a\u00020\u001fJ\b\u0010 \u001a\u00020\u000eH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082D\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u0004\u0018\u00010\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006!"}, d2 = {"Lcom/podcastplayer/app/service/PlayerService;", "Landroidx/media3/session/MediaSessionService;", "()V", "CHANNEL_ID", "", "NOTIFICATION_ID", "", "mediaSession", "Landroidx/media3/session/MediaSession;", "notificationManager", "Landroidx/media3/ui/PlayerNotificationManager;", "player", "Landroidx/media3/exoplayer/ExoPlayer;", "createNotificationChannel", "", "initializePlayer", "onCreate", "onDestroy", "onGetSession", "controllerInfo", "Landroidx/media3/session/MediaSession$ControllerInfo;", "pause", "playEpisode", "episode", "Lcom/podcastplayer/app/domain/model/Episode;", "artworkUrl", "seekTo", "position", "", "setPlaybackSpeed", "speed", "", "setupNotification", "app_debug"})
public final class PlayerService extends androidx.media3.session.MediaSessionService {
    @org.jetbrains.annotations.Nullable
    private androidx.media3.exoplayer.ExoPlayer player;
    @org.jetbrains.annotations.Nullable
    private androidx.media3.session.MediaSession mediaSession;
    @org.jetbrains.annotations.Nullable
    private androidx.media3.ui.PlayerNotificationManager notificationManager;
    @org.jetbrains.annotations.NotNull
    private final java.lang.String CHANNEL_ID = "podcast_player_channel";
    private final int NOTIFICATION_ID = 1;
    
    public PlayerService() {
        super();
    }
    
    @java.lang.Override
    public void onCreate() {
    }
    
    private final void initializePlayer() {
    }
    
    private final void createNotificationChannel() {
    }
    
    private final void setupNotification() {
    }
    
    public final void playEpisode(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Episode episode, @org.jetbrains.annotations.Nullable
    java.lang.String artworkUrl) {
    }
    
    public final void pause() {
    }
    
    public final void seekTo(long position) {
    }
    
    public final void setPlaybackSpeed(float speed) {
    }
    
    @java.lang.Override
    @org.jetbrains.annotations.Nullable
    public androidx.media3.session.MediaSession onGetSession(@org.jetbrains.annotations.NotNull
    androidx.media3.session.MediaSession.ControllerInfo controllerInfo) {
        return null;
    }
    
    @java.lang.Override
    public void onDestroy() {
    }
}