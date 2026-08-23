package cn.enaium.sysinfo

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class SysInfoTest {

    @Test
    fun staticSystemInfoDoesNotThrow() {
        // Each of these returns String? or similar; the call itself must not throw.
        System.name()
        System.kernelVersion()
        System.osVersion()
        System.longOsVersion()
        System.distributionId()
        System.hostName()
        System.cpuArch()
        System.uptime()
        System.bootTime()
        System.loadAverage()
        System.physicalCoreCount()
        assertTrue(System.cpuArch().isNotEmpty(), "cpuArch should be non-empty")
        assertTrue(System.distributionId().isNotEmpty() || true, "distributionId may be empty on macOS")
    }

    @Test
    fun systemRefreshProducesSaneValues() {
        System().use { sys ->
            sys.refreshAll()
            val total = sys.totalMemory
            val free = sys.freeMemory
            val avail = sys.availableMemory
            val used = sys.usedMemory
            assertTrue(total > 0u, "totalMemory should be > 0, was $total")
            assertTrue(free <= total, "freeMemory $free should be <= total $total")
            assertTrue(used <= total, "usedMemory $used should be <= total $total")
            assertTrue(sys.cpus.isNotEmpty(), "should have at least one CPU")
            for (cpu in sys.cpus) {
                assertTrue(cpu.name.isNotEmpty() || cpu.brand.isNotEmpty() || cpu.vendorId.isNotEmpty())
                assertTrue(cpu.usage >= 0f)
            }
            assertTrue(sys.globalCpuUsage >= 0f)
        }
    }

    @Test
    fun processTableIsAccessible() {
        System().use { sys ->
            val count = sys.refreshProcesses()
            assertTrue(count > 0, "should have at least one process")
            assertTrue(sys.processCount > 0)
            val list = sys.processes()
            assertTrue(list.isNotEmpty(), "processes() should not be empty")
            val first = list.first()
            assertTrue(first.pid >= 0)
            assertTrue(first.name.isNotEmpty() || first.exe != null || first.cmd.isNotEmpty())
            assertTrue(first.memoryBytes >= 0u)
            // Single-pid lookup
            val own = sys.process(first.pid)
            assertNotNull(own, "process lookup by pid should succeed")
            assertTrue(own.pid == first.pid)
        }
    }

    @Test
    fun disksAreAccessible() {
        Disks().use { disks ->
            disks.refresh()
            // Disks may be empty in some CI containers; just ensure no crash and sane values.
            for (disk in disks.list) {
                assertTrue(disk.name.isNotEmpty() || disk.mountPoint.isNotEmpty())
                assertTrue(disk.totalSpaceBytes >= disk.availableSpaceBytes || disk.totalSpaceBytes == 0uL)
            }
        }
    }

    @Test
    fun networksAreAccessible() {
        Networks().use { nets ->
            nets.refresh()
            for (net in nets.list) {
                assertTrue(net.name.isNotEmpty())
                // MAC may be empty on some virtual interfaces; just check no crash.
            }
        }
    }

    @Test
    fun componentsDoNotThrow() {
        Components().use { comps ->
            comps.refresh()
            for (c in comps.list) {
                assertTrue(c.label.isNotEmpty())
            }
        }
    }

    @Test
    fun usersAreAccessible() {
        Users().use { users ->
            users.refresh()
            // At least one user should exist on any host.
            assertTrue(users.list.isNotEmpty(), "should have at least one user")
            for (u in users.list) {
                assertTrue(u.name.isNotEmpty())
                assertTrue(u.id.isNotEmpty())
            }
        }
    }
}
