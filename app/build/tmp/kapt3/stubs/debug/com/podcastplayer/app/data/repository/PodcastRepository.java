package com.podcastplayer.app.data.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J2\u0010\u0007\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\n0\t0\b2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\fH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u000e\u0010\u000fJ*\u0010\u0010\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00110\t0\b2\u0006\u0010\u0012\u001a\u00020\fH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0013\u0010\u0014J\f\u0010\u0015\u001a\u00020\u0011*\u00020\u0016H\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u0017"}, d2 = {"Lcom/podcastplayer/app/data/repository/PodcastRepository;", "", "iTunesApi", "Lcom/podcastplayer/app/data/remote/iTunesApi;", "rssParser", "Lcom/podcastplayer/app/data/remote/RssParser;", "(Lcom/podcastplayer/app/data/remote/iTunesApi;Lcom/podcastplayer/app/data/remote/RssParser;)V", "getEpisodes", "Lkotlin/Result;", "", "Lcom/podcastplayer/app/domain/model/Episode;", "feedUrl", "", "podcastId", "getEpisodes-0E7RQCE", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "searchPodcasts", "Lcom/podcastplayer/app/domain/model/Podcast;", "query", "searchPodcasts-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "toDomain", "Lcom/podcastplayer/app/domain/model/PodcastDto;", "app_debug"})
public final class PodcastRepository {
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.remote.iTunesApi iTunesApi = null;
    @org.jetbrains.annotations.NotNull
    private final com.podcastplayer.app.data.remote.RssParser rssParser = null;
    
    public PodcastRepository(@org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.remote.iTunesApi iTunesApi, @org.jetbrains.annotations.NotNull
    com.podcastplayer.app.data.remote.RssParser rssParser) {
        super();
    }
    
    private final com.podcastplayer.app.domain.model.Podcast toDomain(com.podcastplayer.app.domain.model.PodcastDto $this$toDomain) {
        return null;
    }
}