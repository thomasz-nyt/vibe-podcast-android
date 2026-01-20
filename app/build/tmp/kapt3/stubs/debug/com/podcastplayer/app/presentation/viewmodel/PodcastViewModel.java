package com.podcastplayer.app.presentation.viewmodel;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0013\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ$\u0010&\u001a\b\u0012\u0004\u0012\u00020(0\'2\u0006\u0010)\u001a\u00020*H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b+\u0010,J$\u0010-\u001a\b\u0012\u0004\u0012\u00020*0\'2\u0006\u0010.\u001a\u00020\fH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b/\u00100J\u0010\u00101\u001a\u00020(2\u0006\u00102\u001a\u00020\u0010H\u0002J\u0010\u00103\u001a\u00020(2\u0006\u00102\u001a\u00020\u0010H\u0002J\b\u00104\u001a\u00020(H\u0002J\u000e\u00105\u001a\u00020(2\u0006\u0010.\u001a\u00020\fJ\b\u00106\u001a\u00020(H\u0002J\u000e\u00107\u001a\u00020(2\u0006\u00108\u001a\u00020*J\u000e\u00109\u001a\u00020(2\u0006\u00102\u001a\u00020\u0010J\u000e\u0010:\u001a\u00020(2\u0006\u0010;\u001a\u00020*J\u000e\u0010<\u001a\u00020(2\u0006\u00102\u001a\u00020\u0010R\u001a\u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\u000f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00100\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0011\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\f0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0012\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00100\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00140\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u0015\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\f0\u000b0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R\u0010\u0010\u0019\u001a\u0004\u0018\u00010\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u000e0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0018R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001d\u001a\u0004\u0018\u00010\u001aX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u001e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00100\u000b0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0018R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010 \u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\f0\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b!\u0010\u0018R\u0019\u0010\"\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00100\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b#\u0010\u0018R\u0017\u0010$\u001a\b\u0012\u0004\u0012\u00020\u00140\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b%\u0010\u0018\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006="}, d2 = {"Lcom/podcastplayer/app/presentation/viewmodel/PodcastViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/podcastplayer/app/data/repository/PodcastRepository;", "downloadManager", "Lcom/podcastplayer/app/data/repository/DownloadManager;", "savedPodcastsStorage", "Lcom/podcastplayer/app/data/local/SavedPodcastsStorage;", "(Lcom/podcastplayer/app/data/repository/PodcastRepository;Lcom/podcastplayer/app/data/repository/DownloadManager;Lcom/podcastplayer/app/data/local/SavedPodcastsStorage;)V", "_downloadedEpisodes", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "Lcom/podcastplayer/app/domain/model/Episode;", "_episodesUiState", "Lcom/podcastplayer/app/presentation/viewmodel/EpisodesUiState;", "_savedPodcasts", "Lcom/podcastplayer/app/domain/model/Podcast;", "_selectedEpisode", "_selectedPodcast", "_uiState", "Lcom/podcastplayer/app/presentation/viewmodel/PodcastUiState;", "downloadedEpisodes", "Lkotlinx/coroutines/flow/StateFlow;", "getDownloadedEpisodes", "()Lkotlinx/coroutines/flow/StateFlow;", "downloadsJob", "Lkotlinx/coroutines/Job;", "episodesUiState", "getEpisodesUiState", "savedJob", "savedPodcasts", "getSavedPodcasts", "selectedEpisode", "getSelectedEpisode", "selectedPodcast", "getSelectedPodcast", "uiState", "getUiState", "deleteDownload", "Lkotlin/Result;", "", "episodeId", "", "deleteDownload-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "downloadEpisode", "episode", "downloadEpisode-gIAlu-s", "(Lcom/podcastplayer/app/domain/model/Episode;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "loadEpisodes", "podcast", "observeDownloads", "observeSaved", "playEpisode", "refreshEpisodesWithDownloads", "removeSavedPodcast", "podcastId", "savePodcast", "searchPodcasts", "query", "selectPodcast", "app_debug"})
public final class PodcastViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.repository.PodcastRepository repository = null;
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.repository.DownloadManager downloadManager = null;
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.local.SavedPodcastsStorage savedPodcastsStorage = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.podcastplayer.app.presentation.viewmodel.PodcastUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.presentation.viewmodel.PodcastUiState> uiState = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.podcastplayer.app.presentation.viewmodel.EpisodesUiState> _episodesUiState = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.presentation.viewmodel.EpisodesUiState> episodesUiState = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.podcastplayer.app.domain.model.Podcast> _selectedPodcast = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.Podcast> selectedPodcast = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<com.podcastplayer.app.domain.model.Episode> _selectedEpisode = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.Episode> selectedEpisode = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.podcastplayer.app.domain.model.Episode>> _downloadedEpisodes = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.podcastplayer.app.domain.model.Episode>> downloadedEpisodes = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.podcastplayer.app.domain.model.Podcast>> _savedPodcasts = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.podcastplayer.app.domain.model.Podcast>> savedPodcasts = null;
    @org.jetbrains.annotations.Nullable
    private kotlinx.coroutines.Job downloadsJob;
    @org.jetbrains.annotations.Nullable
    private kotlinx.coroutines.Job savedJob;
    
    public PodcastViewModel(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.repository.PodcastRepository repository, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.repository.DownloadManager downloadManager, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.local.SavedPodcastsStorage savedPodcastsStorage) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.presentation.viewmodel.PodcastUiState> getUiState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.presentation.viewmodel.EpisodesUiState> getEpisodesUiState() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.Podcast> getSelectedPodcast() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<com.podcastplayer.app.domain.model.Episode> getSelectedEpisode() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.podcastplayer.app.domain.model.Episode>> getDownloadedEpisodes() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.podcastplayer.app.domain.model.Podcast>> getSavedPodcasts() {
        return null;
    }
    
    public final void searchPodcasts(@org.jetbrains.annotations.NotNull
    java.lang.String query) {
    }
    
    public final void selectPodcast(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Podcast podcast) {
    }
    
    private final void observeDownloads(com.podcastplayer.app.domain.model.Podcast podcast) {
    }
    
    private final void observeSaved() {
    }
    
    private final void refreshEpisodesWithDownloads() {
    }
    
    private final void loadEpisodes(com.podcastplayer.app.domain.model.Podcast podcast) {
    }
    
    public final void playEpisode(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Episode episode) {
    }
    
    public final void savePodcast(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Podcast podcast) {
    }
    
    public final void removeSavedPodcast(@org.jetbrains.annotations.NotNull
    java.lang.String podcastId) {
    }
}