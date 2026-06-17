package com.analogvault;

import com.analogvault.data.db.AccessoryDao;
import com.analogvault.data.db.AppDatabase;
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
public final class AppModule_ProvideAccessoryDaoFactory implements Factory<AccessoryDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideAccessoryDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public AccessoryDao get() {
    return provideAccessoryDao(dbProvider.get());
  }

  public static AppModule_ProvideAccessoryDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideAccessoryDaoFactory(dbProvider);
  }

  public static AccessoryDao provideAccessoryDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAccessoryDao(db));
  }
}
