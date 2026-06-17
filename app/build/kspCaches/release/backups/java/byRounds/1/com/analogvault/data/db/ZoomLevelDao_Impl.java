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
import com.analogvault.data.model.ZoomLevel;
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
public final class ZoomLevelDao_Impl implements ZoomLevelDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<ZoomLevel> __deletionAdapterOfZoomLevel;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final EntityUpsertionAdapter<ZoomLevel> __upsertionAdapterOfZoomLevel;

  public ZoomLevelDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfZoomLevel = new EntityDeletionOrUpdateAdapter<ZoomLevel>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `zoom_levels` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ZoomLevel entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM zoom_levels";
        return _query;
      }
    };
    this.__upsertionAdapterOfZoomLevel = new EntityUpsertionAdapter<ZoomLevel>(new EntityInsertionAdapter<ZoomLevel>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `zoom_levels` (`id`,`label`,`mm`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ZoomLevel entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getLabel());
        statement.bindLong(3, entity.getMm());
      }
    }, new EntityDeletionOrUpdateAdapter<ZoomLevel>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `zoom_levels` SET `id` = ?,`label` = ?,`mm` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ZoomLevel entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getLabel());
        statement.bindLong(3, entity.getMm());
        statement.bindString(4, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final ZoomLevel z, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfZoomLevel.handle(z);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final ZoomLevel z, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfZoomLevel.upsert(z);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ZoomLevel>> getAll() {
    final String _sql = "SELECT * FROM zoom_levels ORDER BY mm ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"zoom_levels"}, new Callable<List<ZoomLevel>>() {
      @Override
      @NonNull
      public List<ZoomLevel> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfMm = CursorUtil.getColumnIndexOrThrow(_cursor, "mm");
          final List<ZoomLevel> _result = new ArrayList<ZoomLevel>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ZoomLevel _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final int _tmpMm;
            _tmpMm = _cursor.getInt(_cursorIndexOfMm);
            _item = new ZoomLevel(_tmpId,_tmpLabel,_tmpMm);
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
