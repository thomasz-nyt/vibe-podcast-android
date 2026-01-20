package com.podcastplayer.app.presentation.viewmodel;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0005\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u0016\u001a\u00020\u0017J\b\u0010\u0018\u001a\u00020\u0017H\u0014J\u0018\u0010\u0019\u001a\u00020\u00172\u0006\u0010\u001a\u001a\u00020\u00072\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cJ\u000e\u0010\u001d\u001a\u00020\u00172\u0006\u0010\u001e\u001a\u00020\u000bJ\u000e\u0010\u001f\u001a\u00020\u00172\u0006\u0010 \u001a\u00020!J\u000e\u0010\"\u001a\u00020\u00172\u0006\u0010#\u001a\u00020\u000bJ\b\u0010$\u001a\u00020\u0017H\u0002J\u0006\u0010%\u001a\u00020\u0017R\u0016\u0010\u0005\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\n\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000b0\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\f\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00070\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\t0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u000fR\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u000b0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u000f\u00a8\u0006&"}, d2 = {"Lcom/podcastplayer/app/presentation/viewmodel/PlayerViewModel;", "Landroidx/lifecycle/ViewModel;", "playerController", "Lcom/podcastplayer/app/service/PlayerController;", "(Lcom/podcastplayer/app/service/PlayerController;)V", "_currentEpisode", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/podcastplayer/app/domain/model/Episode;", "_playerState", "Lcom/podcastplayer/app/domain/model/PlayerState;", "_sleepTimerRemaining", "", "currentEpisode", "Lkotlinx/coroutines/flow/StateFlow;", "getCurrentEpisode", "()Lkotlinx/coroutines/flow/StateFlow;", "playerState", "getPlayerState", "sleepTimerJob", "Lkotlinx/coroutines/Job;", "sleepTimerRemaining", "getSleepTimerRemaining", "cancelSleepTimer", "", "onCleared", "playEpisode", "episode", "artworkUrl", "", "seekTo", "position", "setPlaybackSpeed", "speed", "", "setSleepTimer", "durationMs", "startPositionUpdates", "togglePlayPause", "app_debug"})
public final class PlayerViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.service.PlayerController playerController = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.podcastplayer.app.domain.model.PlayerState> _playerState = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.PlayerState> playerState = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.podcastplayer.app.domain.model.Episode> _currentEpisode = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.Episode> currentEpisode = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Long> _sleepTimerRemaining = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Long> sleepTimerRemaining = null;
    @org.jetbrains.annotations.Nullable
    private kotlinx.coroutines.Job sleepTimerJob;
    
    public PlayerViewModel(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.service.PlayerController playerController) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.PlayerState> getPlayerState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.Episode> getCurrentEpisode() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Long> getSleepTimerRemaining() {
        return null;
    }
    
    public final void playEpisode(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Episode episode, @org.jetbrains.annotations.Nullable
    java.lang.String artworkUrl) {
    }
    
    public final void togglePlayPause() {
    }
    
    public final void seekTo(long position) {
    }
    
    public final void setPlaybackSpeed(float speed) {
    }
    
    public final void setSleepTimer(long durationMs) {
    }
    
    public final void cancelSleepTimer() {
    }
    
    private final void startPositionUpdates() {
    }
    
    @java.lang.Override
    protected void onCleared() {
    }
}