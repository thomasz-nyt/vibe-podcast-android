package com.podcastplayer.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DownloadedEpisodeDao_Impl implements DownloadedEpisodeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DownloadedEpisodeEntity> __insertionAdapterOfDownloadedEpisodeEntity;

  private final EntityDeletionOrUpdateAdapter<DownloadedEpisodeEntity> __deletionAdapterOfDownloadedEpisodeEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteEpisodeById;

  public DownloadedEpisodeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDownloadedEpisodeEntity = new EntityInsertionAdapter<DownloadedEpisodeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `downloaded_episodes` (`id`,`podcastId`,`title`,`description`,`pubDate`,`audioUrl`,`duration`,`localPath`,`fileSize`,`downloadDate`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadedEpisodeEntity entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getPodcastId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getPodcastId());
        }
        if (entity.getTitle() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTitle());
        }
        if (entity.getDescription() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getDescription());
        }
        if (entity.getPubDate() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getPubDate());
        }
        if (entity.getAudioUrl() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getAudioUrl());
        }
        if (entity.getDuration() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getDuration());
        }
        if (entity.getLocalPath() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getLocalPath());
        }
        statement.bindLong(9, entity.getFileSize());
        statement.bindLong(10, entity.getDownloadDate());
      }
    };
    this.__deletionAdapterOfDownloadedEpisodeEntity = new EntityDeletionOrUpdateAdapter<DownloadedEpisodeEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `downloaded_episodes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DownloadedEpisodeEntity entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
      }
    };
    this.__preparedStmtOfDeleteEpisodeById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM downloaded_episodes WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertEpisode(final DownloadedEpisodeEntity episode,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDownloadedEpisodeEntity.insert(episode);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteEpisode(final DownloadedEpisodeEntity episode,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfDownloadedEpisodeEntity.handle(episode);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteEpisodeById(final String episodeId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteEpisodeById.acquire();
        int _argIndex = 1;
        if (episodeId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, episodeId);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteEpisodeById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DownloadedEpisodeEntity>> getEpisodesByPodcast(final String podcastId) {
    final String _sql = "SELECT * FROM downloaded_episodes WHERE podcastId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (podcastId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, podcastId);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"downloaded_episodes"}, new Callable<List<DownloadedEpisodeEntity>>() {
      @Override
      @NonNull
      public List<DownloadedEpisodeEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPodcastId = CursorUtil.getColumnIndexOrThrow(_cursor, "podcastId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPubDate = CursorUtil.getColumnIndexOrThrow(_cursor, "pubDate");
          final int _cursorIndexOfAudioUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "audioUrl");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfLocalPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localPath");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfDownloadDate = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadDate");
          final List<DownloadedEpisodeEntity> _result = new ArrayList<DownloadedEpisodeEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DownloadedEpisodeEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpPodcastId;
            if (_cursor.isNull(_cursorIndexOfPodcastId)) {
              _tmpPodcastId = null;
            } else {
              _tmpPodcastId = _cursor.getString(_cursorIndexOfPodcastId);
            }
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final Long _tmpPubDate;
            if (_cursor.isNull(_cursorIndexOfPubDate)) {
              _tmpPubDate = null;
            } else {
              _tmpPubDate = _cursor.getLong(_cursorIndexOfPubDate);
            }
            final String _tmpAudioUrl;
            if (_cursor.isNull(_cursorIndexOfAudioUrl)) {
              _tmpAudioUrl = null;
            } else {
              _tmpAudioUrl = _cursor.getString(_cursorIndexOfAudioUrl);
            }
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpLocalPath;
            if (_cursor.isNull(_cursorIndexOfLocalPath)) {
              _tmpLocalPath = null;
            } else {
              _tmpLocalPath = _cursor.getString(_cursorIndexOfLocalPath);
            }
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final long _tmpDownloadDate;
            _tmpDownloadDate = _cursor.getLong(_cursorIndexOfDownloadDate);
            _item = new DownloadedEpisodeEntity(_tmpId,_tmpPodcastId,_tmpTitle,_tmpDescription,_tmpPubDate,_tmpAudioUrl,_tmpDuration,_tmpLocalPath,_tmpFileSize,_tmpDownloadDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getEpisodeById(final String episodeId,
      final Continuation<? super DownloadedEpisodeEntity> $completion) {
    final String _sql = "SELECT * FROM downloaded_episodes WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (episodeId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, episodeId);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DownloadedEpisodeEntity>() {
      @Override
      @Nullable
      public DownloadedEpisodeEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPodcastId = CursorUtil.getColumnIndexOrThrow(_cursor, "podcastId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfPubDate = CursorUtil.getColumnIndexOrThrow(_cursor, "pubDate");
          final int _cursorIndexOfAudioUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "audioUrl");
          final int _cursorIndexOfDuration = CursorUtil.getColumnIndexOrThrow(_cursor, "duration");
          final int _cursorIndexOfLocalPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localPath");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfDownloadDate = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadDate");
          final DownloadedEpisodeEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpPodcastId;
            if (_cursor.isNull(_cursorIndexOfPodcastId)) {
              _tmpPodcastId = null;
            } else {
              _tmpPodcastId = _cursor.getString(_cursorIndexOfPodcastId);
            }
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final Long _tmpPubDate;
            if (_cursor.isNull(_cursorIndexOfPubDate)) {
              _tmpPubDate = null;
            } else {
              _tmpPubDate = _cursor.getLong(_cursorIndexOfPubDate);
            }
            final String _tmpAudioUrl;
            if (_cursor.isNull(_cursorIndexOfAudioUrl)) {
              _tmpAudioUrl = null;
            } else {
              _tmpAudioUrl = _cursor.getString(_cursorIndexOfAudioUrl);
            }
            final Long _tmpDuration;
            if (_cursor.isNull(_cursorIndexOfDuration)) {
              _tmpDuration = null;
            } else {
              _tmpDuration = _cursor.getLong(_cursorIndexOfDuration);
            }
            final String _tmpLocalPath;
            if (_cursor.isNull(_cursorIndexOfLocalPath)) {
              _tmpLocalPath = null;
            } else {
              _tmpLocalPath = _cursor.getString(_cursorIndexOfLocalPath);
            }
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final long _tmpDownloadDate;
            _tmpDownloadDate = _cursor.getLong(_cursorIndexOfDownloadDate);
            _result = new DownloadedEpisodeEntity(_tmpId,_tmpPodcastId,_tmpTitle,_tmpDescription,_tmpPubDate,_tmpAudioUrl,_tmpDuration,_tmpLocalPath,_tmpFileSize,_tmpDownloadDate);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object isEpisodeDownloaded(final String episodeId,
      final Continuation<? super Boolean> $completion) {
    final String _sql = "SELECT EXISTS(SELECT 1 FROM downloaded_episodes WHERE id = ?)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (episodeId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, episodeId);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Boolean>() {
      @Override
      @NonNull
      public Boolean call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Boolean _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp == null ? null : _tmp != 0;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
