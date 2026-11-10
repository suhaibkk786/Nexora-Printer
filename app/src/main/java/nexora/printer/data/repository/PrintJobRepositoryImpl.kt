package nexora.printer.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nexora.printer.data.database.PrintJobDao
import nexora.printer.domain.model.JobStatus
import nexora.printer.domain.model.PrintJob
import nexora.printer.domain.repository.PrintJobRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrintJobRepositoryImpl @Inject constructor(
    private val dao: PrintJobDao
) : PrintJobRepository {
    override fun observeJobs(): Flow<List<PrintJob>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun create(job: PrintJob): Long = dao.insert(job.toEntity().copy(id = 0))
    override suspend fun update(id: Long, status: JobStatus, error: String?, payload: ByteArray?) =
        dao.update(id, status.name, error, payload)
    override suspend fun get(id: Long): PrintJob? = dao.get(id)?.toDomain()
    override suspend fun getPayload(id: Long): ByteArray? = dao.getPayload(id)
    override suspend fun clear() = dao.clear()
}
