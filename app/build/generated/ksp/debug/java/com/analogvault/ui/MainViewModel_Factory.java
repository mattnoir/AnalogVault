package com.analogvault.ui;

import com.analogvault.data.network.WeatherApi;
import com.analogvault.data.repo.VaultRepository;
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
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<VaultRepository> repoProvider;

  private final Provider<WeatherApi> weatherApiProvider;

  public MainViewModel_Factory(Provider<VaultRepository> repoProvider,
      Provider<WeatherApi> weatherApiProvider) {
    this.repoProvider = repoProvider;
    this.weatherApiProvider = weatherApiProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(repoProvider.get(), weatherApiProvider.get());
  }

  public static MainViewModel_Factory create(Provider<VaultRepository> repoProvider,
      Provider<WeatherApi> weatherApiProvider) {
    return new MainViewModel_Factory(repoProvider, weatherApiProvider);
  }

  public static MainViewModel newInstance(VaultRepository repo, WeatherApi weatherApi) {
    return new MainViewModel(repo, weatherApi);
  }
}
