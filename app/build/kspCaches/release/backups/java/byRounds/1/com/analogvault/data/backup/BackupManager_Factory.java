package com.analogvault.data.backup;

import com.analogvault.data.repo.VaultRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class BackupManager_Factory implements Factory<BackupManager> {
  private final Provider<VaultRepository> repoProvider;

  public BackupManager_Factory(Provider<VaultRepository> repoProvider) {
    this.repoProvider = repoProvider;
  }

  @Override
  public BackupManager get() {
    return newInstance(repoProvider.get());
  }

  public static BackupManager_Factory create(Provider<VaultRepository> repoProvider) {
    return new BackupManager_Factory(repoProvider);
  }

  public static BackupManager newInstance(VaultRepository repo) {
    return new BackupManager(repo);
  }
}
