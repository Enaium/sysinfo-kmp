package cn.enaium.sysinfo

actual class NativeSystem actual constructor(newAll: Boolean) : AutoCloseable {
    private val handle: Long = Native.systemNew(newAll)
    actual fun refreshAll() = Native.systemRefreshAll(handle)
    actual fun refreshMemory() = Native.systemRefreshMemory(handle)
    actual fun refreshCpu() = Native.systemRefreshCpu(handle)
    actual fun refreshProcesses(): Long = Native.systemRefreshProcesses(handle)
    actual fun globalCpuUsage(): Float = Native.globalCpuUsage(handle)
    actual fun cpuCount(): Int = Native.cpuCount(handle).toInt()
    actual fun cpuName(i: Int): String? = Native.cpuName(handle, i.toLong())
    actual fun cpuVendorId(i: Int): String? = Native.cpuVendorId(handle, i.toLong())
    actual fun cpuBrand(i: Int): String? = Native.cpuBrand(handle, i.toLong())
    actual fun cpuFrequency(i: Int): ULong = Native.cpuFrequency(handle, i.toLong()).toULong()
    actual fun cpuUsage(i: Int): Float = Native.cpuUsage(handle, i.toLong())
    actual fun totalMemory(): ULong = Native.totalMemory(handle).toULong()
    actual fun freeMemory(): ULong = Native.freeMemory(handle).toULong()
    actual fun availableMemory(): ULong = Native.availableMemory(handle).toULong()
    actual fun usedMemory(): ULong = Native.usedMemory(handle).toULong()
    actual fun totalSwap(): ULong = Native.totalSwap(handle).toULong()
    actual fun freeSwap(): ULong = Native.freeSwap(handle).toULong()
    actual fun usedSwap(): ULong = Native.usedSwap(handle).toULong()
    actual fun processCount(): Int = Native.processCount(handle).toInt()
    actual fun pidAt(i: Int): Long = Native.pidAt(handle, i.toLong())
    actual fun processCpuUsage(pid: Long): Float = Native.processCpuUsage(handle, pid)
    actual fun processMemory(pid: Long): ULong = Native.processMemory(handle, pid).toULong()
    actual fun processVirtualMemory(pid: Long): ULong = Native.processVirtualMemory(handle, pid).toULong()
    actual fun processParent(pid: Long): Long = Native.processParent(handle, pid)
    actual fun processStatusCode(pid: Long): Int = Native.processStatus(handle, pid)
    actual fun processStartTime(pid: Long): ULong = Native.processStartTime(handle, pid).toULong()
    actual fun processRunTime(pid: Long): ULong = Native.processRunTime(handle, pid).toULong()
    actual fun processName(pid: Long): String? = Native.processName(handle, pid)
    actual fun processExe(pid: Long): String? = Native.processExe(handle, pid)
    actual fun processCwd(pid: Long): String? = Native.processCwd(handle, pid)
    actual fun processRoot(pid: Long): String? = Native.processRoot(handle, pid)
    actual fun processCmd(pid: Long): String? = Native.processCmd(handle, pid)
    actual fun processEnviron(pid: Long): String? = Native.processEnviron(handle, pid)
    actual fun processUserId(pid: Long): String? = Native.processUserId(handle, pid)
    actual fun processGroupId(pid: Long): String? = Native.processGroupId(handle, pid)
    actual fun processEffectiveUserId(pid: Long): String? = Native.processEffectiveUserId(handle, pid)
    actual fun processEffectiveGroupId(pid: Long): String? = Native.processEffectiveGroupId(handle, pid)
    actual fun processSessionId(pid: Long): Long = Native.processSessionId(handle, pid)
    actual fun processAccumulatedCpuTime(pid: Long): ULong = Native.processAccumulatedCpuTime(handle, pid).toULong()
    actual fun processDiskUsage(pid: Long): DiskUsage? = Native.processDiskUsage(handle, pid)?.let { arr ->
        DiskUsage(arr[0].toULong(), arr[1].toULong(), arr[2].toULong(), arr[3].toULong())
    }
    actual fun processExists(pid: Long): Boolean = Native.processExists(handle, pid)
    actual fun processOpenFiles(pid: Long): Int? = Native.processOpenFiles(handle, pid).let { if (it < 0) null else it.toInt() }
    actual fun processOpenFilesLimit(pid: Long): Int? = Native.processOpenFilesLimit(handle, pid).let { if (it < 0) null else it.toInt() }
    actual fun processCgroupLimits(pid: Long): CGroupLimits? = Native.processCgroupLimits(handle, pid)?.let { arr ->
        CGroupLimits(arr[0].toULong(), arr[1].toULong(), arr[2].toULong(), arr[3].toULong())
    }
    actual fun processTasks(pid: Long): List<Long> = (0 until Native.processTasksCount(handle, pid).toInt()).map { Native.processTasksPidAt(handle, pid, it.toLong()) }
    actual fun processThreadKind(pid: Long): Int = Native.processThreadKind(handle, pid)
    actual fun processKill(pid: Long): Boolean = Native.processKill(handle, pid)
    actual fun processKillWith(pid: Long, signal: Int): Int = Native.processKillWith(handle, pid, signal)
    actual override fun close() = Native.systemFree(handle)
}

