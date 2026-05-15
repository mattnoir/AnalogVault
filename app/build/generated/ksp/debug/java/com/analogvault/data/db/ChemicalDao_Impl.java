package com.analogvault.data.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.analogvault.data.model.Chemical;
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
public final class ChemicalDao_Impl implements ChemicalDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<Chemical> __deletionAdapterOfChemical;

  private final EntityUpsertionAdapter<Chemical> __upsertionAdapterOfChemical;

  public ChemicalDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfChemical = new EntityDeletionOrUpdateAdapter<Chemical>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `chemicals` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Chemical entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__upsertionAdapterOfChemical = new EntityUpsertionAdapter<Chemical>(new EntityInsertionAdapter<Chemical>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `chemicals` (`id`,`name`,`type`,`dilution`,`volume`,`volumeUnit`,`mixDate`,`maxRolls`,`baseDevTime`,`timeAdjPerRoll`,`manualRolls`,`notes`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Chemical entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getDilution());
        statement.bindString(5, entity.getVolume());
        statement.bindString(6, entity.getVolumeUnit());
        statement.bindString(7, entity.getMixDate());
        statement.bindString(8, entity.getMaxRolls());
        statement.bindString(9, entity.getBaseDevTime());
        statement.bindString(10, entity.getTimeAdjPerRoll());
        statement.bindLong(11, entity.getManualRolls());
        statement.bindString(12, entity.getNotes());
      }
    }, new EntityDeletionOrUpdateAdapter<Chemical>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `chemicals` SET `id` = ?,`name` = ?,`type` = ?,`dilution` = ?,`volume` = ?,`volumeUnit` = ?,`mixDate` = ?,`maxRolls` = ?,`baseDevTime` = ?,`timeAdjPerRoll` = ?,`manualRolls` = ?,`notes` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Chemical entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getDilution());
        statement.bindString(5, entity.getVolume());
        statement.bindString(6, entity.getVolumeUnit());
        statement.bindString(7, entity.getMixDate());
        statement.bindString(8, entity.getMaxRolls());
        statement.bindString(9, entity.getBaseDevTime());
        statement.bindString(10, entity.getTimeAdjPerRoll());
        statement.bindLong(11, entity.getManualRolls());
        statement.bindString(12, entity.getNotes());
        statement.bindString(13, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final Chemical chem, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfChemical.handle(chem);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final Chemical chem, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfChemical.upsert(chem);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Chemical>> getAll() {
    final String _sql = "SELECT * FROM chemicals ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"chemicals"}, new Callable<List<Chemical>>() {
      @Override
      @NonNull
      public List<Chemical> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfDilution = CursorUtil.getColumnIndexOrThrow(_cursor, "dilution");
          final int _cursorIndexOfVolume = CursorUtil.getColumnIndexOrThrow(_cursor, "volume");
          final int _cursorIndexOfVolumeUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "volumeUnit");
          final int _cursorIndexOfMixDate = CursorUtil.getColumnIndexOrThrow(_cursor, "mixDate");
          final int _cursorIndexOfMaxRolls = CursorUtil.getColumnIndexOrThrow(_cursor, "maxRolls");
          final int _cursorIndexOfBaseDevTime = CursorUtil.getColumnIndexOrThrow(_cursor, "baseDevTime");
          final int _cursorIndexOfTimeAdjPerRoll = CursorUtil.getColumnIndexOrThrow(_cursor, "timeAdjPerRoll");
          final int _cursorIndexOfManualRolls = CursorUtil.getColumnIndexOrThrow(_cursor, "manualRolls");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<Chemical> _result = new ArrayList<Chemical>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Chemical _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpDilution;
            _tmpDilution = _cursor.getString(_cursorIndexOfDilution);
            final String _tmpVolume;
            _tmpVolume = _cursor.getString(_cursorIndexOfVolume);
            final String _tmpVolumeUnit;
            _tmpVolumeUnit = _cursor.getString(_cursorIndexOfVolumeUnit);
            final String _tmpMixDate;
            _tmpMixDate = _cursor.getString(_cursorIndexOfMixDate);
            final String _tmpMaxRolls;
            _tmpMaxRolls = _cursor.getString(_cursorIndexOfMaxRolls);
            final String _tmpBaseDevTime;
            _tmpBaseDevTime = _cursor.getString(_cursorIndexOfBaseDevTime);
            final String _tmpTimeAdjPerRoll;
            _tmpTimeAdjPerRoll = _cursor.getString(_cursorIndexOfTimeAdjPerRoll);
            final int _tmpManualRolls;
            _tmpManualRolls = _cursor.getInt(_cursorIndexOfManualRolls);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            _item = new Chemical(_tmpId,_tmpName,_tmpType,_tmpDilution,_tmpVolume,_tmpVolumeUnit,_tmpMixDate,_tmpMaxRolls,_tmpBaseDevTime,_tmpTimeAdjPerRoll,_tmpManualRolls,_tmpNotes);
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
