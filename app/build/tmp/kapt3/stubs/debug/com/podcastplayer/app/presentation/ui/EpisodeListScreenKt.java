package com.podcastplayer.app.presentation.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000>\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\u0002\u001aL\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\b\u0010\u0004\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0006\u001a\u00020\u00072\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00010\tH\u0007\u001aN\u0010\f\u001a\u00020\u00012\b\u0010\r\u001a\u0004\u0018\u00010\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\u000e\b\u0002\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00010\t2\f\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00010\tH\u0007\u001a\u0010\u0010\u0016\u001a\u00020\u00052\u0006\u0010\u0002\u001a\u00020\u0003H\u0002\u001a\u0010\u0010\u0017\u001a\u00020\u00052\u0006\u0010\u0018\u001a\u00020\u0019H\u0002\u001a\u000e\u0010\u001a\u001a\u00020\u0005*\u0004\u0018\u00010\u0005H\u0002\u00a8\u0006\u001b"}, d2 = {"EpisodeItem", "", "episode", "Lcom/podcastplayer/app/domain/model/Episode;", "artworkUrl", "", "isDownloaded", "", "onClick", "Lkotlin/Function0;", "onDownload", "onDelete", "EpisodeListScreen", "podcast", "Lcom/podcastplayer/app/domain/model/Podcast;", "podcastViewModel", "Lcom/podcastplayer/app/presentation/viewmodel/PodcastViewModel;", "playerViewModel", "Lcom/podcastplayer/app/presentation/viewmodel/PlayerViewModel;", "onBack", "onPlayEpisode", "onOpenPlayer", "buildMetadataLabel", "formatDuration", "ms", "", "stripHtml", "app_debug"})
public final class EpisodeListScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable
    public static final void EpisodeListScreen(@org.jetbrains.annotations.Nullable
    com.podcastplayer.app.domain.model.Podcast podcast, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.presentation.viewmodel.PodcastViewModel podcastViewModel, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.presentation.viewmodel.PlayerViewModel playerViewModel, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onBack, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onPlayEpisode, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onOpenPlayer) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void EpisodeItem(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Episode episode, @org.jetbrains.annotations.Nullable
    java.lang.String artworkUrl, boolean isDownloaded, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDownload, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDelete) {
    }
    
    private static final java.lang.String stripHtml(java.lang.String $this$stripHtml) {
        return null;
    }
    
    private static final java.lang.String buildMetadataLabel(com.podcastplayer.app.domain.model.Episode episode) {
        return null;
    }
    
    private static final java.lang.String formatDuration(long ms) {
        return null;
    }
}