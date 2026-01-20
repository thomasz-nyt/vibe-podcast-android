package com.podcastplayer.app.data.remote;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\bf\u0018\u0000 \u000b2\u00020\u0001:\u0001\u000bJ2\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0005\u001a\u00020\u00062\b\b\u0003\u0010\u0007\u001a\u00020\u00062\b\b\u0003\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\n\u00a8\u0006\f"}, d2 = {"Lcom/podcastplayer/app/data/remote/iTunesApi;", "", "searchPodcasts", "Lretrofit2/Response;", "Lcom/podcastplayer/app/domain/model/PodcastSearchResponse;", "term", "", "media", "limit", "", "(Ljava/lang/String;Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_debug"})
public abstract interface iTunesApi {
    @org.jetbrains.annotations.NotNull
    public static final com.podcastplayer.app.data.remote.iTunesApi.Companion Companion = null;
    
    @retrofit2.http.GET(value = "search")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object searchPodcasts(@retrofit2.http.Query(value = "term")
    @org.jetbrains.annotations.NotNull
    java.lang.String term, @retrofit2.http.Query(value = "media")
    @org.jetbrains.annotations.NotNull
    java.lang.String media, @retrofit2.http.Query(value = "limit")
    int limit, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super retrofit2.Response<com.podcastplayer.app.domain.model.PodcastSearchResponse>> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0005\u001a\u00020\u0006R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/podcastplayer/app/data/remote/iTunesApi$Companion;", "", "()V", "BASE_URL", "", "create", "Lcom/podcastplayer/app/data/remote/iTunesApi;", "app_debug"})
    public static final class Companion {
        @org.jetbrains.annotations.NotNull
        private static final java.lang.String BASE_URL = "https://itunes.apple.com/";
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull
        public final com.podcastplayer.app.data.remote.iTunesApi create() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}