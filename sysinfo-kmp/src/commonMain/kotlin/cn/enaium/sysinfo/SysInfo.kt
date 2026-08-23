package cn.enaium.sysinfo

/**
 * Thrown when the underlying native library fails to allocate a resource or
 * returns an unrecoverable error.
 */
class SysInfoException(message: String) : RuntimeException(message)

// ===========================================================================
// Value types
// ===========================================================================

enum class ProcessStatus {
    Idle, Run, Sleep, Stop, Zombie, Tracing, Dead, Wakekill, Waking,
    Parked, LockBlocked, UninterruptibleDiskSleep, Suspended, Unknown,
}

enum class DiskKind {
    HDD, SSD, Unknown,
}

enum class InterfaceOperationalState {
    Other, Up, Down, Testing, Dormant, NotPresent, LowerLayerDown, Unknown,
}

enum class ThreadKind {
    Kernel, Userland,
}

enum class Signal {
    Hangup, Interrupt, Quit, Illegal, Trap, Abort, IOT, Bus,
    FloatingPointException, Kill, User1, Segv, User2, Pipe, Alarm, Term,
    Child, Continue, Stop, TSTP, TTIN, TTOU, Urgent, XCPU, XFSZ,
    VirtualAlarm, Profiling, Winch, IO, Poll, Power, Sys,
}

data class LoadAvg(val one: Double, val five: Double, val fifteen: Double)

data class DiskUsage(
    val totalWrittenBytes: ULong,
    val writtenBytes: ULong,
    val totalReadBytes: ULong,
    val readBytes: ULong,
)

data class CGroupLimits(
    val totalMemory: ULong,
    val freeMemory: ULong,
    val freeSwap: ULong,
    val rss: ULong,
)

data class Cpu(
    val name: String,
    val vendorId: String,
    val brand: String,
    /** Clock frequency in MHz. */
    val frequencyMHz: ULong,
    /** Per-CPU usage in percent (0..100). */
    val usage: Float,
)

data class Process(
    val pid: Long,
    val parentPid: Long?,
    val name: String,
    val cmd: List<String>,
    val exe: String?,
    val cwd: String?,
    val root: String?,
    val status: ProcessStatus,
    val memoryBytes: ULong,
    val virtualMemoryBytes: ULong,
    val cpuUsage: Float,
    val accumulatedCpuTime: ULong,
    val diskUsage: DiskUsage,
    val startTimeSeconds: ULong,
    val runTimeSeconds: ULong,
    val userId: String?,
    val groupId: String?,
    val effectiveUserId: String?,
    val effectiveGroupId: String?,
    val sessionId: Long?,
    val environ: List<String>,
    val exists: Boolean,
    val openFiles: Int?,
    val openFilesLimit: Int?,
    val tasks: List<Long>,
    val threadKind: ThreadKind?,
    val cgroupLimits: CGroupLimits?,
)

data class Disk(
    val name: String,
    val mountPoint: String,
    val fileSystem: String,
    val totalSpaceBytes: ULong,
    val availableSpaceBytes: ULong,
    val removable: Boolean,
    val readOnly: Boolean,
    val kind: DiskKind,
    val usage: DiskUsage,
)

data class NetworkInterface(
    val name: String,
    val macAddress: String,
    val mtuBytes: ULong,
    val receivedBytes: ULong,
    val totalReceivedBytes: ULong,
    val transmittedBytes: ULong,
    val totalTransmittedBytes: ULong,
    val packetsReceived: ULong,
    val totalPacketsReceived: ULong,
    val packetsTransmitted: ULong,
    val totalPacketsTransmitted: ULong,
    val errorsOnReceived: ULong,
    val totalErrorsOnReceived: ULong,
    val errorsOnTransmitted: ULong,
    val totalErrorsOnTransmitted: ULong,
    val ipAddresses: List<String>,
    val operationalState: InterfaceOperationalState,
)

data class Component(
    val label: String,
    val id: String?,
    /** Celsius, or null when the sensor reports no value. */
    val temperatureCelsius: Float?,
    val maxCelsius: Float?,
    val criticalCelsius: Float?,
)

data class UserInfo(
    val id: String,
    val groupId: String,
    val name: String,
    val groups: List<GroupInfo>,
)

data class GroupInfo(
    val id: String,
    val name: String,
)

