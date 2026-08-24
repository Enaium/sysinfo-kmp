# sysinfo-kmp

Kotlin Multiplatform bindings for the Rust [sysinfo](https://github.com/GuillaumeGomez/sysinfo) crate (0.39.x), with a snapshot-style common API backed by two implementations:

- **JVM**: the Rust shim crate `rust/` is compiled by cargo into a JNI shared library (`libsyskmp`) that is shipped as per-OS/arch `sysinfo-kmp-jni-jvm-*` artifacts. `NativeLoader` extracts the matching binary at runtime, so consumers need nothing beyond the normal dependencies.
- **Native (Kotlin/Native)**: the same shim is compiled by cargo into a static library that is **embedded into the published klib**, so consumers get a fully self-contained binary (no dynamic syskmp dependency). This includes the Android native targets (`androidNative*`), which are pure-Rust archives and therefore cross-compile on any host without the NDK.

## Supported platforms

| Platform | Targets                                             | Implementation                              |
|----------|-----------------------------------------------------|---------------------------------------------|
| JVM      | `jvm` (Linux/macOS/Windows x64 & arm64)             | JNI shared library built by cargo           |
| macOS    | `macosArm64`, `macosX64`                            | cinterop + embedded static libsyskmp        |
| Linux    | `linuxX64`, `linuxArm64`                            | cinterop + embedded static libsyskmp        |
| Windows  | `mingwX64`                                          | cinterop + embedded static libsyskmp        |
| Android  | `androidNativeArm64`, `androidNativeArm32`, `androidNativeX64`, `androidNativeX86` | cinterop + embedded static libsyskmp |

## API coverage

Everything the upstream 0.39.6 crate exposes except `Process::kill_and_wait` / `kill_with_and_wait` / `wait` (they return Rust `Result`/`ExitStatus` types that do not map to a C ABI):

- **System** — memory/swap totals, global + per-CPU usage/frequency/vendor/brand, process table with full details (cmd, environ, exe/cwd/root, status, effective user/group, session, accumulated CPU time, disk I/O, open files, tasks/threads, cgroup limits), host/kernel/os versions, distribution ids, load average, boot time/uptime, physical cores, open-files limit
- **Disks** — mount points, filesystems, capacity, kind, read-only/removable, per-disk I/O usage
- **Networks** — per-interface byte/packet/error counters (delta + total), MAC, MTU, IP networks, operational state
- **Components** — temperature sensors with max/critical thresholds
- **Users / Groups** — accounts with group membership; standalone group list
- **Motherboard / Product** — vendor/model/serial/UUID/SKU information

All values are returned as Kotlin snapshots (data classes); no native pointers escape the bindings.

## Usage

`build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("cn.enaium:sysinfo-kmp:1.0.0")
        }
    }
}
```

```kotlin
import cn.enaium.sysinfo.*

fun main() {
    // Static host info (no handle required).
    println("${System.longOsVersion()} (${System.cpuArch()}, ${System.physicalCoreCount()} cores)")

    System().use { sys ->
        sys.refreshAll()
        println("memory used ${sys.usedMemory} / ${sys.totalMemory} B")

        for (cpu in sys.cpus) {
            println("${cpu.name}: ${cpu.usage}% @ ${cpu.frequencyMHz} MHz")
        }

        for (p in sys.processes().sortedByDescending { it.memoryBytes }.take(5)) {
            println("[${p.pid}] ${p.name} mem=${p.memoryBytes} cpu=${p.cpuUsage}%")
        }
    }

    Disks().use { disks ->
        for (d in disks.list) {
            println("${d.mountPoint} free=${d.availableSpaceBytes}B / ${d.totalSpaceBytes}B")
        }
    }
}
```

### Platform notes
- **JVM native library**: the matching `sysinfo-kmp-jni-jvm-{os}-{arch}` artifact is a transitive runtime dependency of `sysinfo-kmp`; `NativeLoader` extracts the bundled binary from the classpath and `System.load()`s it, so no `java.library.path` setup is needed.
- **Kotlin version compatibility**: the published klibs are built with Kotlin 2.4.10. Consuming them with a different Kotlin/Native version produces an `IrLinkageError` at the first call. Keep the consumer's Kotlin version in sync.
- **CPU usage**: like upstream, usage is computed from the delta between two refreshes; wait at least `System.minimumCpuUpdateIntervalMs()` between refreshes for meaningful numbers.
- **macOS linking**: the embedded Rust code uses the objc2 crates; the required frameworks (`CoreFoundation`, `IOKit`, `OpenDirectory`) and `-lobjc` are recorded in the klib's `linkerOpts` and applied automatically when the consumer's binary links.
- **Windows linking**: the mingw import libraries (`ws2_32`, `iphlpapi`, `advapi32`, `ole32`, `oleaut32`, `ntdll`, `netapi32`, `uuid`, `bcrypt`) come from the Kotlin/Native MinGW sysroot — nothing extra to install.
- **Android**: pure-Rust static archives mean `androidNative*` targets cross-compile on any host with just `rustup target add`; the consumer's Kotlin/Native Android toolchain provides bionic at link time.

## Examples

Two standalone examples live under `examples/`:

- **`examples/simple`** — console demo (JVM + every native target) that
  prints each API section: host info, motherboard/product, memory, CPUs,
  processes with all fields, disks, networks, components, users with groups.
- **`examples/android-compose`** — Jetpack Compose application for Android
  (ART) that renders the same sections on-device. It is an isolated Gradle
  build included as a composite build (`includeBuild`), using AGP 9's
  built-in Kotlin + Compose; its plugin classpath stays separate from the
  root build's Kotlin Multiplatform classpath.

```bash
# Publish the library to the local Maven repository first (each platform
# builds what it can: macOS -> metadata/jvm/macos klibs/darwin JNI,
# Linux -> linux/mingw klibs/linux JNI, Windows -> windows JNI).
./gradlew :sysinfo-kmp:publishToMavenLocal :jni-jvm-<os>-<arch>:publishToMavenLocal

# simple — JVM
./gradlew :examples:simple:jvmRun

# simple — Native
./gradlew :examples:simple:runDebugExecutableMacosArm64
```

### Android (ART / Compose)

The sysinfo shared library ships inside the published AAR's `jni/<abi>`
entries, so nothing beyond the dependency is required:

```bash
# Publish the Android AAR locally once (any host with the NDK; the cargo
# cdylibs are linked through the NDK toolchain automatically).
./gradlew :sysinfo-kmp:publishAndroidPublicationToMavenLocal

# Build the APK (composite build — run through -p so Gradle picks up its
# own settings.gradle.kts)
./gradlew -p examples/android-compose assembleDebug
adb install -r examples/android-compose/build/outputs/apk/debug/*.apk
```

## Development

The Rust shim lives in `rust/` (`cargo build --release` produces both the static library and the cdylib). Gradle invokes cargo automatically; the only prerequisite is a rustup toolchain with the desired targets installed:

```bash
rustup target add x86_64-apple-darwin          # macosX64 / darwin-x86_64
rustup target add aarch64-unknown-linux-gnu    # linuxArm64 / linux-aarch64 (cdylib also needs gcc-aarch64-linux-gnu)
rustup target add x86_64-pc-windows-gnu        # mingwX64 / windows-x86_64
rustup target add aarch64-linux-android armv7-linux-androideabi \
                 x86_64-linux-android i686-linux-android  # androidNative*

# Tests on the host platform
./gradlew :sysinfo-kmp:jvmTest :sysinfo-kmp:macosArm64Test   # macOS
./gradlew :sysinfo-kmp:jvmTest :sysinfo-kmp:linuxX64Test     # Linux
```

Targets whose rust triple is not installed still compile their bindings (the klib publishes without the embedded library), so partial toolchains never break the build.

## GitHub Actions

- `.github/workflows/test.yml` — push/PR/manual: each runner first publishes everything it can build to Maven Local (signed, mirroring the release path), then runs JVM/native tests and the example. macOS covers metadata/JVM/Apple klibs/darwin JNI; Linux covers linux/mingw klibs/linux JNI (aarch64 linked with `gcc-aarch64-linux-gnu`); Windows builds the `windows-x86_64` JNI artifact; Android builds the four `androidNative` klibs.
- `.github/workflows/publish.yml` — manual dispatch that publishes every publication from the runner that builds it (same split as above) to Maven Central.

Required secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` (base64 GPG keyring), `SIGNING_KEY_ID`, `SIGNING_PASSWORD`.

## License

MIT. The bound [sysinfo](https://github.com/GuillaumeGomez/sysinfo) crate is MIT licensed.