actual object NativeInfo {
    actual fun name(): String? = Native.name()
    actual fun kernelVersion(): String? = Native.kernelVersion()
    actual fun osVersion(): String? = Native.osVersion()
    actual fun longOsVersion(): String? = Native.longOsVersion()
    actual fun kernelLongVersion(): String = Native.systemKernelLongVersion() ?: ""
    actual fun distributionId(): String = Native.distributionId() ?: ""
    actual fun distributionIdLike(): List<String> = (0 until Native.systemDistributionIdLikeCount().toInt()).mapNotNull { Native.systemDistributionIdLikeAt(it.toLong()) }
    actual fun hostName(): String? = Native.hostName()
    actual fun cpuArch(): String = Native.cpuArch() ?: ""
    actual fun uptime(): ULong = Native.uptime().toULong()
    actual fun bootTime(): ULong = Native.bootTime().toULong()
    actual fun loadAverage(): LoadAvg? = Native.loadAverage()?.let { LoadAvg(it[0], it[1], it[2]) }
    actual fun physicalCoreCount(): Int? = Native.physicalCoreCount().let { if (it < 0) null else it.toInt() }
    actual fun openFilesLimit(): Int? = Native.systemOpenFilesLimit().let { if (it < 0) null else it.toInt() }
    actual fun cgroupLimits(): CGroupLimits? = Native.systemCgroupLimits()?.let { arr ->
        CGroupLimits(arr[0].toULong(), arr[1].toULong(), arr[2].toULong(), arr[3].toULong())
    }
    actual fun isSupportedSystem(): Boolean = Native.isSupportedSystem()
    actual fun minimumCpuUpdateIntervalMs(): ULong = Native.minimumCpuUpdateIntervalMs().toULong()
}

actual class NativeDisks actual constructor() : AutoCloseable {
    private val handle: Long = Native.disksNew()
    actual fun refresh(removeNotListed: Boolean) = Native.disksRefresh(handle, removeNotListed)
    actual fun count(): Int = Native.diskCount(handle).toInt()
    actual fun name(i: Int): String? = Native.diskName(handle, i.toLong())
    actual fun mountPoint(i: Int): String? = Native.diskMountPoint(handle, i.toLong())
    actual fun fileSystem(i: Int): String? = Native.diskFileSystem(handle, i.toLong())
    actual fun totalSpace(i: Int): ULong = Native.diskTotalSpace(handle, i.toLong()).toULong()
    actual fun availableSpace(i: Int): ULong = Native.diskAvailableSpace(handle, i.toLong()).toULong()
    actual fun isRemovable(i: Int): Boolean = Native.diskIsRemovable(handle, i.toLong())
    actual fun isReadOnly(i: Int): Boolean = Native.diskIsReadOnly(handle, i.toLong())
    actual fun kind(i: Int): Int = Native.diskKind(handle, i.toLong())
    actual fun usage(i: Int): DiskUsage? = Native.diskUsage(handle, i.toLong())?.let { arr ->
        DiskUsage(arr[0].toULong(), arr[1].toULong(), arr[2].toULong(), arr[3].toULong())
    }
    actual override fun close() = Native.disksFree(handle)
}

