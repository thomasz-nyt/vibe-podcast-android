package com.podcastplayer.app.data.local;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0018\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\nJ\u001c\u0010\f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u000e0\r2\u0006\u0010\u000f\u001a\u00020\tH\'J\u0016\u0010\u0010\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0011\u001a\u00020\u00122\u0006\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\n\u00a8\u0006\u0013"}, d2 = {"Lcom/podcastplayer/app/data/local/DownloadedEpisodeDao;", "", "deleteEpisode", "", "episode", "Lcom/podcastplayer/app/data/local/DownloadedEpisodeEntity;", "(Lcom/podcastplayer/app/data/local/DownloadedEpisodeEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteEpisodeById", "episodeId", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEpisodeById", "getEpisodesByPodcast", "Lkotlinx/coroutines/flow/Flow;", "", "podcastId", "insertEpisode", "isEpisodeDownloaded", "", "app_debug"})
@androidx.room.Dao
public abstract interface DownloadedEpisodeDao {
    
    @androidx.room.Query(value = "SELECT * FROM downloaded_episodes WHERE podcastId = :podcastId")
    @org.jetbrains.annotations.NotNull
    public abstract kotlinx.coroutines.flow.Flow<java.util.List<com.podcastplayer.app.data.local.DownloadedEpisodeEntity>> getEpisodesByPodcast(@org.jetbrains.annotations.NotNull
    java.lang.String podcastId);
    
    @androidx.room.Query(value = "SELECT * FROM downloaded_episodes WHERE id = :episodeId")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getEpisodeById(@org.jetbrains.annotations.NotNull
    java.lang.String episodeId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.podcastplayer.app.data.local.DownloadedEpisodeEntity> $completion);
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object insertEpisode(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.local.DownloadedEpisodeEntity episode, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Delete
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object deleteEpisode(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.local.DownloadedEpisodeEntity episode, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM downloaded_episodes WHERE id = :episodeId")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object deleteEpisodeById(@org.jetbrains.annotations.NotNull
    java.lang.String episodeId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT EXISTS(SELECT 1 FROM downloaded_episodes WHERE id = :episodeId)")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object isEpisodeDownloaded(@org.jetbrains.annotations.NotNull
    java.lang.String episodeId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion);
}