data class MotherboardInfo(
    val name: String?,
    val vendorName: String?,
    val version: String?,
    val serialNumber: String?,
    val assetTag: String?,
)

data class ProductInfo(
    val name: String?,
    val family: String?,
    val serialNumber: String?,
    val stockKeepingUnit: String?,
    val uuid: String?,
    val version: String?,
    val vendorName: String?,
)

// ===========================================================================
// Platform bridge (expect/actual). Each platform implements these with
// primitives that mirror the C ABI; the public classes below materialize
// snapshots so callers never hold native pointers.
// ===========================================================================

internal expect class NativeSystem constructor(newAll: Boolean) : AutoCloseable {
    override fun close()
    fun refreshAll()
    fun refreshMemory()
    fun refreshCpu()
    fun refreshProcesses(): Long
    fun globalCpuUsage(): Float
    fun cpuCount(): Int
    fun cpuName(i: Int): String?
    fun cpuVendorId(i: Int): String?
    fun cpuBrand(i: Int): String?
    fun cpuFrequency(i: Int): ULong
    fun cpuUsage(i: Int): Float
    fun totalMemory(): ULong
    fun freeMemory(): ULong
    fun availableMemory(): ULong
    fun usedMemory(): ULong
    fun totalSwap(): ULong
    fun freeSwap(): ULong
    fun usedSwap(): ULong
    fun processCount(): Int
    fun pidAt(i: Int): Long
    fun processCpuUsage(pid: Long): Float
    fun processMemory(pid: Long): ULong
    fun processVirtualMemory(pid: Long): ULong
    fun processParent(pid: Long): Long
    fun processStatusCode(pid: Long): Int
    fun processStartTime(pid: Long): ULong
    fun processRunTime(pid: Long): ULong
    fun processName(pid: Long): String?
    fun processExe(pid: Long): String?
    fun processCwd(pid: Long): String?
    fun processRoot(pid: Long): String?
    fun processCmd(pid: Long): String?
    fun processEnviron(pid: Long): String?
    fun processUserId(pid: Long): String?
    fun processGroupId(pid: Long): String?
    fun processEffectiveUserId(pid: Long): String?
    fun processEffectiveGroupId(pid: Long): String?
    fun processSessionId(pid: Long): Long
    fun processAccumulatedCpuTime(pid: Long): ULong
    fun processDiskUsage(pid: Long): DiskUsage?
    fun processExists(pid: Long): Boolean
    fun processOpenFiles(pid: Long): Int?
    fun processOpenFilesLimit(pid: Long): Int?
    fun processCgroupLimits(pid: Long): CGroupLimits?
    fun processTasks(pid: Long): List<Long>
    fun processThreadKind(pid: Long): Int
    fun processKill(pid: Long): Boolean
    fun processKillWith(pid: Long, signal: Int): Int
}

internal expect object NativeInfo {
    fun name(): String?
    fun kernelVersion(): String?
    fun osVersion(): String?
    fun longOsVersion(): String?
    fun kernelLongVersion(): String
    fun distributionId(): String
    fun distributionIdLike(): List<String>
    fun hostName(): String?
    fun cpuArch(): String
    fun uptime(): ULong
    fun bootTime(): ULong
    fun loadAverage(): LoadAvg?
    fun physicalCoreCount(): Int?
    fun openFilesLimit(): Int?
    fun cgroupLimits(): CGroupLimits?
    fun isSupportedSystem(): Boolean
    fun minimumCpuUpdateIntervalMs(): ULong
}

internal expect class NativeDisks constructor() : AutoCloseable {
    override fun close()
    fun refresh(removeNotListed: Boolean)
    fun count(): Int
    fun name(i: Int): String?
    fun mountPoint(i: Int): String?
    fun fileSystem(i: Int): String?
    fun totalSpace(i: Int): ULong
    fun availableSpace(i: Int): ULong
    fun isRemovable(i: Int): Boolean
    fun isReadOnly(i: Int): Boolean
    fun kind(i: Int): Int
    fun usage(i: Int): DiskUsage?
}

