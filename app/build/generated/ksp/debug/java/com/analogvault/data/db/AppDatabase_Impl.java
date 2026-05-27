package com.analogvault.data.db;

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
public final class AppDatabase_Impl extends AppDatabase {
  private volatile FilmDao _filmDao;

  private volatile CameraDao _cameraDao;

  private volatile LensDao _lensDao;

  private volatile AccessoryDao _accessoryDao;

  private volatile RollDao _rollDao;

  private volatile ChemicalDao _chemicalDao;

  private volatile ZoomLevelDao _zoomLevelDao;

  private volatile SettingDao _settingDao;

  private volatile BulkRollDao _bulkRollDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `films` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT NOT NULL, `type` TEXT NOT NULL, `iso` INTEGER NOT NULL, `shots` INTEGER NOT NULL, `filmFormat` TEXT NOT NULL, `frameCount` INTEGER NOT NULL, `expiryDate` TEXT NOT NULL, `storage` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cameras` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT NOT NULL, `format` TEXT NOT NULL, `mfFormat` TEXT NOT NULL, `lensSystem` TEXT NOT NULL, `condition` TEXT NOT NULL, `mount` TEXT NOT NULL, `adapterMounts` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `lenses` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT NOT NULL, `focalLength` TEXT NOT NULL, `maxAperture` TEXT NOT NULL, `mount` TEXT NOT NULL, `condition` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `accessories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `brand` TEXT NOT NULL, `condition` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `rolls` (`id` TEXT NOT NULL, `filmId` TEXT NOT NULL, `cameraId` TEXT NOT NULL, `cameraLensId` TEXT NOT NULL, `startDate` TEXT NOT NULL, `finished` INTEGER NOT NULL, `developed` INTEGER NOT NULL, `scanned` INTEGER NOT NULL, `shots` TEXT NOT NULL, `devLog` TEXT, `scanLog` TEXT, `pushIso` TEXT NOT NULL, `totalShots` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `chemicals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `dilution` TEXT NOT NULL, `volume` TEXT NOT NULL, `volumeUnit` TEXT NOT NULL, `mixDate` TEXT NOT NULL, `maxRolls` TEXT NOT NULL, `baseDevTime` TEXT NOT NULL, `timeAdjPerRoll` TEXT NOT NULL, `manualRolls` INTEGER NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `zoom_levels` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `mm` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `bulk_rolls` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT NOT NULL, `type` TEXT NOT NULL, `iso` INTEGER NOT NULL, `totalFrames` INTEGER NOT NULL, `usedFrames` INTEGER NOT NULL, `notes` TEXT NOT NULL, `purchaseDate` TEXT NOT NULL, `expiryDate` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2ba08c7aedd34949297dc3a94e3f02b3')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `films`");
        db.execSQL("DROP TABLE IF EXISTS `cameras`");
        db.execSQL("DROP TABLE IF EXISTS `lenses`");
        db.execSQL("DROP TABLE IF EXISTS `accessories`");
        db.execSQL("DROP TABLE IF EXISTS `rolls`");
        db.execSQL("DROP TABLE IF EXISTS `chemicals`");
        db.execSQL("DROP TABLE IF EXISTS `zoom_levels`");
        db.execSQL("DROP TABLE IF EXISTS `settings`");
        db.execSQL("DROP TABLE IF EXISTS `bulk_rolls`");
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
        final HashMap<String, TableInfo.Column> _columnsFilms = new HashMap<String, TableInfo.Column>(12);
        _columnsFilms.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("brand", new TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("iso", new TableInfo.Column("iso", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("shots", new TableInfo.Column("shots", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("filmFormat", new TableInfo.Column("filmFormat", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("frameCount", new TableInfo.Column("frameCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("expiryDate", new TableInfo.Column("expiryDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("storage", new TableInfo.Column("storage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("quantity", new TableInfo.Column("quantity", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFilms.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFilms = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFilms = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFilms = new TableInfo("films", _columnsFilms, _foreignKeysFilms, _indicesFilms);
        final TableInfo _existingFilms = TableInfo.read(db, "films");
        if (!_infoFilms.equals(_existingFilms)) {
          return new RoomOpenHelper.ValidationResult(false, "films(com.analogvault.data.model.FilmStock).\n"
                  + " Expected:\n" + _infoFilms + "\n"
                  + " Found:\n" + _existingFilms);
        }
        final HashMap<String, TableInfo.Column> _columnsCameras = new HashMap<String, TableInfo.Column>(10);
        _columnsCameras.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("brand", new TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("format", new TableInfo.Column("format", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("mfFormat", new TableInfo.Column("mfFormat", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("lensSystem", new TableInfo.Column("lensSystem", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("condition", new TableInfo.Column("condition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("mount", new TableInfo.Column("mount", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("adapterMounts", new TableInfo.Column("adapterMounts", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCameras.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCameras = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCameras = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCameras = new TableInfo("cameras", _columnsCameras, _foreignKeysCameras, _indicesCameras);
        final TableInfo _existingCameras = TableInfo.read(db, "cameras");
        if (!_infoCameras.equals(_existingCameras)) {
          return new RoomOpenHelper.ValidationResult(false, "cameras(com.analogvault.data.model.Camera).\n"
                  + " Expected:\n" + _infoCameras + "\n"
                  + " Found:\n" + _existingCameras);
        }
        final HashMap<String, TableInfo.Column> _columnsLenses = new HashMap<String, TableInfo.Column>(7);
        _columnsLenses.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLenses.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLenses.put("brand", new TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLenses.put("focalLength", new TableInfo.Column("focalLength", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLenses.put("maxAperture", new TableInfo.Column("maxAperture", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLenses.put("mount", new TableInfo.Column("mount", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLenses.put("condition", new TableInfo.Column("condition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLenses = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLenses = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoLenses = new TableInfo("lenses", _columnsLenses, _foreignKeysLenses, _indicesLenses);
        final TableInfo _existingLenses = TableInfo.read(db, "lenses");
        if (!_infoLenses.equals(_existingLenses)) {
          return new RoomOpenHelper.ValidationResult(false, "lenses(com.analogvault.data.model.Lens).\n"
                  + " Expected:\n" + _infoLenses + "\n"
                  + " Found:\n" + _existingLenses);
        }
        final HashMap<String, TableInfo.Column> _columnsAccessories = new HashMap<String, TableInfo.Column>(6);
        _columnsAccessories.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccessories.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccessories.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccessories.put("brand", new TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccessories.put("condition", new TableInfo.Column("condition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccessories.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAccessories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAccessories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAccessories = new TableInfo("accessories", _columnsAccessories, _foreignKeysAccessories, _indicesAccessories);
        final TableInfo _existingAccessories = TableInfo.read(db, "accessories");
        if (!_infoAccessories.equals(_existingAccessories)) {
          return new RoomOpenHelper.ValidationResult(false, "accessories(com.analogvault.data.model.Accessory).\n"
                  + " Expected:\n" + _infoAccessories + "\n"
                  + " Found:\n" + _existingAccessories);
        }
        final HashMap<String, TableInfo.Column> _columnsRolls = new HashMap<String, TableInfo.Column>(13);
        _columnsRolls.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("filmId", new TableInfo.Column("filmId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("cameraId", new TableInfo.Column("cameraId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("cameraLensId", new TableInfo.Column("cameraLensId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("startDate", new TableInfo.Column("startDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("finished", new TableInfo.Column("finished", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("developed", new TableInfo.Column("developed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("scanned", new TableInfo.Column("scanned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("shots", new TableInfo.Column("shots", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("devLog", new TableInfo.Column("devLog", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("scanLog", new TableInfo.Column("scanLog", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("pushIso", new TableInfo.Column("pushIso", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRolls.put("totalShots", new TableInfo.Column("totalShots", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRolls = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRolls = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRolls = new TableInfo("rolls", _columnsRolls, _foreignKeysRolls, _indicesRolls);
        final TableInfo _existingRolls = TableInfo.read(db, "rolls");
        if (!_infoRolls.equals(_existingRolls)) {
          return new RoomOpenHelper.ValidationResult(false, "rolls(com.analogvault.data.model.Roll).\n"
                  + " Expected:\n" + _infoRolls + "\n"
                  + " Found:\n" + _existingRolls);
        }
        final HashMap<String, TableInfo.Column> _columnsChemicals = new HashMap<String, TableInfo.Column>(12);
        _columnsChemicals.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("dilution", new TableInfo.Column("dilution", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("volume", new TableInfo.Column("volume", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("volumeUnit", new TableInfo.Column("volumeUnit", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("mixDate", new TableInfo.Column("mixDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("maxRolls", new TableInfo.Column("maxRolls", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("baseDevTime", new TableInfo.Column("baseDevTime", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("timeAdjPerRoll", new TableInfo.Column("timeAdjPerRoll", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("manualRolls", new TableInfo.Column("manualRolls", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChemicals.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysChemicals = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesChemicals = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoChemicals = new TableInfo("chemicals", _columnsChemicals, _foreignKeysChemicals, _indicesChemicals);
        final TableInfo _existingChemicals = TableInfo.read(db, "chemicals");
        if (!_infoChemicals.equals(_existingChemicals)) {
          return new RoomOpenHelper.ValidationResult(false, "chemicals(com.analogvault.data.model.Chemical).\n"
                  + " Expected:\n" + _infoChemicals + "\n"
                  + " Found:\n" + _existingChemicals);
        }
        final HashMap<String, TableInfo.Column> _columnsZoomLevels = new HashMap<String, TableInfo.Column>(3);
        _columnsZoomLevels.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZoomLevels.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsZoomLevels.put("mm", new TableInfo.Column("mm", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysZoomLevels = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesZoomLevels = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoZoomLevels = new TableInfo("zoom_levels", _columnsZoomLevels, _foreignKeysZoomLevels, _indicesZoomLevels);
        final TableInfo _existingZoomLevels = TableInfo.read(db, "zoom_levels");
        if (!_infoZoomLevels.equals(_existingZoomLevels)) {
          return new RoomOpenHelper.ValidationResult(false, "zoom_levels(com.analogvault.data.model.ZoomLevel).\n"
                  + " Expected:\n" + _infoZoomLevels + "\n"
                  + " Found:\n" + _existingZoomLevels);
        }
        final HashMap<String, TableInfo.Column> _columnsSettings = new HashMap<String, TableInfo.Column>(2);
        _columnsSettings.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSettings.put("value", new TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSettings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSettings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSettings = new TableInfo("settings", _columnsSettings, _foreignKeysSettings, _indicesSettings);
        final TableInfo _existingSettings = TableInfo.read(db, "settings");
        if (!_infoSettings.equals(_existingSettings)) {
          return new RoomOpenHelper.ValidationResult(false, "settings(com.analogvault.data.model.Setting).\n"
                  + " Expected:\n" + _infoSettings + "\n"
                  + " Found:\n" + _existingSettings);
        }
        final HashMap<String, TableInfo.Column> _columnsBulkRolls = new HashMap<String, TableInfo.Column>(10);
        _columnsBulkRolls.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("brand", new TableInfo.Column("brand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("iso", new TableInfo.Column("iso", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("totalFrames", new TableInfo.Column("totalFrames", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("usedFrames", new TableInfo.Column("usedFrames", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("purchaseDate", new TableInfo.Column("purchaseDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBulkRolls.put("expiryDate", new TableInfo.Column("expiryDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBulkRolls = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBulkRolls = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBulkRolls = new TableInfo("bulk_rolls", _columnsBulkRolls, _foreignKeysBulkRolls, _indicesBulkRolls);
        final TableInfo _existingBulkRolls = TableInfo.read(db, "bulk_rolls");
        if (!_infoBulkRolls.equals(_existingBulkRolls)) {
          return new RoomOpenHelper.ValidationResult(false, "bulk_rolls(com.analogvault.data.model.BulkRoll).\n"
                  + " Expected:\n" + _infoBulkRolls + "\n"
                  + " Found:\n" + _existingBulkRolls);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "2ba08c7aedd34949297dc3a94e3f02b3", "423bf1884aeaa54451ac99aaf4aa9cb1");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "films","cameras","lenses","accessories","rolls","chemicals","zoom_levels","settings","bulk_rolls");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `films`");
      _db.execSQL("DELETE FROM `cameras`");
      _db.execSQL("DELETE FROM `lenses`");
      _db.execSQL("DELETE FROM `accessories`");
      _db.execSQL("DELETE FROM `rolls`");
      _db.execSQL("DELETE FROM `chemicals`");
      _db.execSQL("DELETE FROM `zoom_levels`");
      _db.execSQL("DELETE FROM `settings`");
      _db.execSQL("DELETE FROM `bulk_rolls`");
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
    _typeConvertersMap.put(FilmDao.class, FilmDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CameraDao.class, CameraDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LensDao.class, LensDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AccessoryDao.class, AccessoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(RollDao.class, RollDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ChemicalDao.class, ChemicalDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ZoomLevelDao.class, ZoomLevelDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SettingDao.class, SettingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BulkRollDao.class, BulkRollDao_Impl.getRequiredConverters());
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
  public FilmDao filmDao() {
    if (_filmDao != null) {
      return _filmDao;
    } else {
      synchronized(this) {
        if(_filmDao == null) {
          _filmDao = new FilmDao_Impl(this);
        }
        return _filmDao;
      }
    }
  }

  @Override
  public CameraDao cameraDao() {
    if (_cameraDao != null) {
      return _cameraDao;
    } else {
      synchronized(this) {
        if(_cameraDao == null) {
          _cameraDao = new CameraDao_Impl(this);
        }
        return _cameraDao;
      }
    }
  }

  @Override
  public LensDao lensDao() {
    if (_lensDao != null) {
      return _lensDao;
    } else {
      synchronized(this) {
        if(_lensDao == null) {
          _lensDao = new LensDao_Impl(this);
        }
        return _lensDao;
      }
    }
  }

  @Override
  public AccessoryDao accessoryDao() {
    if (_accessoryDao != null) {
      return _accessoryDao;
    } else {
      synchronized(this) {
        if(_accessoryDao == null) {
          _accessoryDao = new AccessoryDao_Impl(this);
        }
        return _accessoryDao;
      }
    }
  }

  @Override
  public RollDao rollDao() {
    if (_rollDao != null) {
      return _rollDao;
    } else {
      synchronized(this) {
        if(_rollDao == null) {
          _rollDao = new RollDao_Impl(this);
        }
        return _rollDao;
      }
    }
  }

  @Override
  public ChemicalDao chemicalDao() {
    if (_chemicalDao != null) {
      return _chemicalDao;
    } else {
      synchronized(this) {
        if(_chemicalDao == null) {
          _chemicalDao = new ChemicalDao_Impl(this);
        }
        return _chemicalDao;
      }
    }
  }

  @Override
  public ZoomLevelDao zoomLevelDao() {
    if (_zoomLevelDao != null) {
      return _zoomLevelDao;
    } else {
      synchronized(this) {
        if(_zoomLevelDao == null) {
          _zoomLevelDao = new ZoomLevelDao_Impl(this);
        }
        return _zoomLevelDao;
      }
    }
  }

  @Override
  public SettingDao settingDao() {
    if (_settingDao != null) {
      return _settingDao;
    } else {
      synchronized(this) {
        if(_settingDao == null) {
          _settingDao = new SettingDao_Impl(this);
        }
        return _settingDao;
      }
    }
  }

  @Override
  public BulkRollDao bulkRollDao() {
    if (_bulkRollDao != null) {
      return _bulkRollDao;
    } else {
      synchronized(this) {
        if(_bulkRollDao == null) {
          _bulkRollDao = new BulkRollDao_Impl(this);
        }
        return _bulkRollDao;
      }
    }
  }
}
