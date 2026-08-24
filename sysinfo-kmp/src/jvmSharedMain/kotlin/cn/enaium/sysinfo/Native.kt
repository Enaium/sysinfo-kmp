package cn.enaium.sysinfo

/**
 * JNI bridge for the JVM target.
 *
 * Every `@JvmStatic external fun` maps 1:1 to a
 * `Java_cn_enaium_sysinfo_Native_<name>` function exported by the Rust cdylib
 * in `rust/`. All members are declared in a non-internal `object` so their JVM
 * names are not mangled; the [NativeLoader] is invoked once from the init block.
 *
 * The bridge exchanges only scalars, `Long` handles, `String`s and a
 * `DoubleArray` (for load average); the Kotlin side wraps these into the
 * snapshot types defined in commonMain.
 */
internal object Native {

    init {
        NativeLoader.load()
    }

    // ---- static system information ----
    @JvmStatic external fun name(): String?
    @JvmStatic external fun kernelVersion(): String?
    @JvmStatic external fun osVersion(): String?
    @JvmStatic external fun longOsVersion(): String?
    @JvmStatic external fun distributionId(): String?
    @JvmStatic external fun hostName(): String?
    @JvmStatic external fun cpuArch(): String?
    @JvmStatic external fun uptime(): Long
    @JvmStatic external fun bootTime(): Long
    @JvmStatic external fun loadAverage(): DoubleArray?
    @JvmStatic external fun physicalCoreCount(): Long

    // ---- system lifecycle ----
    @JvmStatic external fun systemNew(all: Boolean): Long
    @JvmStatic external fun systemFree(handle: Long)
    @JvmStatic external fun systemRefreshAll(handle: Long)
    @JvmStatic external fun systemRefreshMemory(handle: Long)
    @JvmStatic external fun systemRefreshCpu(handle: Long)
    @JvmStatic external fun systemRefreshProcesses(handle: Long): Long
    @JvmStatic external fun globalCpuUsage(handle: Long): Float
    @JvmStatic external fun cpuCount(handle: Long): Long
    @JvmStatic external fun cpuName(handle: Long, index: Long): String?
    @JvmStatic external fun cpuVendorId(handle: Long, index: Long): String?
    @JvmStatic external fun cpuBrand(handle: Long, index: Long): String?
    @JvmStatic external fun cpuFrequency(handle: Long, index: Long): Long
    @JvmStatic external fun cpuUsage(handle: Long, index: Long): Float
    @JvmStatic external fun totalMemory(handle: Long): Long
    @JvmStatic external fun freeMemory(handle: Long): Long
    @JvmStatic external fun availableMemory(handle: Long): Long
    @JvmStatic external fun usedMemory(handle: Long): Long
    @JvmStatic external fun totalSwap(handle: Long): Long
    @JvmStatic external fun freeSwap(handle: Long): Long
    @JvmStatic external fun usedSwap(handle: Long): Long

    // ---- processes ----
    @JvmStatic external fun processCount(handle: Long): Long
    @JvmStatic external fun pidAt(handle: Long, index: Long): Long
    @JvmStatic external fun processCpuUsage(handle: Long, pid: Long): Float
    @JvmStatic external fun processMemory(handle: Long, pid: Long): Long
    @JvmStatic external fun processVirtualMemory(handle: Long, pid: Long): Long
    @JvmStatic external fun processParent(handle: Long, pid: Long): Long
    @JvmStatic external fun processStatus(handle: Long, pid: Long): Int
    @JvmStatic external fun processStartTime(handle: Long, pid: Long): Long
    @JvmStatic external fun processRunTime(handle: Long, pid: Long): Long
    @JvmStatic external fun processName(handle: Long, pid: Long): String?
    @JvmStatic external fun processExe(handle: Long, pid: Long): String?
    @JvmStatic external fun processCwd(handle: Long, pid: Long): String?
    @JvmStatic external fun processCmd(handle: Long, pid: Long): String?
    @JvmStatic external fun processUserId(handle: Long, pid: Long): String?
    @JvmStatic external fun processGroupId(handle: Long, pid: Long): String?

    // ---- disks ----
    @JvmStatic external fun disksNew(): Long
    @JvmStatic external fun disksFree(handle: Long)
    @JvmStatic external fun disksRefresh(handle: Long, removeNotListed: Boolean)
    @JvmStatic external fun diskCount(handle: Long): Long
    @JvmStatic external fun diskName(handle: Long, index: Long): String?
    @JvmStatic external fun diskMountPoint(handle: Long, index: Long): String?
    @JvmStatic external fun diskFileSystem(handle: Long, index: Long): String?
    @JvmStatic external fun diskTotalSpace(handle: Long, index: Long): Long
    @JvmStatic external fun diskAvailableSpace(handle: Long, index: Long): Long
    @JvmStatic external fun diskIsRemovable(handle: Long, index: Long): Boolean
    @JvmStatic external fun diskIsReadOnly(handle: Long, index: Long): Boolean
    @JvmStatic external fun diskKind(handle: Long, index: Long): Int

