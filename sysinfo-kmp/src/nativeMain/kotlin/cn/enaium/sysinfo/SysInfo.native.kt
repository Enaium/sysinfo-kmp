@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.sysinfo

import syskmp.*
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf

private fun CPointer<ByteVar>?.readAndFree(): String? {
    this ?: return null
    val s = toKString()
    syskmp_free_string(this)
    return s
}

actual class NativeSystem actual constructor(newAll: Boolean) : AutoCloseable {
    private val handle: syskmp_system_t =
        syskmp_system_new(if (newAll) 1 else 0) ?: throw SysInfoException("Failed to create System")

    actual fun refreshAll() = syskmp_system_refresh_all(handle)
    actual fun refreshMemory() = syskmp_system_refresh_memory(handle)
    actual fun refreshCpu() = syskmp_system_refresh_cpu(handle)
    actual fun refreshProcesses(): Long = syskmp_system_refresh_processes(handle)
    actual fun globalCpuUsage(): Float = syskmp_global_cpu_usage(handle)
    actual fun cpuCount(): Int = syskmp_cpu_count(handle).toInt()
    actual fun cpuName(i: Int): String? = syskmp_cpu_name(handle, i.toLong()).readAndFree()
    actual fun cpuVendorId(i: Int): String? = syskmp_cpu_vendor_id(handle, i.toLong()).readAndFree()
    actual fun cpuBrand(i: Int): String? = syskmp_cpu_brand(handle, i.toLong()).readAndFree()
    actual fun cpuFrequency(i: Int): ULong = syskmp_cpu_frequency(handle, i.toLong())
    actual fun cpuUsage(i: Int): Float = syskmp_cpu_usage(handle, i.toLong())
    actual fun totalMemory(): ULong = syskmp_total_memory(handle)
    actual fun freeMemory(): ULong = syskmp_free_memory(handle)
    actual fun availableMemory(): ULong = syskmp_available_memory(handle)
    actual fun usedMemory(): ULong = syskmp_used_memory(handle)
    actual fun totalSwap(): ULong = syskmp_total_swap(handle)
    actual fun freeSwap(): ULong = syskmp_free_swap(handle)
    actual fun usedSwap(): ULong = syskmp_used_swap(handle)
    actual fun processCount(): Int = syskmp_process_count(handle).toInt()
    actual fun pidAt(i: Int): Long = syskmp_pid_at(handle, i.toLong())
    actual fun processCpuUsage(pid: Long): Float = syskmp_process_cpu_usage(handle, pid)
    actual fun processMemory(pid: Long): ULong = syskmp_process_memory(handle, pid)
    actual fun processVirtualMemory(pid: Long): ULong = syskmp_process_virtual_memory(handle, pid)
    actual fun processParent(pid: Long): Long = syskmp_process_parent(handle, pid)
    actual fun processStatusCode(pid: Long): Int = syskmp_process_status(handle, pid)
    actual fun processStartTime(pid: Long): ULong = syskmp_process_start_time(handle, pid)
    actual fun processRunTime(pid: Long): ULong = syskmp_process_run_time(handle, pid)
    actual fun processName(pid: Long): String? = syskmp_process_name(handle, pid).readAndFree()
    actual fun processExe(pid: Long): String? = syskmp_process_exe(handle, pid).readAndFree()
    actual fun processCwd(pid: Long): String? = syskmp_process_cwd(handle, pid).readAndFree()
    actual fun processRoot(pid: Long): String? = syskmp_process_root(handle, pid).readAndFree()
    actual fun processCmd(pid: Long): String? = syskmp_process_cmd(handle, pid).readAndFree()
    actual fun processEnviron(pid: Long): String? = syskmp_process_environ(handle, pid).readAndFree()
    actual fun processUserId(pid: Long): String? = syskmp_process_user_id(handle, pid).readAndFree()
    actual fun processGroupId(pid: Long): String? = syskmp_process_group_id(handle, pid).readAndFree()
    actual fun processEffectiveUserId(pid: Long): String? = syskmp_process_effective_user_id(handle, pid).readAndFree()
    actual fun processEffectiveGroupId(pid: Long): String? = syskmp_process_effective_group_id(handle, pid).readAndFree()
    actual fun processSessionId(pid: Long): Long = syskmp_process_session_id(handle, pid)
    actual fun processAccumulatedCpuTime(pid: Long): ULong = syskmp_process_accumulated_cpu_time(handle, pid)
    actual fun processDiskUsage(pid: Long): DiskUsage? {
        val totalWritten = ULongArray(1)
        val written = ULongArray(1)
        val totalRead = ULongArray(1)
        val read = ULongArray(1)
        val ok = totalWritten.usePinned { tw ->
            written.usePinned { w ->
                totalRead.usePinned { tr ->
                    read.usePinned { r ->
                        syskmp_process_disk_usage(handle, pid, tw.addressOf(0), w.addressOf(0), tr.addressOf(0), r.addressOf(0))
                    }
                }
            }
        }
        return if (ok == 0) null else DiskUsage(totalWritten[0], written[0], totalRead[0], read[0])
    }
    actual fun processExists(pid: Long): Boolean = syskmp_process_exists(handle, pid) != 0
    actual fun processOpenFiles(pid: Long): Int? = syskmp_process_open_files(handle, pid).let { if (it < 0) null else it.toInt() }
    actual fun processOpenFilesLimit(pid: Long): Int? = syskmp_process_open_files_limit(handle, pid).let { if (it < 0) null else it.toInt() }
    actual fun processCgroupLimits(pid: Long): CGroupLimits? {
        val total = ULongArray(1)
        val free = ULongArray(1)
        val swap = ULongArray(1)
        val rss = ULongArray(1)
        val ok = total.usePinned { t ->
            free.usePinned { f ->
                swap.usePinned { s ->
                    rss.usePinned { r ->
                        syskmp_process_cgroup_limits(handle, pid, t.addressOf(0), f.addressOf(0), s.addressOf(0), r.addressOf(0))
                    }
                }
            }
        }
        return if (ok == 0) null else CGroupLimits(total[0], free[0], swap[0], rss[0])
    }
    actual fun processTasks(pid: Long): List<Long> = (0 until syskmp_process_tasks_count(handle, pid).toInt()).map { syskmp_process_tasks_pid_at(handle, pid, it.toLong()) }
    actual fun processThreadKind(pid: Long): Int = syskmp_process_thread_kind(handle, pid)
    actual fun processKill(pid: Long): Boolean = syskmp_process_kill(handle, pid) != 0
    actual fun processKillWith(pid: Long, signal: Int): Int = syskmp_process_kill_with(handle, pid, signal)
    actual override fun close() = syskmp_system_free(handle)
}

