package com.podcastplayer.app.data.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J$\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000e2\u0006\u0010\u0010\u001a\u00020\u0011H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0012\u0010\u0013J$\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00110\u000e2\u0006\u0010\u0015\u001a\u00020\u0016H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0017\u0010\u0018J\u0018\u0010\u0019\u001a\u0004\u0018\u00010\u001a2\u0006\u0010\u0010\u001a\u00020\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0013J\u001c\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00160\u001c2\u0006\u0010\u001d\u001a\u00020\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0013J\u001a\u0010\u001e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\u001c0\u001f2\u0006\u0010\u001d\u001a\u00020\u0011J\u0016\u0010 \u001a\u00020!2\u0006\u0010\u0010\u001a\u00020\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0013J\f\u0010\"\u001a\u00020\u0016*\u00020\u001aH\u0002J\u0014\u0010#\u001a\u00020\u001a*\u00020\u00162\u0006\u0010$\u001a\u00020\u0011H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\u00020\u00068BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\n8BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\b\u000b\u0010\f\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006%"}, d2 = {"Lcom/podcastplayer/app/data/repository/DownloadManager;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "dao", "Lcom/podcastplayer/app/data/local/DownloadedEpisodeDao;", "getDao", "()Lcom/podcastplayer/app/data/local/DownloadedEpisodeDao;", "downloadDir", "Ljava/io/File;", "getDownloadDir", "()Ljava/io/File;", "deleteEpisode", "Lkotlin/Result;", "", "episodeId", "", "deleteEpisode-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadEpisode", "episode", "Lcom/podcastplayer/app/domain/model/Episode;", "downloadEpisode-gIAlu-s", "(Lcom/podcastplayer/app/domain/model/Episode;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getDownloadedEpisode", "Lcom/podcastplayer/app/data/local/DownloadedEpisodeEntity;", "getDownloadedEpisodes", "", "podcastId", "getDownloadedEpisodesFlow", "Lkotlinx/coroutines/flow/Flow;", "isEpisodeDownloaded", "", "toDomain", "toEntity", "localPath", "app_debug"})
public final class DownloadManager {
    @org.jetbrains.annotations.NotNull
    private final android.content.Context context = null;
    
    public DownloadManager(@org.jetbrains.annotations.NotNull
    android.content.Context context) {
        super();
    }
    
    private final com.podcastplayer.app.data.local.DownloadedEpisodeDao getDao() {
        return null;
    }
    
    private final java.io.File getDownloadDir() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object isEpisodeDownloaded(@org.jetbrains.annotations.NotNull
    java.lang.String episodeId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object getDownloadedEpisodes(@org.jetbrains.annotations.NotNull
    java.lang.String podcastId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.podcastplayer.app.domain.model.Episode>> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.Flow<java.util.List<com.podcastplayer.app.domain.model.Episode>> getDownloadedEpisodesFlow(@org.jetbrains.annotations.NotNull
    java.lang.String podcastId) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable
    public final java.lang.Object getDownloadedEpisode(@org.jetbrains.annotations.NotNull
    java.lang.String episodeId, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super com.podcastplayer.app.data.local.DownloadedEpisodeEntity> $completion) {
        return null;
    }
    
    private final com.podcastplayer.app.data.local.DownloadedEpisodeEntity toEntity(com.podcastplayer.app.domain.model.Episode $this$toEntity, java.lang.String localPath) {
        return null;
    }
    
    private final com.podcastplayer.app.domain.model.Episode toDomain(com.podcastplayer.app.data.local.DownloadedEpisodeEntity $this$toDomain) {
        return null;
    }
}