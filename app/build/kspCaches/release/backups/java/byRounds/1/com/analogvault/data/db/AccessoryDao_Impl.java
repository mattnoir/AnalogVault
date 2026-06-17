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
import com.analogvault.data.model.Accessory;
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
public final class AccessoryDao_Impl implements AccessoryDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<Accessory> __deletionAdapterOfAccessory;

  private final EntityUpsertionAdapter<Accessory> __upsertionAdapterOfAccessory;

  public AccessoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfAccessory = new EntityDeletionOrUpdateAdapter<Accessory>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `accessories` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Accessory entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__upsertionAdapterOfAccessory = new EntityUpsertionAdapter<Accessory>(new EntityInsertionAdapter<Accessory>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `accessories` (`id`,`name`,`type`,`brand`,`condition`,`notes`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Accessory entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getBrand());
        statement.bindString(5, entity.getCondition());
        statement.bindString(6, entity.getNotes());
      }
    }, new EntityDeletionOrUpdateAdapter<Accessory>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `accessories` SET `id` = ?,`name` = ?,`type` = ?,`brand` = ?,`condition` = ?,`notes` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Accessory entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getType());
        statement.bindString(4, entity.getBrand());
        statement.bindString(5, entity.getCondition());
        statement.bindString(6, entity.getNotes());
        statement.bindString(7, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final Accessory acc, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfAccessory.handle(acc);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final Accessory acc, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfAccessory.upsert(acc);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Accessory>> getAll() {
    final String _sql = "SELECT * FROM accessories ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"accessories"}, new Callable<List<Accessory>>() {
      @Override
      @NonNull
      public List<Accessory> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfCondition = CursorUtil.getColumnIndexOrThrow(_cursor, "condition");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<Accessory> _result = new ArrayList<Accessory>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Accessory _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpCondition;
            _tmpCondition = _cursor.getString(_cursorIndexOfCondition);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            _item = new Accessory(_tmpId,_tmpName,_tmpType,_tmpBrand,_tmpCondition,_tmpNotes);
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
