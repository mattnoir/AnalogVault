package com.analogvault.data.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.analogvault.data.model.DevLog;
import com.analogvault.data.model.DevLogConverter;
import com.analogvault.data.model.Roll;
import com.analogvault.data.model.ScanLog;
import com.analogvault.data.model.ScanLogConverter;
import com.analogvault.data.model.Shot;
import com.analogvault.data.model.ShotListConverter;
import java.lang.Class;
import java.lang.Exception;
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
public final class RollDao_Impl implements RollDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<Roll> __deletionAdapterOfRoll;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final EntityUpsertionAdapter<Roll> __upsertionAdapterOfRoll;

  private final ShotListConverter __shotListConverter = new ShotListConverter();

  private final DevLogConverter __devLogConverter = new DevLogConverter();

  private final ScanLogConverter __scanLogConverter = new ScanLogConverter();

  public RollDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfRoll = new EntityDeletionOrUpdateAdapter<Roll>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `rolls` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Roll entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM rolls WHERE id = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfRoll = new EntityUpsertionAdapter<Roll>(new EntityInsertionAdapter<Roll>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `rolls` (`id`,`filmId`,`cameraId`,`cameraLensId`,`startDate`,`finished`,`developed`,`scanned`,`shots`,`devLog`,`scanLog`,`pushIso`,`totalShots`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Roll entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getFilmId());
        statement.bindString(3, entity.getCameraId());
        statement.bindString(4, entity.getCameraLensId());
        statement.bindString(5, entity.getStartDate());
        final int _tmp = entity.getFinished() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.getDeveloped() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        final int _tmp_2 = entity.getScanned() ? 1 : 0;
        statement.bindLong(8, _tmp_2);
        final String _tmp_3 = __shotListConverter.fromList(entity.getShots());
        statement.bindString(9, _tmp_3);
        final String _tmp_4 = __devLogConverter.from(entity.getDevLog());
        if (_tmp_4 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_4);
        }
        final String _tmp_5 = __scanLogConverter.from(entity.getScanLog());
        if (_tmp_5 == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, _tmp_5);
        }
        statement.bindString(12, entity.getPushIso());
        statement.bindLong(13, entity.getTotalShots());
      }
    }, new EntityDeletionOrUpdateAdapter<Roll>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `rolls` SET `id` = ?,`filmId` = ?,`cameraId` = ?,`cameraLensId` = ?,`startDate` = ?,`finished` = ?,`developed` = ?,`scanned` = ?,`shots` = ?,`devLog` = ?,`scanLog` = ?,`pushIso` = ?,`totalShots` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Roll entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getFilmId());
        statement.bindString(3, entity.getCameraId());
        statement.bindString(4, entity.getCameraLensId());
        statement.bindString(5, entity.getStartDate());
        final int _tmp = entity.getFinished() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.getDeveloped() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        final int _tmp_2 = entity.getScanned() ? 1 : 0;
        statement.bindLong(8, _tmp_2);
        final String _tmp_3 = __shotListConverter.fromList(entity.getShots());
        statement.bindString(9, _tmp_3);
        final String _tmp_4 = __devLogConverter.from(entity.getDevLog());
        if (_tmp_4 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_4);
        }
        final String _tmp_5 = __scanLogConverter.from(entity.getScanLog());
        if (_tmp_5 == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, _tmp_5);
        }
        statement.bindString(12, entity.getPushIso());
        statement.bindLong(13, entity.getTotalShots());
        statement.bindString(14, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final Roll roll, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfRoll.handle(roll);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final Roll roll, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfRoll.upsert(roll);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Roll>> getAll() {
    final String _sql = "SELECT * FROM rolls ORDER BY startDate DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"rolls"}, new Callable<List<Roll>>() {
      @Override
      @NonNull
      public List<Roll> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFilmId = CursorUtil.getColumnIndexOrThrow(_cursor, "filmId");
          final int _cursorIndexOfCameraId = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraId");
          final int _cursorIndexOfCameraLensId = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraLensId");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfFinished = CursorUtil.getColumnIndexOrThrow(_cursor, "finished");
          final int _cursorIndexOfDeveloped = CursorUtil.getColumnIndexOrThrow(_cursor, "developed");
          final int _cursorIndexOfScanned = CursorUtil.getColumnIndexOrThrow(_cursor, "scanned");
          final int _cursorIndexOfShots = CursorUtil.getColumnIndexOrThrow(_cursor, "shots");
          final int _cursorIndexOfDevLog = CursorUtil.getColumnIndexOrThrow(_cursor, "devLog");
          final int _cursorIndexOfScanLog = CursorUtil.getColumnIndexOrThrow(_cursor, "scanLog");
          final int _cursorIndexOfPushIso = CursorUtil.getColumnIndexOrThrow(_cursor, "pushIso");
          final int _cursorIndexOfTotalShots = CursorUtil.getColumnIndexOrThrow(_cursor, "totalShots");
          final List<Roll> _result = new ArrayList<Roll>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Roll _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpFilmId;
            _tmpFilmId = _cursor.getString(_cursorIndexOfFilmId);
            final String _tmpCameraId;
            _tmpCameraId = _cursor.getString(_cursorIndexOfCameraId);
            final String _tmpCameraLensId;
            _tmpCameraLensId = _cursor.getString(_cursorIndexOfCameraLensId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final boolean _tmpFinished;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFinished);
            _tmpFinished = _tmp != 0;
            final boolean _tmpDeveloped;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfDeveloped);
            _tmpDeveloped = _tmp_1 != 0;
            final boolean _tmpScanned;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfScanned);
            _tmpScanned = _tmp_2 != 0;
            final List<Shot> _tmpShots;
            final String _tmp_3;
            _tmp_3 = _cursor.getString(_cursorIndexOfShots);
            _tmpShots = __shotListConverter.toList(_tmp_3);
            final DevLog _tmpDevLog;
            final String _tmp_4;
            if (_cursor.isNull(_cursorIndexOfDevLog)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getString(_cursorIndexOfDevLog);
            }
            _tmpDevLog = __devLogConverter.to(_tmp_4);
            final ScanLog _tmpScanLog;
            final String _tmp_5;
            if (_cursor.isNull(_cursorIndexOfScanLog)) {
              _tmp_5 = null;
            } else {
              _tmp_5 = _cursor.getString(_cursorIndexOfScanLog);
            }
            _tmpScanLog = __scanLogConverter.to(_tmp_5);
            final String _tmpPushIso;
            _tmpPushIso = _cursor.getString(_cursorIndexOfPushIso);
            final int _tmpTotalShots;
            _tmpTotalShots = _cursor.getInt(_cursorIndexOfTotalShots);
            _item = new Roll(_tmpId,_tmpFilmId,_tmpCameraId,_tmpCameraLensId,_tmpStartDate,_tmpFinished,_tmpDeveloped,_tmpScanned,_tmpShots,_tmpDevLog,_tmpScanLog,_tmpPushIso,_tmpTotalShots);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
