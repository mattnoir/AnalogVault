package com.analogvault;

import com.analogvault.data.network.WeatherApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

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
public final class AppModule_ProvideWeatherApiFactory implements Factory<WeatherApi> {
  private final Provider<Retrofit> retrofitProvider;

  public AppModule_ProvideWeatherApiFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public WeatherApi get() {
    return provideWeatherApi(retrofitProvider.get());
  }

  public static AppModule_ProvideWeatherApiFactory create(Provider<Retrofit> retrofitProvider) {
    return new AppModule_ProvideWeatherApiFactory(retrofitProvider);
  }

  public static WeatherApi provideWeatherApi(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideWeatherApi(retrofit));
  }
}
