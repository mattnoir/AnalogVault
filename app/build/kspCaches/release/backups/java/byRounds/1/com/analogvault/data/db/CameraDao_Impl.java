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
import com.analogvault.data.model.Camera;
import com.analogvault.data.model.StringListConverter;
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
public final class CameraDao_Impl implements CameraDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<Camera> __deletionAdapterOfCamera;

  private final EntityUpsertionAdapter<Camera> __upsertionAdapterOfCamera;

  private final StringListConverter __stringListConverter = new StringListConverter();

  public CameraDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfCamera = new EntityDeletionOrUpdateAdapter<Camera>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `cameras` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Camera entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__upsertionAdapterOfCamera = new EntityUpsertionAdapter<Camera>(new EntityInsertionAdapter<Camera>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `cameras` (`id`,`name`,`brand`,`format`,`mfFormat`,`lensSystem`,`condition`,`mount`,`adapterMounts`,`notes`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Camera entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getFormat());
        statement.bindString(5, entity.getMfFormat());
        statement.bindString(6, entity.getLensSystem());
        statement.bindString(7, entity.getCondition());
        statement.bindString(8, entity.getMount());
        final String _tmp = __stringListConverter.fromList(entity.getAdapterMounts());
        statement.bindString(9, _tmp);
        statement.bindString(10, entity.getNotes());
      }
    }, new EntityDeletionOrUpdateAdapter<Camera>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `cameras` SET `id` = ?,`name` = ?,`brand` = ?,`format` = ?,`mfFormat` = ?,`lensSystem` = ?,`condition` = ?,`mount` = ?,`adapterMounts` = ?,`notes` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Camera entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getFormat());
        statement.bindString(5, entity.getMfFormat());
        statement.bindString(6, entity.getLensSystem());
        statement.bindString(7, entity.getCondition());
        statement.bindString(8, entity.getMount());
        final String _tmp = __stringListConverter.fromList(entity.getAdapterMounts());
        statement.bindString(9, _tmp);
        statement.bindString(10, entity.getNotes());
        statement.bindString(11, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final Camera cam, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCamera.handle(cam);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final Camera cam, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfCamera.upsert(cam);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Camera>> getAll() {
    final String _sql = "SELECT * FROM cameras ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cameras"}, new Callable<List<Camera>>() {
      @Override
      @NonNull
      public List<Camera> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfFormat = CursorUtil.getColumnIndexOrThrow(_cursor, "format");
          final int _cursorIndexOfMfFormat = CursorUtil.getColumnIndexOrThrow(_cursor, "mfFormat");
          final int _cursorIndexOfLensSystem = CursorUtil.getColumnIndexOrThrow(_cursor, "lensSystem");
          final int _cursorIndexOfCondition = CursorUtil.getColumnIndexOrThrow(_cursor, "condition");
          final int _cursorIndexOfMount = CursorUtil.getColumnIndexOrThrow(_cursor, "mount");
          final int _cursorIndexOfAdapterMounts = CursorUtil.getColumnIndexOrThrow(_cursor, "adapterMounts");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<Camera> _result = new ArrayList<Camera>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Camera _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpFormat;
            _tmpFormat = _cursor.getString(_cursorIndexOfFormat);
            final String _tmpMfFormat;
            _tmpMfFormat = _cursor.getString(_cursorIndexOfMfFormat);
            final String _tmpLensSystem;
            _tmpLensSystem = _cursor.getString(_cursorIndexOfLensSystem);
            final String _tmpCondition;
            _tmpCondition = _cursor.getString(_cursorIndexOfCondition);
            final String _tmpMount;
            _tmpMount = _cursor.getString(_cursorIndexOfMount);
            final List<String> _tmpAdapterMounts;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfAdapterMounts);
            _tmpAdapterMounts = __stringListConverter.toList(_tmp);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            _item = new Camera(_tmpId,_tmpName,_tmpBrand,_tmpFormat,_tmpMfFormat,_tmpLensSystem,_tmpCondition,_tmpMount,_tmpAdapterMounts,_tmpNotes);
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