internal expect class NativeNetworks constructor() : AutoCloseable {
    override fun close()
    fun refresh(removeNotListed: Boolean)
    fun count(): Int
    fun name(i: Int): String?
    fun received(i: Int): ULong
    fun totalReceived(i: Int): ULong
    fun transmitted(i: Int): ULong
    fun totalTransmitted(i: Int): ULong
    fun packetsReceived(i: Int): ULong
    fun totalPacketsReceived(i: Int): ULong
    fun packetsTransmitted(i: Int): ULong
    fun totalPacketsTransmitted(i: Int): ULong
    fun errorsOnReceived(i: Int): ULong
    fun totalErrorsOnReceived(i: Int): ULong
    fun errorsOnTransmitted(i: Int): ULong
    fun totalErrorsOnTransmitted(i: Int): ULong
    fun mtu(i: Int): ULong
    fun macAddress(i: Int): String?
    fun ipCount(i: Int): Int
    fun ipAt(i: Int, j: Int): String?
    fun operationalState(i: Int): Int
}

internal expect class NativeComponents constructor() : AutoCloseable {
    override fun close()
    fun refresh(removeNotListed: Boolean)
    fun count(): Int
    fun label(i: Int): String?
    fun id(i: Int): String?
    /** NaN when the sensor reports no value. */
    fun temperature(i: Int): Float
    fun max(i: Int): Float
    fun critical(i: Int): Float
}

internal expect class NativeUsers constructor() : AutoCloseable {
    override fun close()
    fun refresh()
    fun count(): Int
    fun id(i: Int): String?
    fun groupId(i: Int): String?
    fun name(i: Int): String?
    fun groupsCount(i: Int): Int
    fun groupAt(i: Int, j: Int): String?
}

internal expect class NativeGroups constructor() : AutoCloseable {
    override fun close()
    fun refresh()
    fun count(): Int
    fun id(i: Int): String?
    fun name(i: Int): String?
}

internal expect object NativeMotherboard {
    fun info(): MotherboardInfo?
}

internal expect object NativeProduct {
    fun info(): ProductInfo?
}

// ===========================================================================
// Public API (platform-independent wrappers over the bridge)
// ===========================================================================

/**
 * A live view over the host system. Create with [System], call a refresh
 * method, then read the snapshot properties.
 */
class System constructor(newAll: Boolean = true) : AutoCloseable {
    private val native = NativeSystem(newAll)

    fun refreshAll() = native.refreshAll()
    fun refreshMemory() = native.refreshMemory()
    fun refreshCpu() = native.refreshCpu()
    fun refreshProcesses(): Long = native.refreshProcesses()

    val globalCpuUsage: Float get() = native.globalCpuUsage()
    val cpus: List<Cpu>
        get() = (0 until native.cpuCount()).map { i ->
            Cpu(
                name = native.cpuName(i).orEmpty(),
                vendorId = native.cpuVendorId(i).orEmpty(),
                brand = native.cpuBrand(i).orEmpty(),
                frequencyMHz = native.cpuFrequency(i),
                usage = native.cpuUsage(i),
            )
        }

    val totalMemory: ULong get() = native.totalMemory()
    val freeMemory: ULong get() = native.freeMemory()
    val availableMemory: ULong get() = native.availableMemory()
    val usedMemory: ULong get() = native.usedMemory()
    val totalSwap: ULong get() = native.totalSwap()
    val freeSwap: ULong get() = native.freeSwap()
    val usedSwap: ULong get() = native.usedSwap()

    val processCount: Int get() = native.processCount()

    /** Snapshot of all live processes by their current PIDs. */
    fun processes(): List<Process> {
        val n = native.processCount()
        return buildList(n) {
            for (i in 0 until n) {
                val pid = native.pidAt(i)
                if (pid < 0) continue
                process(pid)?.let { add(it) }
            }
        }
    }

