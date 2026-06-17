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
import com.analogvault.data.model.Lens;
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
public final class LensDao_Impl implements LensDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<Lens> __deletionAdapterOfLens;

  private final EntityUpsertionAdapter<Lens> __upsertionAdapterOfLens;

  public LensDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfLens = new EntityDeletionOrUpdateAdapter<Lens>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `lenses` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Lens entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__upsertionAdapterOfLens = new EntityUpsertionAdapter<Lens>(new EntityInsertionAdapter<Lens>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `lenses` (`id`,`name`,`brand`,`focalLength`,`maxAperture`,`mount`,`condition`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Lens entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getFocalLength());
        statement.bindString(5, entity.getMaxAperture());
        statement.bindString(6, entity.getMount());
        statement.bindString(7, entity.getCondition());
      }
    }, new EntityDeletionOrUpdateAdapter<Lens>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `lenses` SET `id` = ?,`name` = ?,`brand` = ?,`focalLength` = ?,`maxAperture` = ?,`mount` = ?,`condition` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Lens entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getFocalLength());
        statement.bindString(5, entity.getMaxAperture());
        statement.bindString(6, entity.getMount());
        statement.bindString(7, entity.getCondition());
        statement.bindString(8, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final Lens lens, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfLens.handle(lens);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final Lens lens, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfLens.upsert(lens);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Lens>> getAll() {
    final String _sql = "SELECT * FROM lenses ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"lenses"}, new Callable<List<Lens>>() {
      @Override
      @NonNull
      public List<Lens> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfMaxAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "maxAperture");
          final int _cursorIndexOfMount = CursorUtil.getColumnIndexOrThrow(_cursor, "mount");
          final int _cursorIndexOfCondition = CursorUtil.getColumnIndexOrThrow(_cursor, "condition");
          final List<Lens> _result = new ArrayList<Lens>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Lens _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpFocalLength;
            _tmpFocalLength = _cursor.getString(_cursorIndexOfFocalLength);
            final String _tmpMaxAperture;
            _tmpMaxAperture = _cursor.getString(_cursorIndexOfMaxAperture);
            final String _tmpMount;
            _tmpMount = _cursor.getString(_cursorIndexOfMount);
            final String _tmpCondition;
            _tmpCondition = _cursor.getString(_cursorIndexOfCondition);
            _item = new Lens(_tmpId,_tmpName,_tmpBrand,_tmpFocalLength,_tmpMaxAperture,_tmpMount,_tmpCondition);
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
