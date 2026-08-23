//! syskmp: C ABI shim over the sysinfo crate.
//!
//! Every exported function is panic-safe (a Rust panic becomes a default
//! return value instead of unwinding across the FFI boundary). Returned
//! `char*` values are heap-allocated and must be released with
//! [syskmp_free_string]. Handles returned by the `*_new` functions must be
//! released with the matching `*_free` function.

// Every `pub unsafe extern "C" fn` is itself the documented unsafe boundary:
// callers guarantee handle/pointer validity per the prose safety comments,
// and the bodies null-check every pointer before dereferencing.

#![allow(unsafe_op_in_unsafe_fn, clippy::missing_safety_doc)]

mod jni;

use std::ffi::{CString, c_char};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use sysinfo::{
    Components, Disks, Groups, Motherboard, Networks, Pid, ProcessStatus, ProcessesToUpdate,
    Product, System, ThreadKind, Users,
};

// ===========================================================================
// Helpers
// ===========================================================================

/// Runs `f`, converting a Rust panic into `T::default()` so nothing ever
/// unwinds across the C ABI.
fn guard<T: Default>(f: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|_| {
        // Swallow the panic payload; the hook already printed the message.
        T::default()
    })
}

/// Copies `s` into a freshly allocated NUL-terminated string. The caller owns
/// the result and must free it with `syskmp_free_string`.
unsafe fn dup(s: &str) -> *mut c_char {
    // Interior NUL bytes cannot be represented in a C string; truncate there.
    CString::new(s.bytes().take_while(|&b| b != 0).collect::<Vec<u8>>())
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// `System` plus a cached, sorted list of PIDs so Kotlin can iterate the
pub struct SystemHandle {
    system: System,
    pids: Vec<Pid>,
}

impl SystemHandle {
    fn refresh_pids(&mut self) {
        let mut pids: Vec<Pid> = self.system.processes().keys().copied().collect();
        pids.sort_unstable_by_key(|p| usize::from(*p));
        self.pids = pids;
    }
    fn process(&self, pid: i64) -> Option<&sysinfo::Process> {
        self.system.process(Pid::from(pid.max(0) as usize))
    }
}

/// `Networks` plus a cached interface-name list; the upstream data structure
/// is a `HashMap` whose iteration order is unspecified.
pub struct NetworksHandle {
    networks: Networks,
    names: Vec<String>,
}

impl NetworksHandle {
    fn refresh_names(&mut self) {
        self.names = self.networks.list().keys().cloned().collect::<Vec<_>>();
        self.names.sort();
    }

    fn data(&self, index: usize) -> Option<&sysinfo::NetworkData> {
        self.names
            .get(index)
            .and_then(|name| self.networks.list().get(name))
    }
}

const NAN: f32 = f32::NAN;

// ===========================================================================
// Memory management
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_free_string(s: *mut c_char) {
    if !s.is_null() {
        unsafe { drop(CString::from_raw(s)) };
    }
}

// ===========================================================================
// Static system information (no handle required)
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_name() -> *mut c_char {
    guard(System::name).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_kernel_version() -> *mut c_char {
    guard(System::kernel_version).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_os_version() -> *mut c_char {
    guard(System::os_version).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_long_os_version() -> *mut c_char {
    guard(System::long_os_version).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_distribution_id() -> *mut c_char {
    guard(|| unsafe { dup(&System::distribution_id()) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_host_name() -> *mut c_char {
    guard(System::host_name).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_cpu_arch() -> *mut c_char {
    guard(|| unsafe { dup(&System::cpu_arch()) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_uptime() -> u64 {
    guard(System::uptime)
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_boot_time() -> u64 {
    guard(System::boot_time)
}

/// Writes the 1/5/15-minute load averages into non-null out pointers.
/// Returns false when load average is unavailable on this platform.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_load_average(
    one: *mut f64,
    five: *mut f64,
    fifteen: *mut f64,
) -> bool {
    guard(|| {
        let avg = System::load_average();
        if !one.is_null() {
            unsafe { *one = avg.one };
        }
        if !five.is_null() {
            unsafe { *five = avg.five };
        }
        if !fifteen.is_null() {
            unsafe { *fifteen = avg.fifteen };
        }
        true
    })
}

/// Returns the physical core count, or -1 when unknown.
#[unsafe(no_mangle)]
pub extern "C" fn syskmp_physical_core_count() -> i64 {
    guard(|| {
        System::physical_core_count()
            .map(|n| n as i64)
            .unwrap_or(-1)
    })
}

// ===========================================================================
// System handle lifecycle
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_new(new_all: bool) -> *mut c_char {
    guard(move || {
        let system = if new_all {
            System::new_all()
        } else {
            System::new()
        };
        Box::into_raw(Box::new(SystemHandle {
            system,
            pids: Vec::new(),
        })) as *mut c_char
    })
}

/// # Safety
///
/// `handle` must have been returned by [syskmp_system_new] and not freed yet.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_system_free(handle: *mut c_char) {
    if !handle.is_null() {
        drop(Box::from_raw(handle as *mut SystemHandle));
    }
}

unsafe fn system<'a>(handle: *mut c_char) -> Option<&'a mut SystemHandle> {
    (handle as *mut SystemHandle).as_mut()
}

// ===========================================================================
// System refresh
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_system_refresh_all(handle: *mut c_char) {
    guard(|| {
        if let Some(h) = system(handle) {
            h.system.refresh_all();
            h.refresh_pids();
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_system_refresh_memory(handle: *mut c_char) {
    guard(|| {
        if let Some(h) = system(handle) {
            h.system.refresh_memory();
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_system_refresh_cpu(handle: *mut c_char) {
    guard(|| {
        if let Some(h) = system(handle) {
            h.system.refresh_cpu_all();
        }
    });
}

/// Refreshes the process table; returns the number of live processes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_system_refresh_processes(handle: *mut c_char) -> i64 {
    guard(|| {
        system(handle)
            .map(|h| {
                let count = h.system.refresh_processes(ProcessesToUpdate::All, true);
                h.refresh_pids();
                count as i64
            })
            .unwrap_or(0)
    })
}

// ===========================================================================
// System accessors: CPU / memory / swap
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_global_cpu_usage(handle: *mut c_char) -> f32 {
    guard(|| system(handle).map_or(0.0, |h| h.system.global_cpu_usage()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_cpu_count(handle: *mut c_char) -> i64 {
    guard(|| system(handle).map_or(0, |h| h.system.cpus().len() as i64))
}

unsafe fn cpu(handle: *mut c_char, index: i64) -> Option<&'static sysinfo::Cpu> {
    system(handle).and_then(|h| h.system.cpus().get(index.max(0) as usize))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_cpu_name(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| cpu(handle, index).map(|c| unsafe { dup(c.name()) })).unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_cpu_vendor_id(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| cpu(handle, index).map(|c| unsafe { dup(c.vendor_id()) })).unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_cpu_brand(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| cpu(handle, index).map(|c| unsafe { dup(c.brand()) })).unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_cpu_frequency(handle: *mut c_char, index: i64) -> u64 {
    guard(|| cpu(handle, index).map_or(0, |c| c.frequency()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_cpu_usage(handle: *mut c_char, index: i64) -> f32 {
    guard(|| cpu(handle, index).map_or(0.0, |c| c.cpu_usage()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_total_memory(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.total_memory()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_free_memory(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.free_memory()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_available_memory(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.available_memory()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_used_memory(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.used_memory()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_total_swap(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.total_swap()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_free_swap(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.free_swap()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_used_swap(handle: *mut c_char) -> u64 {
    guard(|| system(handle).map_or(0, |h| h.system.used_swap()))
}

// ===========================================================================
// Process table iteration
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_count(handle: *mut c_char) -> i64 {
    guard(|| system(handle).map_or(0, |h| h.pids.len() as i64))
}

/// Returns the PID at `index` of the cached process list, or -1.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_pid_at(handle: *mut c_char, index: i64) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.pids.get(index.max(0) as usize))
            .map_or(-1, |p| usize::from(*p) as i64)
    })
}

// ===========================================================================
// Process accessors (by PID)
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_cpu_usage(handle: *mut c_char, pid: i64) -> f32 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(0.0, |p| p.cpu_usage())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_memory(handle: *mut c_char, pid: i64) -> u64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(0, |p| p.memory())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_virtual_memory(handle: *mut c_char, pid: i64) -> u64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(0, |p| p.virtual_memory())
    })
}

/// Parent PID or -1 when unknown.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_parent(handle: *mut c_char, pid: i64) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.parent())
            .map_or(-1, |parent| usize::from(parent) as i64)
    })
}

/// One of the `SYSKMP_PROCESS_*` status codes, or -1 when missing.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_status(handle: *mut c_char, pid: i64) -> i32 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(-1, |p| status_code(p.status()))
    })
}

fn status_code(status: ProcessStatus) -> i32 {
    match status {
        ProcessStatus::Idle => 0,
        ProcessStatus::Run => 1,
        ProcessStatus::Sleep => 2,
        ProcessStatus::Stop => 3,
        ProcessStatus::Zombie => 4,
        ProcessStatus::Tracing => 5,
        ProcessStatus::Dead => 6,
        ProcessStatus::Wakekill => 7,
        ProcessStatus::Waking => 8,
        ProcessStatus::Parked => 9,
        ProcessStatus::LockBlocked => 10,
        ProcessStatus::UninterruptibleDiskSleep => 11,
        ProcessStatus::Suspended => 12,
        ProcessStatus::Unknown(_) => 13,
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_start_time(handle: *mut c_char, pid: i64) -> u64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(0, |p| p.start_time())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_run_time(handle: *mut c_char, pid: i64) -> u64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(0, |p| p.run_time())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_name(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map(|p| unsafe { dup(&p.name().to_string_lossy()) })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_exe(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.exe())
            .map(|p| unsafe { dup(&p.to_string_lossy()) })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_cwd(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.cwd())
            .map(|p| unsafe { dup(&p.to_string_lossy()) })
    })
    .unwrap_or(ptr::null_mut())
}

/// Command line joined with `\x1F` (ASCII unit separator); empty when absent.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_cmd(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle).and_then(|h| h.process(pid)).map(|p| {
            let joined = p
                .cmd()
                .iter()
                .map(|arg| arg.to_string_lossy().into_owned())
                .collect::<Vec<_>>()
                .join("\x1F");
            unsafe { dup(&joined) }
        })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_user_id(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.user_id())
            .map(|uid| unsafe { dup(&uid.to_string()) })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_group_id(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.group_id())
            .map(|gid| unsafe { dup(&gid.to_string()) })
    })
    .unwrap_or(ptr::null_mut())
}

// ===========================================================================
// Disks
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_disks_new() -> *mut c_char {
    guard(|| Box::into_raw(Box::new(Disks::new_with_refreshed_list())) as *mut c_char)
}

/// # Safety
///
/// `handle` must have been returned by [syskmp_disks_new] and not freed yet.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disks_free(handle: *mut c_char) {
    if !handle.is_null() {
        drop(Box::from_raw(handle as *mut Disks));
    }
}

unsafe fn disks<'a>(handle: *mut c_char) -> Option<&'a mut Disks> {
    (handle as *mut Disks).as_mut()
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disks_refresh(handle: *mut c_char, remove_not_listed: bool) {
    guard(|| {
        if let Some(d) = disks(handle) {
            d.refresh(remove_not_listed);
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_count(handle: *mut c_char) -> i64 {
    guard(|| disks(handle).map_or(0, |d| d.list().len() as i64))
}
/// Looks up a disk by index.
unsafe fn disk(handle: *mut c_char, index: i64) -> Option<&'static sysinfo::Disk> {
    disks(handle).and_then(|d| d.list().get(index.max(0) as usize))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_name(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| disk(handle, index).map(|d| unsafe { dup(&d.name().to_string_lossy()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_mount_point(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| disk(handle, index).map(|d| unsafe { dup(&d.mount_point().to_string_lossy()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_file_system(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| disk(handle, index).map(|d| unsafe { dup(&d.file_system().to_string_lossy()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_total_space(handle: *mut c_char, index: i64) -> u64 {
    guard(|| disk(handle, index).map_or(0, |d| d.total_space()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_available_space(handle: *mut c_char, index: i64) -> u64 {
    guard(|| disk(handle, index).map_or(0, |d| d.available_space()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_is_removable(handle: *mut c_char, index: i64) -> bool {
    guard(|| disk(handle, index).is_some_and(|d| d.is_removable()))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_is_read_only(handle: *mut c_char, index: i64) -> bool {
    guard(|| disk(handle, index).is_some_and(|d| d.is_read_only()))
}

/// 0 = HDD, 1 = SSD, 2 = unknown.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_kind(handle: *mut c_char, index: i64) -> i32 {
    use sysinfo::DiskKind;
    guard(|| {
        disk(handle, index).map_or(2, |d| match d.kind() {
            DiskKind::HDD => 0,
            DiskKind::SSD => 1,
            DiskKind::Unknown(_) => 2,
        })
    })
}

// ===========================================================================
// Networks
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_networks_new() -> *mut c_char {
    guard(|| {
        let mut handle = NetworksHandle {
            networks: Networks::new_with_refreshed_list(),
            names: Vec::new(),
        };
        handle.refresh_names();
        Box::into_raw(Box::new(handle)) as *mut c_char
    })
}

/// # Safety
///
/// `handle` must have been returned by [syskmp_networks_new] and not freed yet.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_networks_free(handle: *mut c_char) {
    if !handle.is_null() {
        drop(Box::from_raw(handle as *mut NetworksHandle));
    }
}

unsafe fn networks<'a>(handle: *mut c_char) -> Option<&'a mut NetworksHandle> {
    (handle as *mut NetworksHandle).as_mut()
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_networks_refresh(handle: *mut c_char, remove_not_listed: bool) {
    guard(|| {
        if let Some(n) = networks(handle) {
            n.networks.refresh(remove_not_listed);
            n.refresh_names();
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_network_count(handle: *mut c_char) -> i64 {
    guard(|| networks(handle).map_or(0, |n| n.names.len() as i64))
}

unsafe fn net_data(handle: *mut c_char, index: i64) -> Option<&'static sysinfo::NetworkData> {
    networks(handle).and_then(|n| n.data(index.max(0) as usize))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_network_name(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| {
        networks(handle)
            .and_then(|n| n.names.get(index.max(0) as usize))
            .map(|name| unsafe { dup(name) })
    })
    .unwrap_or(ptr::null_mut())
}

macro_rules! network_u64_getter {
    ($name:ident, $method:ident) => {
        #[unsafe(no_mangle)]
        pub unsafe extern "C" fn $name(handle: *mut c_char, index: i64) -> u64 {
            guard(|| net_data(handle, index).map_or(0, |d| d.$method()))
        }
    };
}

network_u64_getter!(syskmp_network_received, received);
network_u64_getter!(syskmp_network_total_received, total_received);
network_u64_getter!(syskmp_network_transmitted, transmitted);
network_u64_getter!(syskmp_network_total_transmitted, total_transmitted);
network_u64_getter!(syskmp_network_packets_received, packets_received);
network_u64_getter!(
    syskmp_network_total_packets_received,
    total_packets_received
);
network_u64_getter!(syskmp_network_packets_transmitted, packets_transmitted);
network_u64_getter!(
    syskmp_network_total_packets_transmitted,
    total_packets_transmitted
);
network_u64_getter!(syskmp_network_errors_on_received, errors_on_received);
network_u64_getter!(
    syskmp_network_total_errors_on_received,
    total_errors_on_received
);
network_u64_getter!(syskmp_network_errors_on_transmitted, errors_on_transmitted);
network_u64_getter!(
    syskmp_network_total_errors_on_transmitted,
    total_errors_on_transmitted
);
network_u64_getter!(syskmp_network_mtu, mtu);

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_network_mac_address(
    handle: *mut c_char,
    index: i64,
) -> *mut c_char {
    guard(|| net_data(handle, index).map(|d| unsafe { dup(&d.mac_address().to_string()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_network_ip_count(handle: *mut c_char, index: i64) -> i64 {
    guard(|| net_data(handle, index).map_or(0, |d| d.ip_networks().len() as i64))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_network_ip_at(
    handle: *mut c_char,
    index: i64,
    ip_index: i64,
) -> *mut c_char {
    guard(|| {
        net_data(handle, index)
            .and_then(|d| d.ip_networks().get(ip_index.max(0) as usize))
            .map(|ip| unsafe { dup(&ip.to_string()) })
    })
    .unwrap_or(ptr::null_mut())
}

// ===========================================================================
// Components (temperature sensors etc.)
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_components_new() -> *mut c_char {
    guard(|| Box::into_raw(Box::new(Components::new_with_refreshed_list())) as *mut c_char)
}

/// # Safety
///
/// `handle` must have been returned by [syskmp_components_new] and not freed yet.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_components_free(handle: *mut c_char) {
    if !handle.is_null() {
        drop(Box::from_raw(handle as *mut Components));
    }
}

unsafe fn components<'a>(handle: *mut c_char) -> Option<&'a mut Components> {
    (handle as *mut Components).as_mut()
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_components_refresh(handle: *mut c_char, remove_not_listed: bool) {
    guard(|| {
        if let Some(c) = components(handle) {
            c.refresh(remove_not_listed);
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_component_count(handle: *mut c_char) -> i64 {
    guard(|| components(handle).map_or(0, |c| c.list().len() as i64))
}

unsafe fn component<'a>(handle: *mut c_char, index: i64) -> Option<&'static sysinfo::Component> {
    components(handle).and_then(|c| c.list().get(index.max(0) as usize))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_component_label(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| component(handle, index).map(|c| unsafe { dup(c.label()) })).unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_component_id(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| {
        component(handle, index)
            .and_then(|c| c.id())
            .map(|id| unsafe { dup(id) })
    })
    .unwrap_or(ptr::null_mut())
}

// Temperature getters map None to NaN so the caller can detect absence.

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_component_temperature(handle: *mut c_char, index: i64) -> f32 {
    guard(|| {
        component(handle, index)
            .and_then(|c| c.temperature())
            .unwrap_or(NAN)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_component_max(handle: *mut c_char, index: i64) -> f32 {
    guard(|| {
        component(handle, index)
            .and_then(|c| c.max())
            .unwrap_or(NAN)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_component_critical(handle: *mut c_char, index: i64) -> f32 {
    guard(|| {
        component(handle, index)
            .and_then(|c| c.critical())
            .unwrap_or(NAN)
    })
}

// ===========================================================================
// Users
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_users_new() -> *mut c_char {
    guard(|| Box::into_raw(Box::new(Users::new_with_refreshed_list())) as *mut c_char)
}

/// # Safety
///
/// `handle` must have been returned by [syskmp_users_new] and not freed yet.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_users_free(handle: *mut c_char) {
    if !handle.is_null() {
        drop(Box::from_raw(handle as *mut Users));
    }
}

unsafe fn users<'a>(handle: *mut c_char) -> Option<&'a mut Users> {
    (handle as *mut Users).as_mut()
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_users_refresh(handle: *mut c_char) {
    guard(|| {
        if let Some(u) = users(handle) {
            u.refresh();
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_user_count(handle: *mut c_char) -> i64 {
    guard(|| users(handle).map_or(0, |u| u.list().len() as i64))
}

unsafe fn user<'a>(handle: *mut c_char, index: i64) -> Option<&'static sysinfo::User> {
    users(handle).and_then(|u| u.list().get(index.max(0) as usize))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_user_id(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| user(handle, index).map(|u| unsafe { dup(&u.id().to_string()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_user_group_id(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| user(handle, index).map(|u| unsafe { dup(&u.group_id().to_string()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_user_name(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| user(handle, index).map(|u| unsafe { dup(u.name()) })).unwrap_or(ptr::null_mut())
}
// ===========================================================================
// Groups
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_groups_new() -> *mut c_char {
    guard(|| Box::into_raw(Box::new(Groups::new_with_refreshed_list())) as *mut c_char)
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_groups_free(handle: *mut c_char) {
    if !handle.is_null() {
        drop(Box::from_raw(handle as *mut Groups));
    }
}

unsafe fn groups<'a>(handle: *mut c_char) -> Option<&'a mut Groups> {
    (handle as *mut Groups).as_mut()
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_groups_refresh(handle: *mut c_char) {
    guard(|| {
        if let Some(g) = groups(handle) {
            g.refresh();
        }
    });
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_group_count(handle: *mut c_char) -> i64 {
    guard(|| groups(handle).map_or(0, |g| g.list().len() as i64))
}

unsafe fn group<'a>(handle: *mut c_char, index: i64) -> Option<&'static sysinfo::Group> {
    groups(handle).and_then(|g| g.list().get(index.max(0) as usize))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_group_id(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| group(handle, index).map(|g| unsafe { dup(&g.id().to_string()) }))
        .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_group_name(handle: *mut c_char, index: i64) -> *mut c_char {
    guard(|| group(handle, index).map(|g| unsafe { dup(g.name()) })).unwrap_or(ptr::null_mut())
}

// User groups (per-user)
#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_user_groups_count(handle: *mut c_char, index: i64) -> i64 {
    guard(|| user(handle, index).map_or(0, |u| u.groups().len() as i64))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_user_group_at(
    handle: *mut c_char,
    user_index: i64,
    group_index: i64,
) -> *mut c_char {
    guard(|| {
        user(handle, user_index)
            .and_then(|u| u.groups().into_iter().nth(group_index.max(0) as usize))
            .map(|g| unsafe { dup(&format!("{}:{}", &g.id().to_string(), g.name())) })
    })
    .unwrap_or(ptr::null_mut())
}

// ===========================================================================
// Motherboard
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_motherboard_name() -> *mut c_char {
    guard(|| Motherboard::new().and_then(|m| m.name()))
        .map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_motherboard_vendor_name() -> *mut c_char {
    guard(|| Motherboard::new().and_then(|m| m.vendor_name()))
        .map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_motherboard_version() -> *mut c_char {
    guard(|| Motherboard::new().and_then(|m| m.version()))
        .map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_motherboard_serial_number() -> *mut c_char {
    guard(|| Motherboard::new().and_then(|m| m.serial_number()))
        .map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_motherboard_asset_tag() -> *mut c_char {
    guard(|| Motherboard::new().and_then(|m| m.asset_tag()))
        .map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

// ===========================================================================
// Product
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_name() -> *mut c_char {
    guard(|| Product::name()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_family() -> *mut c_char {
    guard(|| Product::family()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_serial_number() -> *mut c_char {
    guard(|| Product::serial_number()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_stock_keeping_unit() -> *mut c_char {
    guard(|| Product::stock_keeping_unit()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_uuid() -> *mut c_char {
    guard(|| Product::uuid()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_version() -> *mut c_char {
    guard(|| Product::version()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_product_vendor_name() -> *mut c_char {
    guard(|| Product::vendor_name()).map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

// ===========================================================================
// System extra
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_kernel_long_version() -> *mut c_char {
    guard(|| unsafe { dup(&System::kernel_long_version()) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_distribution_id_like_count() -> i64 {
    guard(|| System::distribution_id_like().len() as i64)
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_distribution_id_like_at(index: i64) -> *mut c_char {
    guard(|| {
        System::distribution_id_like()
            .get(index.max(0) as usize)
            .cloned()
    })
    .map_or(ptr::null_mut(), |s| unsafe { dup(&s) })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_system_open_files_limit() -> i64 {
    guard(|| System::open_files_limit().map(|n| n as i64).unwrap_or(-1))
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_system_cgroup_limits(
    total_memory: *mut u64,
    free_memory: *mut u64,
    free_swap: *mut u64,
    rss: *mut u64,
) -> bool {
    guard(|| {
        if let Some(limits) = System::new().cgroup_limits() {
            if !total_memory.is_null() {
                unsafe { *total_memory = limits.total_memory };
            }
            if !free_memory.is_null() {
                unsafe { *free_memory = limits.free_memory };
            }
            if !free_swap.is_null() {
                unsafe { *free_swap = limits.free_swap };
            }
            if !rss.is_null() {
                unsafe { *rss = limits.rss };
            }
            true
        } else {
            false
        }
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_is_supported_system() -> bool {
    sysinfo::IS_SUPPORTED_SYSTEM
}

#[unsafe(no_mangle)]
pub extern "C" fn syskmp_minimum_cpu_update_interval_ms() -> u64 {
    sysinfo::MINIMUM_CPU_UPDATE_INTERVAL.as_millis() as u64
}

// ===========================================================================
// Process extra
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_environ(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle).and_then(|h| h.process(pid)).map(|p| {
            let joined = p
                .environ()
                .iter()
                .map(|s| s.to_string_lossy().into_owned())
                .collect::<Vec<_>>()
                .join("\x1F");
            unsafe { dup(&joined) }
        })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_root(handle: *mut c_char, pid: i64) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.root())
            .map(|p| unsafe { dup(&p.to_string_lossy()) })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_accumulated_cpu_time(handle: *mut c_char, pid: i64) -> u64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .map_or(0, |p| p.accumulated_cpu_time())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_disk_usage(
    handle: *mut c_char,
    pid: i64,
    total_written: *mut u64,
    written: *mut u64,
    total_read: *mut u64,
    read: *mut u64,
) -> bool {
    guard(|| {
        if let Some(p) = system(handle).and_then(|h| h.process(pid)) {
            let usage = p.disk_usage();
            if !total_written.is_null() {
                unsafe { *total_written = usage.total_written_bytes };
            }
            if !written.is_null() {
                unsafe { *written = usage.written_bytes };
            }
            if !total_read.is_null() {
                unsafe { *total_read = usage.total_read_bytes };
            }
            if !read.is_null() {
                unsafe { *read = usage.read_bytes };
            }
            true
        } else {
            false
        }
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_effective_user_id(
    handle: *mut c_char,
    pid: i64,
) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.effective_user_id())
            .map(|uid| unsafe { dup(&uid.to_string()) })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_effective_group_id(
    handle: *mut c_char,
    pid: i64,
) -> *mut c_char {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.effective_group_id())
            .map(|gid| unsafe { dup(&gid.to_string()) })
    })
    .unwrap_or(ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_session_id(handle: *mut c_char, pid: i64) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.session_id())
            .map_or(-1, |sid| usize::from(sid) as i64)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_exists(handle: *mut c_char, pid: i64) -> bool {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .is_some_and(|p| p.exists())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_open_files(handle: *mut c_char, pid: i64) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.open_files())
            .map_or(-1, |n| n as i64)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_open_files_limit(handle: *mut c_char, pid: i64) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.open_files_limit())
            .map_or(-1, |n| n as i64)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_cgroup_limits(
    handle: *mut c_char,
    pid: i64,
    total_memory: *mut u64,
    free_memory: *mut u64,
    free_swap: *mut u64,
    rss: *mut u64,
) -> bool {
    guard(|| {
        if let Some(p) = system(handle).and_then(|h| h.process(pid)) {
            if let Some(limits) = p.cgroup_limits() {
                if !total_memory.is_null() {
                    unsafe { *total_memory = limits.total_memory };
                }
                if !free_memory.is_null() {
                    unsafe { *free_memory = limits.free_memory };
                }
                if !free_swap.is_null() {
                    unsafe { *free_swap = limits.free_swap };
                }
                if !rss.is_null() {
                    unsafe { *rss = limits.rss };
                }
                return true;
            }
        }
        false
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_tasks_count(handle: *mut c_char, pid: i64) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.tasks())
            .map_or(0, |tasks| tasks.len() as i64)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_tasks_pid_at(
    handle: *mut c_char,
    pid: i64,
    index: i64,
) -> i64 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.tasks())
            .and_then(|tasks| {
                let mut v: Vec<Pid> = tasks.iter().copied().collect();
                v.sort_unstable_by_key(|p| usize::from(*p));
                v.get(index.max(0) as usize).copied()
            })
            .map_or(-1, |task_pid| usize::from(task_pid) as i64)
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_thread_kind(handle: *mut c_char, pid: i64) -> i32 {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.thread_kind())
            .map_or(-1, |k| match k {
                ThreadKind::Kernel => 0,
                ThreadKind::Userland => 1,
            })
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_kill(handle: *mut c_char, pid: i64) -> bool {
    guard(|| {
        system(handle)
            .and_then(|h| h.process(pid))
            .is_some_and(|p| p.kill())
    })
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_process_kill_with(
    handle: *mut c_char,
    pid: i64,
    signal: i32,
) -> i32 {
    guard(|| {
        let sig = match signal {
            0 => sysinfo::Signal::Hangup,
            1 => sysinfo::Signal::Interrupt,
            2 => sysinfo::Signal::Quit,
            3 => sysinfo::Signal::Illegal,
            4 => sysinfo::Signal::Trap,
            5 => sysinfo::Signal::Abort,
            6 => sysinfo::Signal::IOT,
            7 => sysinfo::Signal::Bus,
            8 => sysinfo::Signal::FloatingPointException,
            9 => sysinfo::Signal::Kill,
            10 => sysinfo::Signal::User1,
            11 => sysinfo::Signal::Segv,
            12 => sysinfo::Signal::User2,
            13 => sysinfo::Signal::Pipe,
            14 => sysinfo::Signal::Alarm,
            15 => sysinfo::Signal::Term,
            16 => sysinfo::Signal::Child,
            17 => sysinfo::Signal::Continue,
            18 => sysinfo::Signal::Stop,
            19 => sysinfo::Signal::TSTP,
            20 => sysinfo::Signal::TTIN,
            21 => sysinfo::Signal::TTOU,
            22 => sysinfo::Signal::Urgent,
            23 => sysinfo::Signal::XCPU,
            24 => sysinfo::Signal::XFSZ,
            25 => sysinfo::Signal::VirtualAlarm,
            26 => sysinfo::Signal::Profiling,
            27 => sysinfo::Signal::Winch,
            28 => sysinfo::Signal::IO,
            29 => sysinfo::Signal::Poll,
            30 => sysinfo::Signal::Power,
            31 => sysinfo::Signal::Sys,
            _ => return -1,
        };
        system(handle)
            .and_then(|h| h.process(pid))
            .and_then(|p| p.kill_with(sig))
            .map_or(-1, |v| if v { 1 } else { 0 })
    })
}

// ===========================================================================
// Disk extra
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_disk_usage(
    handle: *mut c_char,
    index: i64,
    total_written: *mut u64,
    written: *mut u64,
    total_read: *mut u64,
    read: *mut u64,
) -> bool {
    guard(|| {
        if let Some(d) = disk(handle, index) {
            let usage = d.usage();
            if !total_written.is_null() {
                unsafe { *total_written = usage.total_written_bytes };
            }
            if !written.is_null() {
                unsafe { *written = usage.written_bytes };
            }
            if !total_read.is_null() {
                unsafe { *total_read = usage.total_read_bytes };
            }
            if !read.is_null() {
                unsafe { *read = usage.read_bytes };
            }
            true
        } else {
            false
        }
    })
}

// ===========================================================================
// Network extra
// ===========================================================================

#[unsafe(no_mangle)]
pub unsafe extern "C" fn syskmp_network_operational_state(handle: *mut c_char, index: i64) -> i32 {
    guard(|| {
        net_data(handle, index).map_or(-1, |d| {
            let raw = format!("{:?}", d.operational_state());
            if raw.contains("Up") {
                1
            } else if raw.contains("Down") && !raw.contains("LowerLayer") {
                2
            } else if raw.contains("Testing") {
                3
            } else if raw.contains("Dormant") {
                4
            } else if raw.contains("NotPresent") {
                5
            } else if raw.contains("LowerLayerDown") {
                6
            } else {
                0
            }
        })
    })
}
