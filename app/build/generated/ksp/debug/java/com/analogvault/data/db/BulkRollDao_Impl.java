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
import com.analogvault.data.model.BulkRoll;
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
public final class BulkRollDao_Impl implements BulkRollDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<BulkRoll> __deletionAdapterOfBulkRoll;

  private final EntityUpsertionAdapter<BulkRoll> __upsertionAdapterOfBulkRoll;

  public BulkRollDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfBulkRoll = new EntityDeletionOrUpdateAdapter<BulkRoll>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `bulk_rolls` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BulkRoll entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__upsertionAdapterOfBulkRoll = new EntityUpsertionAdapter<BulkRoll>(new EntityInsertionAdapter<BulkRoll>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `bulk_rolls` (`id`,`name`,`brand`,`type`,`iso`,`totalFrames`,`usedFrames`,`notes`,`purchaseDate`,`expiryDate`,`totalCost`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BulkRoll entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getType());
        statement.bindLong(5, entity.getIso());
        statement.bindLong(6, entity.getTotalFrames());
        statement.bindLong(7, entity.getUsedFrames());
        statement.bindString(8, entity.getNotes());
        statement.bindString(9, entity.getPurchaseDate());
        statement.bindString(10, entity.getExpiryDate());
        statement.bindDouble(11, entity.getTotalCost());
      }
    }, new EntityDeletionOrUpdateAdapter<BulkRoll>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `bulk_rolls` SET `id` = ?,`name` = ?,`brand` = ?,`type` = ?,`iso` = ?,`totalFrames` = ?,`usedFrames` = ?,`notes` = ?,`purchaseDate` = ?,`expiryDate` = ?,`totalCost` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BulkRoll entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getBrand());
        statement.bindString(4, entity.getType());
        statement.bindLong(5, entity.getIso());
        statement.bindLong(6, entity.getTotalFrames());
        statement.bindLong(7, entity.getUsedFrames());
        statement.bindString(8, entity.getNotes());
        statement.bindString(9, entity.getPurchaseDate());
        statement.bindString(10, entity.getExpiryDate());
        statement.bindDouble(11, entity.getTotalCost());
        statement.bindString(12, entity.getId());
      }
    });
  }

  @Override
  public Object delete(final BulkRoll b, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfBulkRoll.handle(b);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final BulkRoll b, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfBulkRoll.upsert(b);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BulkRoll>> getAll() {
    final String _sql = "SELECT * FROM bulk_rolls ORDER BY name ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"bulk_rolls"}, new Callable<List<BulkRoll>>() {
      @Override
      @NonNull
      public List<BulkRoll> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBrand = CursorUtil.getColumnIndexOrThrow(_cursor, "brand");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfTotalFrames = CursorUtil.getColumnIndexOrThrow(_cursor, "totalFrames");
          final int _cursorIndexOfUsedFrames = CursorUtil.getColumnIndexOrThrow(_cursor, "usedFrames");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfPurchaseDate = CursorUtil.getColumnIndexOrThrow(_cursor, "purchaseDate");
          final int _cursorIndexOfExpiryDate = CursorUtil.getColumnIndexOrThrow(_cursor, "expiryDate");
          final int _cursorIndexOfTotalCost = CursorUtil.getColumnIndexOrThrow(_cursor, "totalCost");
          final List<BulkRoll> _result = new ArrayList<BulkRoll>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BulkRoll _item;
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
            final int _tmpTotalFrames;
            _tmpTotalFrames = _cursor.getInt(_cursorIndexOfTotalFrames);
            final int _tmpUsedFrames;
            _tmpUsedFrames = _cursor.getInt(_cursorIndexOfUsedFrames);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final String _tmpPurchaseDate;
            _tmpPurchaseDate = _cursor.getString(_cursorIndexOfPurchaseDate);
            final String _tmpExpiryDate;
            _tmpExpiryDate = _cursor.getString(_cursorIndexOfExpiryDate);
            final double _tmpTotalCost;
            _tmpTotalCost = _cursor.getDouble(_cursorIndexOfTotalCost);
            _item = new BulkRoll(_tmpId,_tmpName,_tmpBrand,_tmpType,_tmpIso,_tmpTotalFrames,_tmpUsedFrames,_tmpNotes,_tmpPurchaseDate,_tmpExpiryDate,_tmpTotalCost);
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
