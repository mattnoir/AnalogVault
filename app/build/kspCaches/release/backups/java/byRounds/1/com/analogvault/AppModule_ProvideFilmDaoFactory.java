package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.FilmDao;
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
public final class AppModule_ProvideFilmDaoFactory implements Factory<FilmDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideFilmDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public FilmDao get() {
    return provideFilmDao(dbProvider.get());
  }

  public static AppModule_ProvideFilmDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideFilmDaoFactory(dbProvider);
  }

  public static FilmDao provideFilmDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideFilmDao(db));
  }
}
