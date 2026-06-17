package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.ChemicalDao;
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
public final class AppModule_ProvideChemicalDaoFactory implements Factory<ChemicalDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideChemicalDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ChemicalDao get() {
    return provideChemicalDao(dbProvider.get());
  }

  public static AppModule_ProvideChemicalDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideChemicalDaoFactory(dbProvider);
  }

  public static ChemicalDao provideChemicalDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideChemicalDao(db));
  }
}
