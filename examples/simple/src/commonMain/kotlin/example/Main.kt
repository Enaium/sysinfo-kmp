package example

import cn.enaium.sysinfo.Component
import cn.enaium.sysinfo.Components
import cn.enaium.sysinfo.Cpu
import cn.enaium.sysinfo.Disk
import cn.enaium.sysinfo.DiskKind
import cn.enaium.sysinfo.Disks
import cn.enaium.sysinfo.DiskUsage
import cn.enaium.sysinfo.GroupInfo
import cn.enaium.sysinfo.Groups
import cn.enaium.sysinfo.InterfaceOperationalState
import cn.enaium.sysinfo.LoadAvg
import cn.enaium.sysinfo.Motherboard
import cn.enaium.sysinfo.NetworkInterface
import cn.enaium.sysinfo.Networks
import cn.enaium.sysinfo.Process
import cn.enaium.sysinfo.ProcessStatus
import cn.enaium.sysinfo.Product
import cn.enaium.sysinfo.Signal
import cn.enaium.sysinfo.System as SysInfoSystem
import cn.enaium.sysinfo.ThreadKind
import cn.enaium.sysinfo.UserInfo
import cn.enaium.sysinfo.Users

/**
 * Exercises every public symbol of cn.enaium.sysinfo.
 *
 * The only intentionally non-exercised paths are real signal delivery
 * (System.kill / System.killWith against a live process); they are called
 * with a PID that cannot exist so the native layer reports failure instead
 * of terminating anything.
 */
