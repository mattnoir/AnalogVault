package com.analogvault.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.analogvault.data.model.Setting;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SettingDao_Impl implements SettingDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<Setting> __upsertionAdapterOfSetting;

  public SettingDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfSetting = new EntityUpsertionAdapter<Setting>(new EntityInsertionAdapter<Setting>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `settings` (`key`,`value`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Setting entity) {
        statement.bindString(1, entity.getKey());
        statement.bindString(2, entity.getValue());
      }
    }, new EntityDeletionOrUpdateAdapter<Setting>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `settings` SET `key` = ?,`value` = ? WHERE `key` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Setting entity) {
        statement.bindString(1, entity.getKey());
        statement.bindString(2, entity.getValue());
        statement.bindString(3, entity.getKey());
      }
    });
  }

  @Override
  public Object upsert(final Setting setting, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfSetting.upsert(setting);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String key, final Continuation<? super Setting> $completion) {
    final String _sql = "SELECT * FROM settings WHERE `key` = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Setting>() {
      @Override
      @Nullable
      public Setting call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKey = CursorUtil.getColumnIndexOrThrow(_cursor, "key");
          final int _cursorIndexOfValue = CursorUtil.getColumnIndexOrThrow(_cursor, "value");
          final Setting _result;
          if (_cursor.moveToFirst()) {
            final String _tmpKey;
            _tmpKey = _cursor.getString(_cursorIndexOfKey);
            final String _tmpValue;
            _tmpValue = _cursor.getString(_cursorIndexOfValue);
            _result = new Setting(_tmpKey,_tmpValue);
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