actual class NativeNetworks actual constructor() : AutoCloseable {
    private val handle: Long = Native.networksNew()
    actual fun refresh(removeNotListed: Boolean) = Native.networksRefresh(handle, removeNotListed)
    actual fun count(): Int = Native.networkCount(handle).toInt()
    actual fun name(i: Int): String? = Native.networkName(handle, i.toLong())
    actual fun received(i: Int): ULong = Native.networkReceived(handle, i.toLong()).toULong()
    actual fun totalReceived(i: Int): ULong = Native.networkTotalReceived(handle, i.toLong()).toULong()
    actual fun transmitted(i: Int): ULong = Native.networkTransmitted(handle, i.toLong()).toULong()
    actual fun totalTransmitted(i: Int): ULong = Native.networkTotalTransmitted(handle, i.toLong()).toULong()
    actual fun packetsReceived(i: Int): ULong = Native.networkPacketsReceived(handle, i.toLong()).toULong()
    actual fun totalPacketsReceived(i: Int): ULong = Native.networkTotalPacketsReceived(handle, i.toLong()).toULong()
    actual fun packetsTransmitted(i: Int): ULong = Native.networkPacketsTransmitted(handle, i.toLong()).toULong()
    actual fun totalPacketsTransmitted(i: Int): ULong = Native.networkTotalPacketsTransmitted(handle, i.toLong()).toULong()
    actual fun errorsOnReceived(i: Int): ULong = Native.networkErrorsOnReceived(handle, i.toLong()).toULong()
    actual fun totalErrorsOnReceived(i: Int): ULong = Native.networkTotalErrorsOnReceived(handle, i.toLong()).toULong()
    actual fun errorsOnTransmitted(i: Int): ULong = Native.networkErrorsOnTransmitted(handle, i.toLong()).toULong()
    actual fun totalErrorsOnTransmitted(i: Int): ULong = Native.networkTotalErrorsOnTransmitted(handle, i.toLong()).toULong()
    actual fun mtu(i: Int): ULong = Native.networkMtu(handle, i.toLong()).toULong()
    actual fun macAddress(i: Int): String? = Native.networkMacAddress(handle, i.toLong())
    actual fun ipCount(i: Int): Int = Native.networkIpCount(handle, i.toLong()).toInt()
    actual fun ipAt(i: Int, j: Int): String? = Native.networkIpAt(handle, i.toLong(), j.toLong())
    actual fun operationalState(i: Int): Int = Native.networkOperationalState(handle, i.toLong())
    actual override fun close() = Native.networksFree(handle)
}

actual class NativeComponents actual constructor() : AutoCloseable {
    private val handle: Long = Native.componentsNew()
    actual fun refresh(removeNotListed: Boolean) = Native.componentsRefresh(handle, removeNotListed)
    actual fun count(): Int = Native.componentCount(handle).toInt()
    actual fun label(i: Int): String? = Native.componentLabel(handle, i.toLong())
    actual fun id(i: Int): String? = Native.componentId(handle, i.toLong())
    actual fun temperature(i: Int): Float = Native.componentTemperature(handle, i.toLong())
    actual fun max(i: Int): Float = Native.componentMax(handle, i.toLong())
    actual fun critical(i: Int): Float = Native.componentCritical(handle, i.toLong())
    actual override fun close() = Native.componentsFree(handle)
}

actual class NativeUsers actual constructor() : AutoCloseable {
    private val handle: Long = Native.usersNew()
    actual fun refresh() = Native.usersRefresh(handle)
    actual fun count(): Int = Native.userCount(handle).toInt()
    actual fun id(i: Int): String? = Native.userId(handle, i.toLong())
    actual fun groupId(i: Int): String? = Native.userGroupId(handle, i.toLong())
    actual fun name(i: Int): String? = Native.userName(handle, i.toLong())
    actual fun groupsCount(i: Int): Int = Native.userGroupsCount(handle, i.toLong()).toInt()
    actual fun groupAt(i: Int, j: Int): String? = Native.userGroupAt(handle, i.toLong(), j.toLong())
    actual override fun close() = Native.usersFree(handle)
}

actual class NativeGroups actual constructor() : AutoCloseable {
    private val handle: Long = Native.groupsNew()
    actual fun refresh() = Native.groupsRefresh(handle)
    actual fun count(): Int = Native.groupCount(handle).toInt()
    actual fun id(i: Int): String? = Native.groupId(handle, i.toLong())
    actual fun name(i: Int): String? = Native.groupName(handle, i.toLong())
    actual override fun close() = Native.groupsFree(handle)
}

actual object NativeMotherboard {
    actual fun info(): MotherboardInfo? {
        val name = Native.motherboardName()
        val vendor = Native.motherboardVendorName()
        val version = Native.motherboardVersion()
        val serial = Native.motherboardSerialNumber()
        val asset = Native.motherboardAssetTag()
        if (name == null && vendor == null && version == null && serial == null && asset == null) return null
        return MotherboardInfo(name, vendor, version, serial, asset)
    }
}

actual object NativeProduct {
    actual fun info(): ProductInfo? {
        val name = Native.productName()
        val family = Native.productFamily()
        val serial = Native.productSerialNumber()
        val sku = Native.productStockKeepingUnit()
        val uuid = Native.productUuid()
        val version = Native.productVersion()
        val vendor = Native.productVendorName()
        if (name == null && family == null && serial == null && sku == null && uuid == null && version == null && vendor == null) return null
        return ProductInfo(name, family, serial, sku, uuid, version, vendor)
    }
}
