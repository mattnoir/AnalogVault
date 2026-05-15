package com.analogvault;

import com.analogvault.data.db.AppDatabase;
import com.analogvault.data.db.CameraDao;
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
public final class AppModule_ProvideCameraDaoFactory implements Factory<CameraDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideCameraDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public CameraDao get() {
    return provideCameraDao(dbProvider.get());
  }

  public static AppModule_ProvideCameraDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideCameraDaoFactory(dbProvider);
  }

  public static CameraDao provideCameraDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideCameraDao(db));
  }
}
