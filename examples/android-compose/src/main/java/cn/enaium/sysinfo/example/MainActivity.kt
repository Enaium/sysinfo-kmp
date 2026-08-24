package cn.enaium.sysinfo.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.enaium.sysinfo.Components
import cn.enaium.sysinfo.Disks
import cn.enaium.sysinfo.Groups
import cn.enaium.sysinfo.Networks
import cn.enaium.sysinfo.System as SysInfoSystem
import cn.enaium.sysinfo.Users

private data class Snapshot(
    val host: List<Pair<String, String>>,
    val motherboard: String,
    val product: String,
    val memory: List<Pair<String, String>>,
    val cpus: List<String>,
    val globalCpuUsage: Float,
    val topProcesses: List<String>,
    val disks: List<String>,
    val networks: List<String>,
    val components: List<String>,
    val users: List<String>,
    val groups: Int,
)

/**
 * Android (ART) use case: the JNI shared library ships inside the AAR's
 * jniLibs and is loaded through System.loadLibrary by NativeLoader.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SysInfoApp()
            }
        }
    }
}

@Composable
private fun SysInfoApp() {
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                try { snapshot = takeSnapshot() ; error = null }
                catch (t: Throwable) { error = t.toString() }
            }) { Text("Refresh") }
        }
        error?.let { err ->
            Text(text = "error: $err", color = MaterialTheme.colorScheme.error,
                 modifier = Modifier.padding(12.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            snapshot?.let { s ->
                item { Section("Host") { s.host.forEach { (k, v) -> KeyValue(k, v) } } }
                item { Section("Motherboard") { Text(s.motherboard) } }
                item { Section("Product") { Text(s.product) } }
                item { Section("Memory") { s.memory.forEach { (k, v) -> KeyValue(k, v) } } }
                item {
                    Section("CPUs (${s.cpus.size}, global ${s.globalCpuUsage}%)") {
                        s.cpus.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item { Section("Top processes") { s.topProcesses.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                item { Section("Disks (${s.disks.size})") { s.disks.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                item { Section("Networks (${s.networks.size})") { s.networks.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                item { Section("Components (${s.components.size})") { s.components.take(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                item { Section("Users (${s.users.size})") { s.users.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
                item { Section("Groups (${s.groups})") { Text("see Groups API for the full list") } }
            } ?: item {
                Text("Tap Refresh to collect system info.", Modifier.padding(12.dp))
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }
    }
}

private fun takeSnapshot(): Snapshot {
    SysInfoSystem().use { sys ->
        sys.refreshAll()
        sys.refreshCpu()

        val host = buildList {
            add("name" to (SysInfoSystem.name() ?: "?"))
            add("longOsVersion" to (SysInfoSystem.longOsVersion() ?: "?"))
            add("kernelVersion" to (SysInfoSystem.kernelVersion() ?: "?"))
            add("hostName" to (SysInfoSystem.hostName() ?: "?"))
            add("cpuArch" to SysInfoSystem.cpuArch())
            add("uptime" to "${SysInfoSystem.uptime()}s")
            add("physicalCores" to "${SysInfoSystem.physicalCoreCount()}")
            add("supported" to "${SysInfoSystem.isSupportedSystem()}")
        }

        val mb = cn.enaium.sysinfo.Motherboard.info()
        val pd = cn.enaium.sysinfo.Product.info()

        val memory = listOf(
            "total" to "${sys.totalMemory} B",
            "used" to "${sys.usedMemory} B",
            "available" to "${sys.availableMemory} B",
            "swap total/used" to "${sys.totalSwap} / ${sys.usedSwap} B",
        )

        return Snapshot(
            host = host,
            motherboard = listOfNotNull(mb?.name, mb?.vendorName, mb?.serialNumber).joinToString(" | ").ifEmpty { "n/a" },
            product = listOfNotNull(pd?.name, pd?.family, pd?.uuid).joinToString(" | ").ifEmpty { "n/a" },
            memory = memory,
            cpus = sys.cpus.map { "#${it.name} ${it.brand} ${it.frequencyMHz}MHz ${it.usage}%" },
            globalCpuUsage = sys.globalCpuUsage,
            topProcesses = sys.processes().sortedByDescending { it.memoryBytes }.take(5)
                .map { "[${it.pid}] ${it.name} mem=${it.memoryBytes}B cpu=${it.cpuUsage}%" },
            disks = Disks().use { d ->
                d.refresh(); d.list.map { "${it.mountPoint} ${it.kind} free=${it.availableSpaceBytes}B" }
            },
            networks = Networks().use { n ->
                n.refresh(); n.list.map { "${it.name} mac=${it.macAddress} state=${it.operationalState}" }
            },
            components = Components().use { c ->
                c.refresh(); c.list.map { "${it.label}: ${it.temperatureCelsius}°C" }
            },
            users = Users().use { u ->
                u.refresh(); u.list.filter { !it.name.startsWith("_") }
                    .map { "${it.name} id=${it.id} groups=${it.groups.size}" }
            },
            groups = Groups().use { g -> g.refresh(); g.list.size },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        content()
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
