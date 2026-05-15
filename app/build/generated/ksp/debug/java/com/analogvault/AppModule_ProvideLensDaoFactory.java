package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.LensDao;
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
public final class AppModule_ProvideLensDaoFactory implements Factory<LensDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideLensDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public LensDao get() {
    return provideLensDao(dbProvider.get());
  }

  public static AppModule_ProvideLensDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideLensDaoFactory(dbProvider);
  }

  public static LensDao provideLensDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideLensDao(db));
  }
}
