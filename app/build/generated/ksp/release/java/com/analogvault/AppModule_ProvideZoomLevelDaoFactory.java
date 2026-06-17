package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.ZoomLevelDao;
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
public final class AppModule_ProvideZoomLevelDaoFactory implements Factory<ZoomLevelDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideZoomLevelDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ZoomLevelDao get() {
    return provideZoomLevelDao(dbProvider.get());
  }

  public static AppModule_ProvideZoomLevelDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideZoomLevelDaoFactory(dbProvider);
  }

  public static ZoomLevelDao provideZoomLevelDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideZoomLevelDao(db));
  }
}