    // ---- networks ----
    @JvmStatic external fun networksNew(): Long
    @JvmStatic external fun networksFree(handle: Long)
    @JvmStatic external fun networksRefresh(handle: Long, removeNotListed: Boolean)
    @JvmStatic external fun networkCount(handle: Long): Long
    @JvmStatic external fun networkName(handle: Long, index: Long): String?
    @JvmStatic external fun networkReceived(handle: Long, index: Long): Long
    @JvmStatic external fun networkTotalReceived(handle: Long, index: Long): Long
    @JvmStatic external fun networkTransmitted(handle: Long, index: Long): Long
    @JvmStatic external fun networkTotalTransmitted(handle: Long, index: Long): Long
    @JvmStatic external fun networkPacketsReceived(handle: Long, index: Long): Long
    @JvmStatic external fun networkTotalPacketsReceived(handle: Long, index: Long): Long
    @JvmStatic external fun networkPacketsTransmitted(handle: Long, index: Long): Long
    @JvmStatic external fun networkTotalPacketsTransmitted(handle: Long, index: Long): Long
    @JvmStatic external fun networkErrorsOnReceived(handle: Long, index: Long): Long
    @JvmStatic external fun networkTotalErrorsOnReceived(handle: Long, index: Long): Long
    @JvmStatic external fun networkErrorsOnTransmitted(handle: Long, index: Long): Long
    @JvmStatic external fun networkTotalErrorsOnTransmitted(handle: Long, index: Long): Long
    @JvmStatic external fun networkMtu(handle: Long, index: Long): Long
    @JvmStatic external fun networkMacAddress(handle: Long, index: Long): String?
    @JvmStatic external fun networkIpCount(handle: Long, index: Long): Long
    @JvmStatic external fun networkIpAt(handle: Long, index: Long, ipIndex: Long): String?

    // ---- components ----
    @JvmStatic external fun componentsNew(): Long
    @JvmStatic external fun componentsFree(handle: Long)
    @JvmStatic external fun componentsRefresh(handle: Long, removeNotListed: Boolean)
    @JvmStatic external fun componentCount(handle: Long): Long
    @JvmStatic external fun componentLabel(handle: Long, index: Long): String?
    @JvmStatic external fun componentId(handle: Long, index: Long): String?
    @JvmStatic external fun componentTemperature(handle: Long, index: Long): Float
    @JvmStatic external fun componentMax(handle: Long, index: Long): Float
    @JvmStatic external fun componentCritical(handle: Long, index: Long): Float

    // ---- users ----
    @JvmStatic external fun usersNew(): Long
    @JvmStatic external fun usersFree(handle: Long)
    @JvmStatic external fun usersRefresh(handle: Long)
    @JvmStatic external fun userCount(handle: Long): Long
    @JvmStatic external fun userId(handle: Long, index: Long): String?
    @JvmStatic external fun userGroupId(handle: Long, index: Long): String?
    @JvmStatic external fun userName(handle: Long, index: Long): String?

    // ---- groups ----
    @JvmStatic external fun groupsNew(): Long
    @JvmStatic external fun groupsFree(handle: Long)
    @JvmStatic external fun groupsRefresh(handle: Long)
    @JvmStatic external fun groupCount(handle: Long): Long
    @JvmStatic external fun groupId(handle: Long, index: Long): String?
    @JvmStatic external fun groupName(handle: Long, index: Long): String?
    @JvmStatic external fun userGroupsCount(handle: Long, userIndex: Long): Long
    @JvmStatic external fun userGroupAt(handle: Long, userIndex: Long, groupIndex: Long): String?

    // ---- motherboard ----
    @JvmStatic external fun motherboardName(): String?
    @JvmStatic external fun motherboardVendorName(): String?
    @JvmStatic external fun motherboardVersion(): String?
    @JvmStatic external fun motherboardSerialNumber(): String?
    @JvmStatic external fun motherboardAssetTag(): String?

    // ---- product ----
    @JvmStatic external fun productName(): String?
    @JvmStatic external fun productFamily(): String?
    @JvmStatic external fun productSerialNumber(): String?
    @JvmStatic external fun productStockKeepingUnit(): String?
    @JvmStatic external fun productUuid(): String?
    @JvmStatic external fun productVersion(): String?
    @JvmStatic external fun productVendorName(): String?

    // ---- system extra ----
    @JvmStatic external fun systemKernelLongVersion(): String?
    @JvmStatic external fun systemDistributionIdLikeCount(): Long
    @JvmStatic external fun systemDistributionIdLikeAt(index: Long): String?
    @JvmStatic external fun systemOpenFilesLimit(): Long
    @JvmStatic external fun systemCgroupLimits(): LongArray?
    @JvmStatic external fun isSupportedSystem(): Boolean
    @JvmStatic external fun minimumCpuUpdateIntervalMs(): Long

    // ---- process extra ----
    @JvmStatic external fun processEnviron(handle: Long, pid: Long): String?
    @JvmStatic external fun processRoot(handle: Long, pid: Long): String?
    @JvmStatic external fun processAccumulatedCpuTime(handle: Long, pid: Long): Long
    @JvmStatic external fun processDiskUsage(handle: Long, pid: Long): LongArray?
    @JvmStatic external fun processEffectiveUserId(handle: Long, pid: Long): String?
    @JvmStatic external fun processEffectiveGroupId(handle: Long, pid: Long): String?
    @JvmStatic external fun processSessionId(handle: Long, pid: Long): Long
    @JvmStatic external fun processExists(handle: Long, pid: Long): Boolean
    @JvmStatic external fun processOpenFiles(handle: Long, pid: Long): Long
    @JvmStatic external fun processOpenFilesLimit(handle: Long, pid: Long): Long
    @JvmStatic external fun processCgroupLimits(handle: Long, pid: Long): LongArray?
    @JvmStatic external fun processTasksCount(handle: Long, pid: Long): Long
    @JvmStatic external fun processTasksPidAt(handle: Long, pid: Long, index: Long): Long
    @JvmStatic external fun processThreadKind(handle: Long, pid: Long): Int
    @JvmStatic external fun processKill(handle: Long, pid: Long): Boolean
    @JvmStatic external fun processKillWith(handle: Long, pid: Long, signal: Int): Int

    // ---- disk extra ----
    @JvmStatic external fun diskUsage(handle: Long, index: Long): LongArray?

    // ---- network extra ----
    @JvmStatic external fun networkOperationalState(handle: Long, index: Long): Int
}
