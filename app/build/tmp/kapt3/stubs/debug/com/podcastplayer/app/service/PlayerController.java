package com.podcastplayer.app.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\u0007\n\u0002\b\u0003\u0018\u0000 #2\u00020\u0001:\u0001#B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\r\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\u000fJ\u000e\u0010\u0010\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\u000fJ\u000e\u0010\u0011\u001a\u00020\u0012H\u0086@\u00a2\u0006\u0002\u0010\u000fJ\u000e\u0010\u0013\u001a\u00020\u0014H\u0086@\u00a2\u0006\u0002\u0010\u000fJ \u0010\u0015\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u00172\b\u0010\u0018\u001a\u0004\u0018\u00010\u0019H\u0086@\u00a2\u0006\u0002\u0010\u001aJ\u0006\u0010\u001b\u001a\u00020\u0014J\u0016\u0010\u001c\u001a\u00020\u00142\u0006\u0010\u001d\u001a\u00020\u000eH\u0086@\u00a2\u0006\u0002\u0010\u001eJ\u0016\u0010\u001f\u001a\u00020\u00142\u0006\u0010 \u001a\u00020!H\u0086@\u00a2\u0006\u0002\u0010\"R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\b\u001a\n \n*\u0004\u0018\u00010\t0\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006$"}, d2 = {"Lcom/podcastplayer/app/service/PlayerController;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "controllerFuture", "Lcom/google/common/util/concurrent/ListenableFuture;", "Landroidx/media3/session/MediaController;", "executor", "Ljava/util/concurrent/ExecutorService;", "kotlin.jvm.PlatformType", "sessionToken", "Landroidx/media3/session/SessionToken;", "getCurrentPosition", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getDuration", "getPlaybackState", "", "pause", "", "playEpisode", "episode", "Lcom/podcastplayer/app/domain/model/Episode;", "artworkUrl", "", "(Lcom/podcastplayer/app/domain/model/Episode;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "release", "seekTo", "position", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setPlaybackSpeed", "speed", "", "(FLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_debug"})
public final class PlayerController {
    @org.jetbrains.annotations.NotNull
    private final androidx.media3.session.SessionToken sessionToken = null;
    @org.jetbrains.annotations.NotNull
    private final com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController> controllerFuture = null;
    private final java.util.concurrent.ExecutorService executor = null;
    @kotlin.jvm.Volatile
    @org.jetbrains.annotations.Nullable
    private static volatile com.podcastplayer.app.service.PlayerController instance;
    @org.jetbrains.annotations.NotNull
    public static final com.podcastplayer.app.service.PlayerController.Companion Companion = null;
    
    private PlayerController(android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object playEpisode(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Episode episode, @org.jetbrains.annotations.Nullable
    java.lang.String artworkUrl, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object pause(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object seekTo(long position, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object setPlaybackSpeed(float speed, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object getCurrentPosition(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object getDuration(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object getPlaybackState(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion) {
        return null;
    }
    
    public final void release() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u0007R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"Lcom/podcastplayer/app/service/PlayerController$Companion;", "", "()V", "instance", "Lcom/podcastplayer/app/service/PlayerController;", "getInstance", "context", "Landroid/content/Context;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.podcastplayer.app.service.PlayerController getInstance(@org.jetbrains.annotations.NotNull
        android.content.Context context) {
            return null;
        }
    }
}