actual object NativeInfo {
    actual fun name(): String? = syskmp_system_name().readAndFree()
    actual fun kernelVersion(): String? = syskmp_system_kernel_version().readAndFree()
    actual fun osVersion(): String? = syskmp_system_os_version().readAndFree()
    actual fun longOsVersion(): String? = syskmp_system_long_os_version().readAndFree()
    actual fun kernelLongVersion(): String = syskmp_system_kernel_long_version().readAndFree() ?: ""
    actual fun distributionId(): String = syskmp_system_distribution_id().readAndFree() ?: ""
    actual fun distributionIdLike(): List<String> = (0 until syskmp_system_distribution_id_like_count().toInt()).mapNotNull { syskmp_system_distribution_id_like_at(it.toLong()).readAndFree() }
    actual fun hostName(): String? = syskmp_system_host_name().readAndFree()
    actual fun cpuArch(): String = syskmp_system_cpu_arch().readAndFree() ?: ""
    actual fun uptime(): ULong = syskmp_uptime()
    actual fun bootTime(): ULong = syskmp_boot_time()
    actual fun loadAverage(): LoadAvg? {
        val buf = DoubleArray(3)
        val ok = buf.usePinned { pinned ->
            syskmp_load_average(pinned.addressOf(0), pinned.addressOf(1), pinned.addressOf(2))
        }
        return if (ok != 0) LoadAvg(buf[0], buf[1], buf[2]) else null
    }
    actual fun physicalCoreCount(): Int? {
        val c = syskmp_physical_core_count()
        return if (c < 0) null else c.toInt()
    }
    actual fun openFilesLimit(): Int? = syskmp_system_open_files_limit().let { if (it < 0) null else it.toInt() }
    actual fun cgroupLimits(): CGroupLimits? {
        val total = ULongArray(1)
        val free = ULongArray(1)
        val swap = ULongArray(1)
        val rss = ULongArray(1)
        val ok = total.usePinned { t ->
            free.usePinned { f ->
                swap.usePinned { s ->
                    rss.usePinned { r ->
                        syskmp_system_cgroup_limits(t.addressOf(0), f.addressOf(0), s.addressOf(0), r.addressOf(0))
                    }
                }
            }
        }
        return if (ok == 0) null else CGroupLimits(total[0], free[0], swap[0], rss[0])
    }
    actual fun isSupportedSystem(): Boolean = syskmp_is_supported_system() != 0
    actual fun minimumCpuUpdateIntervalMs(): ULong = syskmp_minimum_cpu_update_interval_ms()
}

