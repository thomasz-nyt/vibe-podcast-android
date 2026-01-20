package com.podcastplayer.app.presentation.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ%\u0010\t\u001a\u0002H\n\"\b\b\u0000\u0010\n*\u00020\u000b2\f\u0010\f\u001a\b\u0012\u0004\u0012\u0002H\n0\rH\u0016\u00a2\u0006\u0002\u0010\u000eR\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/podcastplayer/app/presentation/ui/PodcastViewModelFactory;", "Landroidx/lifecycle/ViewModelProvider$Factory;", "repository", "Lcom/podcastplayer/app/data/repository/PodcastRepository;", "downloadManager", "Lcom/podcastplayer/app/data/repository/DownloadManager;", "savedPodcastsStorage", "Lcom/podcastplayer/app/data/local/SavedPodcastsStorage;", "(Lcom/podcastplayer/app/data/repository/PodcastRepository;Lcom/podcastplayer/app/data/repository/DownloadManager;Lcom/podcastplayer/app/data/local/SavedPodcastsStorage;)V", "create", "T", "Landroidx/lifecycle/ViewModel;", "modelClass", "Ljava/lang/Class;", "(Ljava/lang/Class;)Landroidx/lifecycle/ViewModel;", "app_debug"})
public final class PodcastViewModelFactory implements androidx.lifecycle.ViewModelProvider.Factory {
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.repository.PodcastRepository repository = null;
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.repository.DownloadManager downloadManager = null;
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.local.SavedPodcastsStorage savedPodcastsStorage = null;
    
    public PodcastViewModelFactory(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.repository.PodcastRepository repository, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.repository.DownloadManager downloadManager, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.local.SavedPodcastsStorage savedPodcastsStorage) {
        super();
    }
    
    @java.lang.Override
    @kotlin.Suppress(names = {"UNCHECKED_CAST"})
    @org.jetbrains.annotations.NotNull
    public <T extends androidx.lifecycle.ViewModel>T create(@org.jetbrains.annotations.NotNull
    java.lang.Class<T> modelClass) {
        return null;
    }
}