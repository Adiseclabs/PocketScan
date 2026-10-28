// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.content.Context
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Entity(tableName = "devices", indices = [Index("ip", unique = true)])
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ip: String, val mac: String? = null, val hostname: String = "", val vendor: String = "Unknown",
    val deviceType: String = "Unknown", val riskLevel: String = "Clean", val riskScore: Int = 0,
    val openPorts: String = "", val banner: String = "", val firstSeen: Long, val lastSeen: Long, val online: Boolean = true
)

@Entity(tableName = "cve_records", indices = [Index("service")])
data class CveRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val service: String, val fromV: String, val toV: String, val cveId: String,
    val cvss: Double, val severity: String, val description: String, val reference: String
)

@Entity(tableName = "cve_findings", indices = [Index("deviceIp")])
data class CveFindingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceIp: String, val cveId: String, val cvss: Double, val severity: String,
    val description: String, val reference: String, val fix: String, val foundAt: Long
)

@Entity(tableName = "domains", indices = [Index("name", unique = true)])
data class DomainEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val sourceIp: String, val seenAt: Long)

@Entity(tableName = "blocklist", indices = [Index("domain"), Index("ip")])
data class BlocklistEntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val domain: String?, val ip: String?, val source: String)

data class SourceCount(val source: String, val n: Int)

@Dao interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY riskScore DESC, lastSeen DESC") fun all(): Flow<List<DeviceEntity>>
    @Query("SELECT * FROM devices") suspend fun snapshot(): List<DeviceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: DeviceEntity): Long
    @Query("UPDATE devices SET online = 0") suspend fun markAllOffline()
    @Query("DELETE FROM devices WHERE lastSeen < :t") suspend fun purge(t: Long)
}

@Dao interface CveRecordDao {
    @Query("SELECT COUNT(*) FROM cve_records") suspend fun count(): Int
    @Insert suspend fun insertAll(r: List<CveRecordEntity>)
    @Query("SELECT * FROM cve_records WHERE service = :s") suspend fun forService(s: String): List<CveRecordEntity>
}

@Dao interface CveFindingDao {
    @Query("SELECT * FROM cve_findings ORDER BY cvss DESC") fun all(): Flow<List<CveFindingEntity>>
    @Query("DELETE FROM cve_findings WHERE deviceIp = :ip") suspend fun deleteFor(ip: String)
    @Insert suspend fun insertAll(r: List<CveFindingEntity>)
    @Query("DELETE FROM cve_findings WHERE foundAt < :t") suspend fun purge(t: Long)
}

@Dao interface DomainDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(d: DomainEntity)
    @Query("SELECT * FROM domains ORDER BY seenAt DESC") fun all(): Flow<List<DomainEntity>>
    @Query("DELETE FROM domains WHERE seenAt < :t") suspend fun purge(t: Long)
}

@Dao interface BlocklistDao {
    @Insert suspend fun insertAll(r: List<BlocklistEntryEntity>)
    @Query("DELETE FROM blocklist WHERE source = :s") suspend fun deleteBySource(s: String)
    @Query("SELECT COUNT(*) FROM blocklist WHERE source = :s") suspend fun countFor(s: String): Int
    @Query("SELECT COUNT(*) FROM blocklist") suspend fun total(): Int
    @Query("SELECT source, COUNT(*) AS n FROM blocklist GROUP BY source") fun counts(): Flow<List<SourceCount>>
    @Query("SELECT COUNT(*) FROM blocklist WHERE domain = :d") suspend fun hitDomain(d: String): Int
    @Query("SELECT COUNT(*) FROM blocklist WHERE ip = :ip") suspend fun hitIp(ip: String): Int
}

@Database(
    entities = [DeviceEntity::class, CveRecordEntity::class, CveFindingEntity::class, DomainEntity::class, BlocklistEntryEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun devices(): DeviceDao
    abstract fun cveRecords(): CveRecordDao
    abstract fun findings(): CveFindingDao
    abstract fun domains(): DomainDao
    abstract fun blocklist(): BlocklistDao
}

@Module @InstallIn(SingletonComponent::class)
object DbModule {
    @Provides @Singleton fun db(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "pocketlab.db").fallbackToDestructiveMigration().build()
    @Provides fun dd(d: AppDatabase) = d.devices()
    @Provides fun cr(d: AppDatabase) = d.cveRecords()
    @Provides fun cf(d: AppDatabase) = d.findings()
    @Provides fun dm(d: AppDatabase) = d.domains()
    @Provides fun bd(d: AppDatabase) = d.blocklist()
    @Provides @Singleton fun http(): OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
}
