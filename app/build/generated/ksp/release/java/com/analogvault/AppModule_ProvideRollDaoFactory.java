package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.RollDao;
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
public final class AppModule_ProvideRollDaoFactory implements Factory<RollDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideRollDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public RollDao get() {
    return provideRollDao(dbProvider.get());
  }

  public static AppModule_ProvideRollDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideRollDaoFactory(dbProvider);
  }

  public static RollDao provideRollDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideRollDao(db));
  }
}
