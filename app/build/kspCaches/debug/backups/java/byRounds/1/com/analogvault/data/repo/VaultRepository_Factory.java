package com.analogvault.data.repo;

import com.analogvault.data.db.AccessoryDao;
import com.analogvault.data.db.BulkRollDao;
import com.analogvault.data.db.CameraDao;
import com.analogvault.data.db.ChemicalDao;
import com.analogvault.data.db.FilmDao;
import com.analogvault.data.db.LensDao;
import com.analogvault.data.db.RollDao;
import com.analogvault.data.db.SettingDao;
import com.analogvault.data.db.ZoomLevelDao;
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
public final class VaultRepository_Factory implements Factory<VaultRepository> {
  private final Provider<FilmDao> filmDaoProvider;

  private final Provider<CameraDao> cameraDaoProvider;

  private final Provider<LensDao> lensDaoProvider;

  private final Provider<AccessoryDao> accessoryDaoProvider;

  private final Provider<RollDao> rollDaoProvider;

  private final Provider<ChemicalDao> chemicalDaoProvider;

  private final Provider<ZoomLevelDao> zoomLevelDaoProvider;

  private final Provider<SettingDao> settingDaoProvider;

  private final Provider<BulkRollDao> bulkRollDaoProvider;

  public VaultRepository_Factory(Provider<FilmDao> filmDaoProvider,
      Provider<CameraDao> cameraDaoProvider, Provider<LensDao> lensDaoProvider,
      Provider<AccessoryDao> accessoryDaoProvider, Provider<RollDao> rollDaoProvider,
      Provider<ChemicalDao> chemicalDaoProvider, Provider<ZoomLevelDao> zoomLevelDaoProvider,
      Provider<SettingDao> settingDaoProvider, Provider<BulkRollDao> bulkRollDaoProvider) {
    this.filmDaoProvider = filmDaoProvider;
    this.cameraDaoProvider = cameraDaoProvider;
    this.lensDaoProvider = lensDaoProvider;
    this.accessoryDaoProvider = accessoryDaoProvider;
    this.rollDaoProvider = rollDaoProvider;
    this.chemicalDaoProvider = chemicalDaoProvider;
    this.zoomLevelDaoProvider = zoomLevelDaoProvider;
    this.settingDaoProvider = settingDaoProvider;
    this.bulkRollDaoProvider = bulkRollDaoProvider;
  }

  @Override
  public VaultRepository get() {
    return newInstance(filmDaoProvider.get(), cameraDaoProvider.get(), lensDaoProvider.get(), accessoryDaoProvider.get(), rollDaoProvider.get(), chemicalDaoProvider.get(), zoomLevelDaoProvider.get(), settingDaoProvider.get(), bulkRollDaoProvider.get());
  }

  public static VaultRepository_Factory create(Provider<FilmDao> filmDaoProvider,
      Provider<CameraDao> cameraDaoProvider, Provider<LensDao> lensDaoProvider,
      Provider<AccessoryDao> accessoryDaoProvider, Provider<RollDao> rollDaoProvider,
      Provider<ChemicalDao> chemicalDaoProvider, Provider<ZoomLevelDao> zoomLevelDaoProvider,
      Provider<SettingDao> settingDaoProvider, Provider<BulkRollDao> bulkRollDaoProvider) {
    return new VaultRepository_Factory(filmDaoProvider, cameraDaoProvider, lensDaoProvider, accessoryDaoProvider, rollDaoProvider, chemicalDaoProvider, zoomLevelDaoProvider, settingDaoProvider, bulkRollDaoProvider);
  }

  public static VaultRepository newInstance(FilmDao filmDao, CameraDao cameraDao, LensDao lensDao,
      AccessoryDao accessoryDao, RollDao rollDao, ChemicalDao chemicalDao,
      ZoomLevelDao zoomLevelDao, SettingDao settingDao, BulkRollDao bulkRollDao) {
    return new VaultRepository(filmDao, cameraDao, lensDao, accessoryDao, rollDao, chemicalDao, zoomLevelDao, settingDao, bulkRollDao);
  }
}
