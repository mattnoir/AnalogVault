package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.BulkRollDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class AppModule_ProvideBulkRollDaoFactory implements Factory<BulkRollDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideBulkRollDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public BulkRollDao get() {
    return provideBulkRollDao(dbProvider.get());
  }

  public static AppModule_ProvideBulkRollDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideBulkRollDaoFactory(dbProvider);
  }

  public static BulkRollDao provideBulkRollDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBulkRollDao(db));
  }
}
