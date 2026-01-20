package com.podcastplayer.app.data.local;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0007\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/podcastplayer/app/data/local/DatabaseProvider;", "", "()V", "DATABASE_NAME", "", "instance", "Lcom/podcastplayer/app/data/local/PodcastDatabase;", "getDatabase", "context", "Landroid/content/Context;", "app_debug"})
public final class DatabaseProvider {
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String DATABASE_NAME = "podcast_database";
    @kotlin.jvm.Volatile
    @org.jetbrains.annotations.Nullable
    private static volatile com.podcastplayer.app.data.local.PodcastDatabase instance;
    @org.jetbrains.annotations.NotNull
    public static final com.podcastplayer.app.data.local.DatabaseProvider INSTANCE = null;
    
    private DatabaseProvider() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final com.podcastplayer.app.data.local.PodcastDatabase getDatabase(@org.jetbrains.annotations.NotNull
    android.content.Context context) {
        return null;
    }
}