actual class NativeDisks actual constructor() : AutoCloseable {
    private val handle: syskmp_disks_t =
        syskmp_disks_new() ?: throw SysInfoException("Failed to create Disks")
    actual fun refresh(removeNotListed: Boolean) = syskmp_disks_refresh(handle, if (removeNotListed) 1 else 0)
    actual fun count(): Int = syskmp_disk_count(handle).toInt()
    actual fun name(i: Int): String? = syskmp_disk_name(handle, i.toLong()).readAndFree()
    actual fun mountPoint(i: Int): String? = syskmp_disk_mount_point(handle, i.toLong()).readAndFree()
    actual fun fileSystem(i: Int): String? = syskmp_disk_file_system(handle, i.toLong()).readAndFree()
    actual fun totalSpace(i: Int): ULong = syskmp_disk_total_space(handle, i.toLong())
    actual fun availableSpace(i: Int): ULong = syskmp_disk_available_space(handle, i.toLong())
    actual fun isRemovable(i: Int): Boolean = syskmp_disk_is_removable(handle, i.toLong()) != 0
    actual fun isReadOnly(i: Int): Boolean = syskmp_disk_is_read_only(handle, i.toLong()) != 0
    actual fun kind(i: Int): Int = syskmp_disk_kind(handle, i.toLong())
    actual fun usage(i: Int): DiskUsage? {
        val totalWritten = ULongArray(1)
        val written = ULongArray(1)
        val totalRead = ULongArray(1)
        val read = ULongArray(1)
        val ok = totalWritten.usePinned { tw ->
            written.usePinned { w ->
                totalRead.usePinned { tr ->
                    read.usePinned { r ->
                        syskmp_disk_usage(handle, i.toLong(), tw.addressOf(0), w.addressOf(0), tr.addressOf(0), r.addressOf(0))
                    }
                }
            }
        }
        return if (ok == 0) null else DiskUsage(totalWritten[0], written[0], totalRead[0], read[0])
    }
    actual override fun close() = syskmp_disks_free(handle)
}

actual class NativeNetworks actual constructor() : AutoCloseable {
    private val handle: syskmp_networks_t =
        syskmp_networks_new() ?: throw SysInfoException("Failed to create Networks")
    actual fun refresh(removeNotListed: Boolean) = syskmp_networks_refresh(handle, if (removeNotListed) 1 else 0)
    actual fun count(): Int = syskmp_network_count(handle).toInt()
    actual fun name(i: Int): String? = syskmp_network_name(handle, i.toLong()).readAndFree()
    actual fun received(i: Int): ULong = syskmp_network_received(handle, i.toLong())
    actual fun totalReceived(i: Int): ULong = syskmp_network_total_received(handle, i.toLong())
    actual fun transmitted(i: Int): ULong = syskmp_network_transmitted(handle, i.toLong())
    actual fun totalTransmitted(i: Int): ULong = syskmp_network_total_transmitted(handle, i.toLong())
    actual fun packetsReceived(i: Int): ULong = syskmp_network_packets_received(handle, i.toLong())
    actual fun totalPacketsReceived(i: Int): ULong = syskmp_network_total_packets_received(handle, i.toLong())
    actual fun packetsTransmitted(i: Int): ULong = syskmp_network_packets_transmitted(handle, i.toLong())
    actual fun totalPacketsTransmitted(i: Int): ULong = syskmp_network_total_packets_transmitted(handle, i.toLong())
    actual fun errorsOnReceived(i: Int): ULong = syskmp_network_errors_on_received(handle, i.toLong())
    actual fun totalErrorsOnReceived(i: Int): ULong = syskmp_network_total_errors_on_received(handle, i.toLong())
    actual fun errorsOnTransmitted(i: Int): ULong = syskmp_network_errors_on_transmitted(handle, i.toLong())
    actual fun totalErrorsOnTransmitted(i: Int): ULong = syskmp_network_total_errors_on_transmitted(handle, i.toLong())
    actual fun mtu(i: Int): ULong = syskmp_network_mtu(handle, i.toLong())
    actual fun macAddress(i: Int): String? = syskmp_network_mac_address(handle, i.toLong()).readAndFree()
    actual fun ipCount(i: Int): Int = syskmp_network_ip_count(handle, i.toLong()).toInt()
    actual fun ipAt(i: Int, j: Int): String? = syskmp_network_ip_at(handle, i.toLong(), j.toLong()).readAndFree()
    actual fun operationalState(i: Int): Int = syskmp_network_operational_state(handle, i.toLong())
    actual override fun close() = syskmp_networks_free(handle)
}

