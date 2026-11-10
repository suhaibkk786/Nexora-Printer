package nexora.printer.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nexora.printer.core.printer.PrinterConnector
import nexora.printer.core.printer.PrinterManager
import nexora.printer.data.database.NexoraDatabase
import nexora.printer.data.database.PrintJobDao
import nexora.printer.data.database.PrinterDao
import nexora.printer.data.preferences.PreferencesRepositoryImpl
import nexora.printer.data.repository.PrintJobRepositoryImpl
import nexora.printer.data.repository.PrinterRepositoryImpl
import nexora.printer.domain.repository.PreferencesRepository
import nexora.printer.domain.repository.PrintJobRepository
import nexora.printer.domain.repository.PrinterRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NexoraDatabase =
        Room.databaseBuilder(context, NexoraDatabase::class.java, "nexora.db").build()

    @Provides fun printerDao(db: NexoraDatabase): PrinterDao = db.printerDao()
    @Provides fun printJobDao(db: NexoraDatabase): PrintJobDao = db.printJobDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun printers(impl: PrinterRepositoryImpl): PrinterRepository
    @Binds abstract fun jobs(impl: PrintJobRepositoryImpl): PrintJobRepository
    @Binds abstract fun prefs(impl: PreferencesRepositoryImpl): PreferencesRepository
    @Binds abstract fun connector(impl: PrinterManager): PrinterConnector
}
