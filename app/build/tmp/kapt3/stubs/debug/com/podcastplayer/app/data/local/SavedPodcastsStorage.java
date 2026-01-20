package com.podcastplayer.app.data.local;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0006\u0018\u0000 \u001f2\u00020\u0001:\u0001\u001fB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\b0\u0007H\u0002J\u0016\u0010\u0015\u001a\u00020\u00162\f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\b0\u0007H\u0002J\u0016\u0010\u0018\u001a\u00020\u00162\u0006\u0010\u0019\u001a\u00020\u001aH\u0086@\u00a2\u0006\u0002\u0010\u001bJ\u0016\u0010\u001c\u001a\u00020\u00162\u0006\u0010\u001d\u001a\u00020\bH\u0086@\u00a2\u0006\u0002\u0010\u001eR\u001a\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\r\u001a\n \u000f*\u0004\u0018\u00010\u000e0\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u0010\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013\u00a8\u0006 "}, d2 = {"Lcom/podcastplayer/app/data/local/SavedPodcastsStorage;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "_savedPodcasts", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "Lcom/podcastplayer/app/domain/model/Podcast;", "gson", "Lcom/google/gson/Gson;", "mutex", "Lkotlinx/coroutines/sync/Mutex;", "prefs", "Landroid/content/SharedPreferences;", "kotlin.jvm.PlatformType", "savedPodcasts", "Lkotlinx/coroutines/flow/StateFlow;", "getSavedPodcasts", "()Lkotlinx/coroutines/flow/StateFlow;", "load", "persist", "", "list", "remove", "podcastId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "save", "podcast", "(Lcom/podcastplayer/app/domain/model/Podcast;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_debug"})
public final class SavedPodcastsStorage {
    private final android.content.SharedPreferences prefs = null;
    @org.jetbrains.annotations.NotNull
    private final com.google.gson.Gson gson = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.sync.Mutex mutex = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.podcastplayer.app.domain.model.Podcast>> _savedPodcasts = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.podcastplayer.app.domain.model.Podcast>> savedPodcasts = null;
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String PREFS_NAME = "saved_podcasts";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KEY_PODCASTS = "podcasts";
    @org.jetbrains.annotations.NotNull
    public static final com.podcastplayer.app.data.local.SavedPodcastsStorage.Companion Companion = null;
    
    public SavedPodcastsStorage(@org.jetbrains.annotations.NotNull
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.podcastplayer.app.domain.model.Podcast>> getSavedPodcasts() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object save(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Podcast podcast, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object remove(@org.jetbrains.annotations.NotNull
    java.lang.String podcastId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final void persist(java.util.List<com.podcastplayer.app.domain.model.Podcast> list) {
    }
    
    private final java.util.List<com.podcastplayer.app.domain.model.Podcast> load() {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/podcastplayer/app/data/local/SavedPodcastsStorage$Companion;", "", "()V", "KEY_PODCASTS", "", "PREFS_NAME", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}