actual class NativeComponents actual constructor() : AutoCloseable {
    private val handle: syskmp_components_t =
        syskmp_components_new() ?: throw SysInfoException("Failed to create Components")
    actual fun refresh(removeNotListed: Boolean) = syskmp_components_refresh(handle, if (removeNotListed) 1 else 0)
    actual fun count(): Int = syskmp_component_count(handle).toInt()
    actual fun label(i: Int): String? = syskmp_component_label(handle, i.toLong()).readAndFree()
    actual fun id(i: Int): String? = syskmp_component_id(handle, i.toLong()).readAndFree()
    actual fun temperature(i: Int): Float = syskmp_component_temperature(handle, i.toLong())
    actual fun max(i: Int): Float = syskmp_component_max(handle, i.toLong())
    actual fun critical(i: Int): Float = syskmp_component_critical(handle, i.toLong())
    actual override fun close() = syskmp_components_free(handle)
}

actual class NativeUsers actual constructor() : AutoCloseable {
    private val handle: syskmp_users_t =
        syskmp_users_new() ?: throw SysInfoException("Failed to create Users")
    actual fun refresh() = syskmp_users_refresh(handle)
    actual fun count(): Int = syskmp_user_count(handle).toInt()
    actual fun id(i: Int): String? = syskmp_user_id(handle, i.toLong()).readAndFree()
    actual fun groupId(i: Int): String? = syskmp_user_group_id(handle, i.toLong()).readAndFree()
    actual fun name(i: Int): String? = syskmp_user_name(handle, i.toLong()).readAndFree()
    actual fun groupsCount(i: Int): Int = syskmp_user_groups_count(handle, i.toLong()).toInt()
    actual fun groupAt(i: Int, j: Int): String? = syskmp_user_group_at(handle, i.toLong(), j.toLong()).readAndFree()
    actual override fun close() = syskmp_users_free(handle)
}

actual class NativeGroups actual constructor() : AutoCloseable {
    private val handle: syskmp_groups_t =
        syskmp_groups_new() ?: throw SysInfoException("Failed to create Groups")
    actual fun refresh() = syskmp_groups_refresh(handle)
    actual fun count(): Int = syskmp_group_count(handle).toInt()
    actual fun id(i: Int): String? = syskmp_group_id(handle, i.toLong()).readAndFree()
    actual fun name(i: Int): String? = syskmp_group_name(handle, i.toLong()).readAndFree()
    actual override fun close() = syskmp_groups_free(handle)
}

actual object NativeMotherboard {
    actual fun info(): MotherboardInfo? {
        val name = syskmp_motherboard_name().readAndFree()
        val vendor = syskmp_motherboard_vendor_name().readAndFree()
        val version = syskmp_motherboard_version().readAndFree()
        val serial = syskmp_motherboard_serial_number().readAndFree()
        val asset = syskmp_motherboard_asset_tag().readAndFree()
        if (name == null && vendor == null && version == null && serial == null && asset == null) return null
        return MotherboardInfo(name, vendor, version, serial, asset)
    }
}

actual object NativeProduct {
    actual fun info(): ProductInfo? {
        val name = syskmp_product_name().readAndFree()
        val family = syskmp_product_family().readAndFree()
        val serial = syskmp_product_serial_number().readAndFree()
        val sku = syskmp_product_stock_keeping_unit().readAndFree()
        val uuid = syskmp_product_uuid().readAndFree()
        val version = syskmp_product_version().readAndFree()
        val vendor = syskmp_product_vendor_name().readAndFree()
        if (name == null && family == null && serial == null && sku == null && uuid == null && version == null && vendor == null) return null
        return ProductInfo(name, family, serial, sku, uuid, version, vendor)
    }
}
