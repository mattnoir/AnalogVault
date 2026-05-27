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
import com.analogvault.data.model.FilmStock;
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
public final class FilmDao_Impl implements FilmDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<FilmStock> __deletionAdapterOfFilmStock;

  private final EntityUpsertionAdapter<FilmStock> __upsertionAdapterOfFilmStock;

  public FilmDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfFilmStock = new EntityDeletionOrUpdateAdapter<FilmStock>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `films` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FilmStock entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__upsertionAdapterOfFilmStock = new EntityUpsertionAdapter<FilmStock>(new EntityInsertionAdapter<FilmStock>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `films` (`id`,`name`,`brand`,`type`,`iso`,`shots`,`filmFormat`,`frameCount`,`expiryDate`,`storage`,`quantity`,`notes`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FilmStock entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getType());
        statement.bindLong(5, entity.getIso());
        statement.bindLong(6, entity.getShots());
        statement.bindString(7, entity.getFilmFormat());
        statement.bindLong(8, entity.getFrameCount());
        statement.bindString(9, entity.getExpiryDate());
        statement.bindString(10, entity.getStorage());
        statement.bindLong(11, entity.getQuantity());
        statement.bindString(12, entity.getNotes());
      }
    }, new EntityDeletionOrUpdateAdapter<FilmStock>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `films` SET `id` = ?,`name` = ?,`brand` = ?,`type` = ?,`iso` = ?,`shots` = ?,`filmFormat` = ?,`frameCount` = ?,`expiryDate` = ?,`storage` = ?,`quantity` = ?,`notes` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FilmStock entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getType());
        statement.bindLong(5, entity.getIso());
        statement.bindLong(6, entity.getShots());
        statement.bindString(7, entity.getFilmFormat());
        statement.bindLong(8, entity.getFrameCount());
        statement.bindString(9, entity.getExpiryDate());
        statement.bindString(10, entity.getStorage());
        statement.bindLong(11, entity.getQuantity());
        statement.bindString(12, entity.getNotes());
        statement.bindString(13, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final FilmStock film, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfFilmStock.handle(film);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final FilmStock film, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfFilmStock.upsert(film);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<FilmStock>> getAll() {
    final String _sql = "SELECT * FROM films ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"films"}, new Callable<List<FilmStock>>() {
      @Override
      @NonNull
      public List<FilmStock> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfShots = CursorUtil.getColumnIndexOrThrow(_cursor, "shots");
          final int _cursorIndexOfFilmFormat = CursorUtil.getColumnIndexOrThrow(_cursor, "filmFormat");
          final int _cursorIndexOfFrameCount = CursorUtil.getColumnIndexOrThrow(_cursor, "frameCount");
          final int _cursorIndexOfExpiryDate = CursorUtil.getColumnIndexOrThrow(_cursor, "expiryDate");
          final int _cursorIndexOfStorage = CursorUtil.getColumnIndexOrThrow(_cursor, "storage");
          final int _cursorIndexOfQuantity = CursorUtil.getColumnIndexOrThrow(_cursor, "quantity");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<FilmStock> _result = new ArrayList<FilmStock>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FilmStock _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpBrand;
            _tmpBrand = _cursor.getString(_cursorIndexOfBrand);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final int _tmpIso;
            _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            final int _tmpShots;
            _tmpShots = _cursor.getInt(_cursorIndexOfShots);
            final String _tmpFilmFormat;
            _tmpFilmFormat = _cursor.getString(_cursorIndexOfFilmFormat);
            final int _tmpFrameCount;
            _tmpFrameCount = _cursor.getInt(_cursorIndexOfFrameCount);
            final String _tmpExpiryDate;
            _tmpExpiryDate = _cursor.getString(_cursorIndexOfExpiryDate);
            final String _tmpStorage;
            _tmpStorage = _cursor.getString(_cursorIndexOfStorage);
            final int _tmpQuantity;
            _tmpQuantity = _cursor.getInt(_cursorIndexOfQuantity);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            _item = new FilmStock(_tmpId,_tmpName,_tmpBrand,_tmpType,_tmpIso,_tmpShots,_tmpFilmFormat,_tmpFrameCount,_tmpExpiryDate,_tmpStorage,_tmpQuantity,_tmpNotes);
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