    fun process(pid: Long): Process? {
        if (pid < 0) return null
        // Use name as existence check: if the process doesn't exist, the native layer
        // returns null for name and 0 for most fields; we treat that as missing.
        // A more precise check is exists(), but we still want to return a snapshot
        // even for non-existent PIDs (the caller can check exists).
        val name = native.processName(pid) ?: return null
        val parent = native.processParent(pid).let { if (it < 0) null else it }
        val status = processStatusFromCode(native.processStatusCode(pid))
        val cmd = native.processCmd(pid).orEmpty().split('\u001F').filter { it.isNotEmpty() }
        val environ = native.processEnviron(pid).orEmpty().split('\u001F').filter { it.isNotEmpty() }
        return Process(
            pid = pid,
            parentPid = parent,
            name = name,
            cmd = cmd,
            exe = native.processExe(pid),
            cwd = native.processCwd(pid),
            root = native.processRoot(pid),
            status = status,
            memoryBytes = native.processMemory(pid),
            virtualMemoryBytes = native.processVirtualMemory(pid),
            cpuUsage = native.processCpuUsage(pid),
            accumulatedCpuTime = native.processAccumulatedCpuTime(pid),
            diskUsage = native.processDiskUsage(pid) ?: DiskUsage(0u, 0u, 0u, 0u),
            startTimeSeconds = native.processStartTime(pid),
            runTimeSeconds = native.processRunTime(pid),
            userId = native.processUserId(pid),
            groupId = native.processGroupId(pid),
            effectiveUserId = native.processEffectiveUserId(pid),
            effectiveGroupId = native.processEffectiveGroupId(pid),
            sessionId = native.processSessionId(pid).let { if (it < 0) null else it },
            environ = environ,
            exists = native.processExists(pid),
            openFiles = native.processOpenFiles(pid),
            openFilesLimit = native.processOpenFilesLimit(pid),
            tasks = native.processTasks(pid),
            threadKind = threadKindFromCode(native.processThreadKind(pid)),
            cgroupLimits = native.processCgroupLimits(pid),
        )
    }

    fun kill(pid: Long): Boolean = native.processKill(pid)
    fun killWith(pid: Long, signal: Signal): Int = native.processKillWith(pid, signal.ordinal)

    override fun close() = native.close()

    companion object {
        fun name(): String? = NativeInfo.name()
        fun kernelVersion(): String? = NativeInfo.kernelVersion()
        fun osVersion(): String? = NativeInfo.osVersion()
        fun longOsVersion(): String? = NativeInfo.longOsVersion()
        fun kernelLongVersion(): String = NativeInfo.kernelLongVersion()
        fun distributionId(): String = NativeInfo.distributionId()
        fun distributionIdLike(): List<String> = NativeInfo.distributionIdLike()
        fun hostName(): String? = NativeInfo.hostName()
        fun cpuArch(): String = NativeInfo.cpuArch()
        fun uptime(): ULong = NativeInfo.uptime()
        fun bootTime(): ULong = NativeInfo.bootTime()
        fun loadAverage(): LoadAvg? = NativeInfo.loadAverage()
        fun physicalCoreCount(): Int? = NativeInfo.physicalCoreCount()
        fun openFilesLimit(): Int? = NativeInfo.openFilesLimit()
        fun cgroupLimits(): CGroupLimits? = NativeInfo.cgroupLimits()
        fun isSupportedSystem(): Boolean = NativeInfo.isSupportedSystem()
        fun minimumCpuUpdateIntervalMs(): ULong = NativeInfo.minimumCpuUpdateIntervalMs()
    }
}

class Disks : AutoCloseable {
    private val native = NativeDisks()

    fun refresh(removeNotListed: Boolean = false) = native.refresh(removeNotListed)

    val list: List<Disk>
        get() = (0 until native.count()).map { i ->
            Disk(
                name = native.name(i).orEmpty(),
                mountPoint = native.mountPoint(i).orEmpty(),
                fileSystem = native.fileSystem(i).orEmpty(),
                totalSpaceBytes = native.totalSpace(i),
                availableSpaceBytes = native.availableSpace(i),
                removable = native.isRemovable(i),
                readOnly = native.isReadOnly(i),
                kind = diskKindFromCode(native.kind(i)),
                usage = native.usage(i) ?: DiskUsage(0u, 0u, 0u, 0u),
            )
        }

    override fun close() = native.close()
}

class Networks : AutoCloseable {
    private val native = NativeNetworks()

    fun refresh(removeNotListed: Boolean = false) = native.refresh(removeNotListed)

