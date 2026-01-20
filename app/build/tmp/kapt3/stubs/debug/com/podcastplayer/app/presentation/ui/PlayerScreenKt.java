package com.podcastplayer.app.presentation.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00004\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0002\b\b\u001a\u0097\u0001\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\b\u0010\u0006\u001a\u0004\u0018\u00010\u00072\b\u0010\b\u001a\u0004\u0018\u00010\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\u0012\u0010\f\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u00010\r2\u0012\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00010\r2\u0012\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\u00010\r2\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00010\u000b2\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00010\u000bH\u0007\u00a2\u0006\u0002\u0010\u0013\u001a\u0010\u0010\u0014\u001a\u00020\u00072\u0006\u0010\u0015\u001a\u00020\tH\u0002\u001a\u000e\u0010\u0016\u001a\u00020\u0007*\u0004\u0018\u00010\u0007H\u0002\u00a8\u0006\u0017"}, d2 = {"PlayerScreen", "", "episode", "Lcom/podcastplayer/app/domain/model/Episode;", "playerState", "Lcom/podcastplayer/app/domain/model/PlayerState;", "artworkUrl", "", "sleepTimerRemaining", "", "onPlayPause", "Lkotlin/Function0;", "onSeek", "Lkotlin/Function1;", "onSpeedChange", "", "onSetSleepTimer", "onCancelSleepTimer", "onDismiss", "(Lcom/podcastplayer/app/domain/model/Episode;Lcom/podcastplayer/app/domain/model/PlayerState;Ljava/lang/String;Ljava/lang/Long;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V", "formatTime", "ms", "stripHtml", "app_debug"})
public final class PlayerScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable
    public static final void PlayerScreen(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Episode episode, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.PlayerState playerState, @org.jetbrains.annotations.Nullable
    java.lang.String artworkUrl, @org.jetbrains.annotations.Nullable
    java.lang.Long sleepTimerRemaining, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onPlayPause, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.Long, kotlin.Unit> onSeek, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.Float, kotlin.Unit> onSpeedChange, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.Long, kotlin.Unit> onSetSleepTimer, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onCancelSleepTimer, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss) {
    }
    
    private static final java.lang.String formatTime(long ms) {
        return null;
    }
    
    private static final java.lang.String stripHtml(java.lang.String $this$stripHtml) {
        return null;
    }
}