package com.analogvault.ui;

import com.analogvault.data.backup.BackupManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class BackupViewModel_Factory implements Factory<BackupViewModel> {
  private final Provider<BackupManager> backupManagerProvider;

  public BackupViewModel_Factory(Provider<BackupManager> backupManagerProvider) {
    this.backupManagerProvider = backupManagerProvider;
  }

  @Override
  public BackupViewModel get() {
    return newInstance(backupManagerProvider.get());
  }

  public static BackupViewModel_Factory create(Provider<BackupManager> backupManagerProvider) {
    return new BackupViewModel_Factory(backupManagerProvider);
  }

  public static BackupViewModel newInstance(BackupManager backupManager) {
    return new BackupViewModel(backupManager);
  }
}
