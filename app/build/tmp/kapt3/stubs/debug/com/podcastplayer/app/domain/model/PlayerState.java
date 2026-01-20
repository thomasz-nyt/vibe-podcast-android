package com.podcastplayer.app.domain.model;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0011\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B9\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\b\b\u0002\u0010\b\u001a\u00020\u0007\u0012\b\b\u0002\u0010\t\u001a\u00020\n\u00a2\u0006\u0002\u0010\u000bJ\t\u0010\u0015\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u0016\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003J\t\u0010\u0017\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\nH\u00c6\u0003J=\u0010\u001a\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\nH\u00c6\u0001J\u0013\u0010\u001b\u001a\u00020\u001c2\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001e\u001a\u00020\u001fH\u00d6\u0001J\t\u0010 \u001a\u00020!H\u00d6\u0001R\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u000fR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014\u00a8\u0006\""}, d2 = {"Lcom/podcastplayer/app/domain/model/PlayerState;", "", "state", "Lcom/podcastplayer/app/domain/model/PlaybackState;", "currentEpisode", "Lcom/podcastplayer/app/domain/model/Episode;", "currentPosition", "", "duration", "playbackSpeed", "", "(Lcom/podcastplayer/app/domain/model/PlaybackState;Lcom/podcastplayer/app/domain/model/Episode;JJF)V", "getCurrentEpisode", "()Lcom/podcastplayer/app/domain/model/Episode;", "getCurrentPosition", "()J", "getDuration", "getPlaybackSpeed", "()F", "getState", "()Lcom/podcastplayer/app/domain/model/PlaybackState;", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
public final class PlayerState {
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.domain.model.PlaybackState state = null;
    @org.jetbrains.annotations.Nullable
    private final com.podcastplayer.app.domain.model.Episode currentEpisode = null;
    private final long currentPosition = 0L;
    private final long duration = 0L;
    private final float playbackSpeed = 0.0F;
    
    public PlayerState(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.PlaybackState state, @org.jetbrains.annotations.Nullable
    com.podcastplayer.app.domain.model.Episode currentEpisode, long currentPosition, long duration, float playbackSpeed) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.podcastplayer.app.domain.model.PlaybackState getState() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final com.podcastplayer.app.domain.model.Episode getCurrentEpisode() {
        return null;
    }
    
    public final long getCurrentPosition() {
        return 0L;
    }
    
    public final long getDuration() {
        return 0L;
    }
    
    public final float getPlaybackSpeed() {
        return 0.0F;
    }
    
    public PlayerState() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.podcastplayer.app.domain.model.PlaybackState component1() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final com.podcastplayer.app.domain.model.Episode component2() {
        return null;
    }
    
    public final long component3() {
        return 0L;
    }
    
    public final long component4() {
        return 0L;
    }
    
    public final float component5() {
        return 0.0F;
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.podcastplayer.app.domain.model.PlayerState copy(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.PlaybackState state, @org.jetbrains.annotations.Nullable
    com.podcastplayer.app.domain.model.Episode currentEpisode, long currentPosition, long duration, float playbackSpeed) {
        return null;
    }
    
    @java.lang.Override
    public boolean equals(@org.jetbrains.annotations.Nullable
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public java.lang.String toString() {
        return null;
    }
}