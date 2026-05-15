package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.SettingDao;
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
public final class AppModule_ProvideSettingDaoFactory implements Factory<SettingDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideSettingDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public SettingDao get() {
    return provideSettingDao(dbProvider.get());
  }

  public static AppModule_ProvideSettingDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideSettingDaoFactory(dbProvider);
  }

  public static SettingDao provideSettingDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSettingDao(db));
  }
}
