package com.podcastplayer.app.presentation.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u00002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\u001a:\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u00032\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00010\u00052\u0010\b\u0002\u0010\u0006\u001a\n\u0012\u0004\u0012\u00020\u0001\u0018\u00010\u00052\b\b\u0002\u0010\u0007\u001a\u00020\bH\u0007\u001a:\u0010\t\u001a\u00020\u00012\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0012\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\u000f2\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00010\u0005H\u0007\u001a\u0016\u0010\u0011\u001a\u00020\u00012\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00010\u0005H\u0007\u00a8\u0006\u0013"}, d2 = {"PodcastItem", "", "podcast", "Lcom/podcastplayer/app/domain/model/Podcast;", "onClick", "Lkotlin/Function0;", "onSaveToggle", "isSaved", "", "PodcastListScreen", "viewModel", "Lcom/podcastplayer/app/presentation/viewmodel/PodcastViewModel;", "playerViewModel", "Lcom/podcastplayer/app/presentation/viewmodel/PlayerViewModel;", "onPodcastSelected", "Lkotlin/Function1;", "onOpenPlayer", "SavedEmptyStateCard", "onBrowse", "app_debug"})
public final class PodcastListScreenKt {
    
    @kotlin.OptIn(markerClass = {androidx.compose.material3.ExperimentalMaterial3Api.class})
    @androidx.compose.runtime.Composable
    public static final void PodcastListScreen(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.presentation.viewmodel.PodcastViewModel viewModel, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.presentation.viewmodel.PlayerViewModel playerViewModel, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.podcastplayer.app.domain.model.Podcast, kotlin.Unit> onPodcastSelected, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onOpenPlayer) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void SavedEmptyStateCard(@org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onBrowse) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void PodcastItem(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.domain.model.Podcast podcast, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onClick, @org.jetbrains.annotations.Nullable
    kotlin.jvm.functions.Function0<kotlin.Unit> onSaveToggle, boolean isSaved) {
    }
}