    val list: List<NetworkInterface>
        get() = (0 until native.count()).map { i ->
            NetworkInterface(
                name = native.name(i).orEmpty(),
                macAddress = native.macAddress(i).orEmpty(),
                mtuBytes = native.mtu(i),
                receivedBytes = native.received(i),
                totalReceivedBytes = native.totalReceived(i),
                transmittedBytes = native.transmitted(i),
                totalTransmittedBytes = native.totalTransmitted(i),
                packetsReceived = native.packetsReceived(i),
                totalPacketsReceived = native.totalPacketsReceived(i),
                packetsTransmitted = native.packetsTransmitted(i),
                totalPacketsTransmitted = native.totalPacketsTransmitted(i),
                errorsOnReceived = native.errorsOnReceived(i),
                totalErrorsOnReceived = native.totalErrorsOnReceived(i),
                errorsOnTransmitted = native.errorsOnTransmitted(i),
                totalErrorsOnTransmitted = native.totalErrorsOnTransmitted(i),
                ipAddresses = (0 until native.ipCount(i)).mapNotNull { native.ipAt(i, it) },
                operationalState = interfaceStateFromCode(native.operationalState(i)),
            )
        }

    override fun close() = native.close()
}

class Components : AutoCloseable {
    private val native = NativeComponents()

    fun refresh(removeNotListed: Boolean = false) = native.refresh(removeNotListed)

    val list: List<Component>
        get() = (0 until native.count()).map { i ->
            Component(
                label = native.label(i).orEmpty(),
                id = native.id(i),
                temperatureCelsius = native.temperature(i).takeUnless { it.isNaN() },
                maxCelsius = native.max(i).takeUnless { it.isNaN() },
                criticalCelsius = native.critical(i).takeUnless { it.isNaN() },
            )
        }

    override fun close() = native.close()
}

class Users : AutoCloseable {
    private val native = NativeUsers()

    fun refresh() = native.refresh()

    val list: List<UserInfo>
        get() = (0 until native.count()).map { i ->
            val groups = (0 until native.groupsCount(i)).mapNotNull { j ->
                native.groupAt(i, j)?.let { raw ->
                    // raw is "Gid:GroupName" from Rust
                    val colon = raw.indexOf(':')
                    if (colon >= 0) GroupInfo(raw.substring(0, colon), raw.substring(colon + 1))
                    else GroupInfo(raw, raw)
                }
            }
            UserInfo(
                id = native.id(i).orEmpty(),
                groupId = native.groupId(i).orEmpty(),
                name = native.name(i).orEmpty(),
                groups = groups,
            )
        }

    override fun close() = native.close()
}

class Groups : AutoCloseable {
    private val native = NativeGroups()

    fun refresh() = native.refresh()

    val list: List<GroupInfo>
        get() = (0 until native.count()).map { i ->
            GroupInfo(
                id = native.id(i).orEmpty(),
                name = native.name(i).orEmpty(),
            )
        }

    override fun close() = native.close()
}

object Motherboard {
    fun info(): MotherboardInfo? = NativeMotherboard.info()
}

object Product {
    fun info(): ProductInfo? = NativeProduct.info()
}

private fun processStatusFromCode(code: Int): ProcessStatus = when (code) {
    0 -> ProcessStatus.Idle
    1 -> ProcessStatus.Run
    2 -> ProcessStatus.Sleep
    3 -> ProcessStatus.Stop
    4 -> ProcessStatus.Zombie
    5 -> ProcessStatus.Tracing
    6 -> ProcessStatus.Dead
    7 -> ProcessStatus.Wakekill
    8 -> ProcessStatus.Waking
    9 -> ProcessStatus.Parked
    10 -> ProcessStatus.LockBlocked
    11 -> ProcessStatus.UninterruptibleDiskSleep
    12 -> ProcessStatus.Suspended
    else -> ProcessStatus.Unknown
}

private fun diskKindFromCode(code: Int): DiskKind = when (code) {
    0 -> DiskKind.HDD
    1 -> DiskKind.SSD
    else -> DiskKind.Unknown
}

private fun interfaceStateFromCode(code: Int): InterfaceOperationalState = when (code) {
    1 -> InterfaceOperationalState.Up
    2 -> InterfaceOperationalState.Down
    3 -> InterfaceOperationalState.Testing
    4 -> InterfaceOperationalState.Dormant
    5 -> InterfaceOperationalState.NotPresent
    6 -> InterfaceOperationalState.LowerLayerDown
    0 -> InterfaceOperationalState.Other
    else -> InterfaceOperationalState.Unknown
}

private fun threadKindFromCode(code: Int): ThreadKind? = when (code) {
    0 -> ThreadKind.Kernel
    1 -> ThreadKind.Userland
    else -> null
}
