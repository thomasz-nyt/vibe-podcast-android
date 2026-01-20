package com.podcastplayer.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PodcastDatabase_Impl extends PodcastDatabase {
  private volatile DownloadedEpisodeDao _downloadedEpisodeDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_episodes` (`id` TEXT NOT NULL, `podcastId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `pubDate` INTEGER, `audioUrl` TEXT NOT NULL, `duration` INTEGER, `localPath` TEXT NOT NULL, `fileSize` INTEGER NOT NULL, `downloadDate` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'dadac6513e698f08b6040e962ee127ea')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `downloaded_episodes`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsDownloadedEpisodes = new HashMap<String, TableInfo.Column>(10);
        _columnsDownloadedEpisodes.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("podcastId", new TableInfo.Column("podcastId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("description", new TableInfo.Column("description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("pubDate", new TableInfo.Column("pubDate", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("audioUrl", new TableInfo.Column("audioUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("duration", new TableInfo.Column("duration", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("localPath", new TableInfo.Column("localPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("fileSize", new TableInfo.Column("fileSize", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDownloadedEpisodes.put("downloadDate", new TableInfo.Column("downloadDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDownloadedEpisodes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDownloadedEpisodes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDownloadedEpisodes = new TableInfo("downloaded_episodes", _columnsDownloadedEpisodes, _foreignKeysDownloadedEpisodes, _indicesDownloadedEpisodes);
        final TableInfo _existingDownloadedEpisodes = TableInfo.read(db, "downloaded_episodes");
        if (!_infoDownloadedEpisodes.equals(_existingDownloadedEpisodes)) {
          return new RoomOpenHelper.ValidationResult(false, "downloaded_episodes(com.podcastplayer.app.data.local.DownloadedEpisodeEntity).\n"
                  + " Expected:\n" + _infoDownloadedEpisodes + "\n"
                  + " Found:\n" + _existingDownloadedEpisodes);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "dadac6513e698f08b6040e962ee127ea", "c6e9e24a29e044d9813588d8c0b031b6");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "downloaded_episodes");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `downloaded_episodes`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(DownloadedEpisodeDao.class, DownloadedEpisodeDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public DownloadedEpisodeDao downloadedEpisodeDao() {
    if (_downloadedEpisodeDao != null) {
      return _downloadedEpisodeDao;
    } else {
      synchronized(this) {
        if(_downloadedEpisodeDao == null) {
          _downloadedEpisodeDao = new DownloadedEpisodeDao_Impl(this);
        }
        return _downloadedEpisodeDao;
      }
    }
  }
}