fun main() {
    banner("sysinfo-kmp example — full API coverage")

    // =====================================================================
    // Enums (reference tables)
    // =====================================================================
    section("Enum reference tables")
    println("  ProcessStatus (${ProcessStatus.entries.size}):             ${ProcessStatus.entries}")
    println("  DiskKind (${DiskKind.entries.size}):                       ${DiskKind.entries}")
    println("  InterfaceOperationalState (${InterfaceOperationalState.entries.size}): ${InterfaceOperationalState.entries}")
    println("  ThreadKind (${ThreadKind.entries.size}):                   ${ThreadKind.entries}")
    println("  Signal (${Signal.entries.size}):                           ${Signal.entries}")

    // =====================================================================
    // Static system information (no handle required)
    // =====================================================================
    section("System companion object")
    val name = SysInfoSystem.name()
    val kernelVersion = SysInfoSystem.kernelVersion()
    val osVersion = SysInfoSystem.osVersion()
    val longOsVersion = SysInfoSystem.longOsVersion()
    val kernelLongVersion = SysInfoSystem.kernelLongVersion()
    val distributionId = SysInfoSystem.distributionId()
    val distributionIdLike = SysInfoSystem.distributionIdLike()
    val hostName = SysInfoSystem.hostName()
    val cpuArch = SysInfoSystem.cpuArch()
    val uptime = SysInfoSystem.uptime()
    val bootTime = SysInfoSystem.bootTime()
    val loadAvg: LoadAvg? = SysInfoSystem.loadAverage()
    val physicalCores = SysInfoSystem.physicalCoreCount()
    val openFilesLimit = SysInfoSystem.openFilesLimit()
    val cgroupLimits = SysInfoSystem.cgroupLimits()
    val supported = SysInfoSystem.isSupportedSystem()
    val minCpuUpdateMs = SysInfoSystem.minimumCpuUpdateIntervalMs()

    println("  name()                 = $name")
    println("  kernelVersion()        = $kernelVersion")
    println("  kernelLongVersion()    = $kernelLongVersion")
    println("  osVersion()            = $osVersion")
    println("  longOsVersion()        = $longOsVersion")
    println("  distributionId()       = $distributionId")
    println("  distributionIdLike()   = $distributionIdLike")
    println("  hostName()             = $hostName")
    println("  cpuArch()              = $cpuArch")
    println("  uptime()               = $uptime s")
    println("  bootTime()             = $bootTime (unix seconds)")
    loadAvg?.let { la ->
        println("  loadAverage()          = one=${la.one} five=${la.five} fifteen=${la.fifteen}")
    } ?: println("  loadAverage()          = null (unsupported)")
    println("  physicalCoreCount()    = $physicalCores")
    println("  openFilesLimit()       = $openFilesLimit")
    cgroupLimits?.let { cl ->
        println("  cgroupLimits()         = total=${cl.totalMemory} freeMem=${cl.freeMemory} freeSwap=${cl.freeSwap} rss=${cl.rss}")
    } ?: println("  cgroupLimits()         = null (not running in a cgroup)")
    println("  isSupportedSystem()    = $supported")
    println("  minCpuUpdateIntervalMs = $minCpuUpdateMs")

    // =====================================================================
    // Motherboard
    // =====================================================================
    section("Motherboard.info()")
    val mb = Motherboard.info()
    mb?.let { m ->
        println("  name         = ${m.name}")
        println("  vendorName   = ${m.vendorName}")
        println("  version      = ${m.version}")
        println("  serialNumber = ${m.serialNumber}")
        println("  assetTag     = ${m.assetTag}")
    } ?: println("  (unavailable on this platform)")

    // =====================================================================
    // Product
    // =====================================================================
    section("Product.info()")
    val product = Product.info()
    product?.let { p ->
        println("  name               = ${p.name}")
        println("  family             = ${p.family}")
        println("  serialNumber       = ${p.serialNumber}")
        println("  stockKeepingUnit   = ${p.stockKeepingUnit}")
        println("  uuid               = ${p.uuid}")
        println("  version            = ${p.version}")
        println("  vendorName         = ${p.vendorName}")
    } ?: println("  (unavailable on this platform)")

    // =====================================================================
    // System instance: memory + CPU
    // =====================================================================
    banner("System instance")

    SysInfoSystem(newAll = true).use { sys ->
        section("Refresh")
        sys.refreshAll()
        sys.refreshMemory()
        sys.refreshCpu()
        val refreshedCount = sys.refreshProcesses()
        println("  refreshAll() / refreshMemory() / refreshCpu()")
        println("  refreshProcesses() = $refreshedCount live processes")

        section("Memory")
        println("  totalMemory     = ${sys.totalMemory} B")
        println("  freeMemory      = ${sys.freeMemory} B")
        println("  availableMemory = ${sys.availableMemory} B")
        println("  usedMemory      = ${sys.usedMemory} B")
        println("  totalSwap       = ${sys.totalSwap} B")
        println("  freeSwap        = ${sys.freeSwap} B")
        println("  usedSwap        = ${sys.usedSwap} B")

        section("CPU")
        println("  globalCpuUsage  = ${sys.globalCpuUsage}%")
        println("  cpus().size     = ${sys.cpus.size}")
        printCpus(sys.cpus)

        section("Process table (${sys.processCount} entries)")
        val procs = sys.processes()
        println("  processes().size      = ${procs.size}")
        println("  processCount property = ${sys.processCount}")

        // Single-PID lookup round trip.
        val first = procs.firstOrNull()
        if (first != null) {
            val lookedUp = sys.process(first.pid)
            println("  process(${first.pid}) round trip -> pid=${lookedUp?.pid} (matches=${lookedUp?.pid == first.pid})")
        }

        // Show the most interesting processes fully.
        val interesting = procs.sortedByDescending { it.memoryBytes }.take(2)
        for (p in interesting) {
            printProcess(p)
        }

        section("System.kill / System.killWith (safe, no live process touched)")
        // A PID that cannot exist; the native layer simply reports failure.
        val ghostPid = 0x40000000L
        println("  kill($ghostPid)                    = ${sys.kill(ghostPid)}   (false: no such process)")
        val killResult = sys.killWith(ghostPid, Signal.Continue)
        println("  killWith($ghostPid, Continue)  = $killResult   (-1: no such process, 0: failed, 1: delivered)")
    }

    // =====================================================================
    // Disks
    // =====================================================================
    Disks().use { disks ->
        disks.refresh()
        section("Disks (${disks.list.size})")
        for (d in disks.list) {
            printDisk(d)
        }
    }

    // =====================================================================
    // Networks
    // =====================================================================
    Networks().use { nets ->
        nets.refresh()
        section("Network interfaces (${nets.list.size})")
        for ((i, n) in nets.list.withIndex()) {
            printNetwork(i, n)
        }
    }

    // =====================================================================
    // Components (temperature sensors etc.)
    // =====================================================================
    Components().use { comps ->
        comps.refresh()
        section("Components (${comps.list.size})")
        for (c in comps.list) {
            printComponent(c)
        }
        if (comps.list.isEmpty()) println("  (none reported on this host)")
    }

    // =====================================================================
    // Users (with per-user group membership)
    // =====================================================================
    Users().use { users ->
        users.refresh()
        val realAccounts = users.list.filter { !it.name.startsWith("_") }
        section("Users (real accounts: ${realAccounts.size} of ${users.list.size})")
        for (u in realAccounts) {
            printUser(u)
        }
    }

    // =====================================================================
    // Groups
    // =====================================================================
    Groups().use { groups ->
        groups.refresh()
        section("Groups (${groups.list.size})")
        for (g in groups.list.take(8)) {
            println("  ${g.name} | id=${g.id}")
        }
        if (groups.list.size > 8) println("  ... and ${groups.list.size - 8} more")
    }

    banner("done — every public API exercised")
}

