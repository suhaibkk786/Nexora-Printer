package nexora.printer.data.repository

import nexora.printer.core.escpos.Align
import nexora.printer.core.escpos.DitherMode
import nexora.printer.data.database.*
import nexora.printer.domain.model.*

private inline fun <reified E : Enum<E>> parseEnum(value: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == value } ?: default

fun PrinterSettingsEntity?.toDomain(): PrinterSettings =
    if (this == null) PrinterSettings() else PrinterSettings(
        encoding = encoding,
        codePage = codePage,
        density = density,
        leftMargin = leftMargin,
        rightMargin = rightMargin,
        autoCut = autoCut,
        feedLines = feedLines,
        ditheringMode = parseEnum(ditheringMode, DitherMode.FLOYD_STEINBERG),
        defaultAlignment = parseEnum(defaultAlignment, Align.LEFT),
        connectionTimeoutMs = connectionTimeoutMs,
        autoReconnect = autoReconnect
    )

fun PrinterWithSettings.toDomain(): PrinterProfile = PrinterProfile(
    id = printer.id,
    name = printer.name,
    connectionType = parseEnum(printer.connectionType, ConnectionType.BLUETOOTH),
    address = printer.address,
    paperWidth = PaperWidth.fromMm(printer.paperWidth),
    printableWidth = printer.printableWidth,
    isDefault = printer.isDefault,
    createdAt = printer.createdAt,
    settings = settings.toDomain()
)

fun PrinterProfile.toEntity() = PrinterEntity(
    id = id,
    name = name,
    address = address,
    connectionType = connectionType.name,
    paperWidth = paperWidth.mm,
    printableWidth = printableWidth,
    isDefault = isDefault,
    createdAt = createdAt
)

fun PrinterProfile.toSettingsEntity(printerId: Long = id) = PrinterSettingsEntity(
    printerId = printerId,
    encoding = settings.encoding,
    codePage = settings.codePage,
    density = settings.density,
    leftMargin = settings.leftMargin,
    rightMargin = settings.rightMargin,
    autoCut = settings.autoCut,
    feedLines = settings.feedLines,
    ditheringMode = settings.ditheringMode.name,
    defaultAlignment = settings.defaultAlignment.name,
    connectionTimeoutMs = settings.connectionTimeoutMs,
    autoReconnect = settings.autoReconnect
)

fun PrintJobEntity.toDomain() = PrintJob(
    id = id,
    printerId = printerId,
    name = name,
    printerName = printerName,
    connectionType = connectionType?.let { parseEnum(it, ConnectionType.BLUETOOTH) },
    type = parseEnum(jobType, JobType.TEXT),
    timestamp = timestamp,
    status = parseEnum(status, JobStatus.FAILED),
    errorMessage = errorMessage
)

fun PrintJob.toEntity() = PrintJobEntity(
    id = id,
    printerId = printerId,
    name = name,
    printerName = printerName,
    connectionType = connectionType?.name,
    jobType = type.name,
    timestamp = timestamp,
    status = status.name,
    errorMessage = errorMessage
)