// ===========================================================================
// Pretty printers — one function per data class so every field appears.
// ===========================================================================

private fun banner(text: String) {
    println()
    println("=== $text ===")
}

private fun section(title: String) {
    println()
    println("-- $title --")
}

private fun printCpus(cpus: List<Cpu>) {
    for ((i, cpu) in cpus.withIndex()) {
        println(
            "  #$i name=${cpu.name} vendor=${cpu.vendorId} brand=${cpu.brand} " +
                "freq=${cpu.frequencyMHz}MHz usage=${cpu.usage}%",
        )
    }
}

private fun printProcess(p: Process) {
    println("  [${p.pid}] ${p.name}")
    println("    parentPid=${p.parentPid} status=${p.status} session=${p.sessionId} exists=${p.exists}")
    println("    mem=${p.memoryBytes}B vmem=${p.virtualMemoryBytes}B cpu=${p.cpuUsage}% accumulatedCpu=${p.accumulatedCpuTime}ms")
    println("    start=${p.startTimeSeconds}s run=${p.runTimeSeconds}s")
    println("    user=${p.userId} euser=${p.effectiveUserId} group=${p.groupId} egroup=${p.effectiveGroupId}")
    println("    openFiles=${p.openFiles}/${p.openFilesLimit} tasks=${p.tasks.size} threadKind=${p.threadKind}")
    println("    diskUsage: read=${p.diskUsage.readBytes}/${p.diskUsage.totalReadBytes}B written=${p.diskUsage.writtenBytes}/${p.diskUsage.totalWrittenBytes}B")
    p.cgroupLimits?.let { cl ->
        println("    cgroup: total=${cl.totalMemory} freeMem=${cl.freeMemory} freeSwap=${cl.freeSwap} rss=${cl.rss}")
    }
    println("    exe=${p.exe}")
    println("    cwd=${p.cwd}")
    println("    root=${p.root}")
    println("    cmd(${p.cmd.size})=${p.cmd.joinToString(" ").take(160)}")
    println("    environ(${p.environ.size})=${p.environ.take(3).joinToString("; ").take(160)}")
}

private fun printDisk(d: Disk) {
    val u: DiskUsage = d.usage
    println(
        "  ${d.name} | mount=${d.mountPoint} fs=${d.fileSystem} total=${d.totalSpaceBytes}B " +
            "avail=${d.availableSpaceBytes}B kind=${d.kind} removable=${d.removable} readOnly=${d.readOnly}",
    )
    println(
        "    usage: read=${u.readBytes}/${u.totalReadBytes}B written=${u.writtenBytes}/${u.totalWrittenBytes}B",
    )
}

private fun printNetwork(index: Int, n: NetworkInterface) {
    println(
        "  #$index ${n.name} | mac=${n.macAddress} mtu=${n.mtuBytes}B state=${n.operationalState}",
    )
    println(
        "    bytes : rx=${n.receivedBytes}/${n.totalReceivedBytes} tx=${n.transmittedBytes}/${n.totalTransmittedBytes}",
    )
    println(
        "    pkts  : rx=${n.packetsReceived}/${n.totalPacketsReceived} tx=${n.packetsTransmitted}/${n.totalPacketsTransmitted}",
    )
    println(
        "    errors: rx=${n.errorsOnReceived}/${n.totalErrorsOnReceived} tx=${n.errorsOnTransmitted}/${n.totalErrorsOnTransmitted}",
    )
    println("    ips   =${n.ipAddresses}")
}

private fun printComponent(c: Component) {
    println("  ${c.label} | id=${c.id} temp=${c.temperatureCelsius}°C max=${c.maxCelsius}°C critical=${c.criticalCelsius}°C")
}

private fun printUser(u: UserInfo) {
    val groups: List<GroupInfo> = u.groups
    println("  ${u.name} | id=${u.id} gid=${u.groupId} groups(${groups.size})=${groups.map { "${it.id}:${it.name}" }}")
}
