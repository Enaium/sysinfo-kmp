//! JNI exports for the JVM target of sysinfo-kmp.
//!
//! Every function maps 1:1 to an `external fun` on `cn.enaium.sysinfo.Native`
//! (hence the `Java_cn_enaium_sysinfo_Native_` prefix). All functions are
//! static (`jclass` receiver). Bodies delegate to the C ABI in [crate], so
//! the JVM and native code paths share one implementation. Java strings are
//! materialized from the heap-allocated C strings the C ABI hands out, which
//! are released immediately after conversion.
// Call sites use a uniform `unsafe {}` wrapper around C ABI calls whether the
// individual export is declared safe or `unsafe`, so the JNI layer stays regular.
// Parameter names mirror the Kotlin declarations (camelCase), hence non_snake_case.
#![allow(clippy::missing_safety_doc, unused_unsafe, non_snake_case, unused_mut)]

use std::ffi::{CStr, c_char};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring};

/// Converts a C string returned by the C ABI into a Java string and releases
/// the original. NULL becomes a Java null.
fn to_jstring(env: &mut JNIEnv, s: *mut c_char) -> jstring {
    if s.is_null() {
        return ptr::null_mut();
    }
    let text = unsafe { CStr::from_ptr(s) }.to_string_lossy();
    let result = env
        .new_string(text)
        .map(|j| j.into_raw())
        .unwrap_or(ptr::null_mut());
    unsafe { crate::syskmp_free_string(s) };
    result
}

/// Silence unused-import warnings on platforms where JString isn't referenced.
#[allow(unused)]
fn _touch(_: &JString) {}

// ===========================================================================
// Static system information
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_name(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_name() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_kernelVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_kernel_version() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_osVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_os_version() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_longOsVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_long_os_version() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_distributionId(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_distribution_id() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_hostName(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_host_name() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuArch(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_system_cpu_arch() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_uptime(_env: JNIEnv, _class: JClass) -> jlong {
    unsafe { crate::syskmp_uptime() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_bootTime(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_boot_time() as jlong }
}

/// Returns the 1/5/15-minute load averages as a `double[3]`, or null when
/// load average is unavailable on this platform.
#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_loadAverage(
    mut env: JNIEnv,
    _class: JClass,
) -> jni::sys::jobject {
    let mut buf = [0.0f64; 3];
    let ok = unsafe {
        crate::syskmp_load_average(
            buf.as_mut_ptr(),
            buf.as_mut_ptr().add(1),
            buf.as_mut_ptr().add(2),
        )
    };
    if !ok {
        return ptr::null_mut();
    }
    let array = match env.new_double_array(3) {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };
    let raw = array.as_raw();
    if env.set_double_array_region(array, 0, &buf).is_err() {
        return ptr::null_mut();
    }
    raw
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_physicalCoreCount(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_physical_core_count() as jlong }
}

// ===========================================================================
// System lifecycle and refresh
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemNew(
    _env: JNIEnv,
    _class: JClass,
    all: jboolean,
) -> jlong {
    unsafe { crate::syskmp_system_new(all != 0) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { crate::syskmp_system_free(handle as *mut _) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemRefreshAll(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { crate::syskmp_system_refresh_all(handle as *mut _) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemRefreshMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { crate::syskmp_system_refresh_memory(handle as *mut _) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemRefreshCpu(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { crate::syskmp_system_refresh_cpu(handle as *mut _) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemRefreshProcesses(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_system_refresh_processes(handle as *mut _) as jlong }
}

// ===========================================================================
// CPU / memory / swap
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_globalCpuUsage(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jfloat {
    unsafe { crate::syskmp_global_cpu_usage(handle as *mut _) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_cpu_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_cpu_name(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuVendorId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_cpu_vendor_id(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuBrand(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_cpu_brand(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuFrequency(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jlong {
    unsafe { crate::syskmp_cpu_frequency(handle as *mut _, index) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_cpuUsage(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jfloat {
    unsafe { crate::syskmp_cpu_usage(handle as *mut _, index) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_totalMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_total_memory(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_freeMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_free_memory(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_availableMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_available_memory(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_usedMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_used_memory(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_totalSwap(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_total_swap(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_freeSwap(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_free_swap(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_usedSwap(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_used_swap(handle as *mut _) as jlong }
}

// ===========================================================================
// Process table
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_pidAt(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jlong {
    unsafe { crate::syskmp_pid_at(handle as *mut _, index) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processCpuUsage(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jfloat {
    unsafe { crate::syskmp_process_cpu_usage(handle as *mut _, pid) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_memory(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processVirtualMemory(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_virtual_memory(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processParent(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_parent(handle as *mut _, pid) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processStatus(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jint {
    unsafe { crate::syskmp_process_status(handle as *mut _, pid) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processStartTime(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_start_time(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processRunTime(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_run_time(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_name(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processExe(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_exe(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processCwd(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_cwd(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processCmd(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_cmd(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processUserId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_user_id(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processGroupId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_group_id(handle as *mut _, pid)
    })
}

// ===========================================================================
// Disks
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_disksNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_disks_new() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_disksFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { crate::syskmp_disks_free(handle as *mut _) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_disksRefresh(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    removeNotListed: jboolean,
) {
    unsafe { crate::syskmp_disks_refresh(handle as *mut _, removeNotListed != 0) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_disk_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_disk_name(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskMountPoint(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_disk_mount_point(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskFileSystem(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_disk_file_system(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskTotalSpace(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jlong {
    unsafe { crate::syskmp_disk_total_space(handle as *mut _, index) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskAvailableSpace(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jlong {
    unsafe { crate::syskmp_disk_available_space(handle as *mut _, index) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskIsRemovable(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jboolean {
    unsafe { crate::syskmp_disk_is_removable(handle as *mut _, index) as u8 as jboolean }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskIsReadOnly(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jboolean {
    unsafe { crate::syskmp_disk_is_read_only(handle as *mut _, index) as u8 as jboolean }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskKind(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jint {
    unsafe { crate::syskmp_disk_kind(handle as *mut _, index) }
}

// ===========================================================================
// Networks
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networksNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_networks_new() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networksFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { crate::syskmp_networks_free(handle as *mut _) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networksRefresh(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    removeNotListed: jboolean,
) {
    unsafe { crate::syskmp_networks_refresh(handle as *mut _, removeNotListed != 0) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networkCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_network_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networkName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_network_name(handle as *mut _, index)
    })
}

macro_rules! network_long_export {
    ($method:ident, $abi:path) => {
        #[unsafe(no_mangle)]
        pub extern "system" fn $method(
            _env: JNIEnv,
            _class: JClass,
            handle: jlong,
            index: jlong,
        ) -> jlong {
            unsafe { $abi(handle as *mut _, index) as jlong }
        }
    };
}

network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkReceived,
    crate::syskmp_network_received
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTotalReceived,
    crate::syskmp_network_total_received
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTransmitted,
    crate::syskmp_network_transmitted
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTotalTransmitted,
    crate::syskmp_network_total_transmitted
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkPacketsReceived,
    crate::syskmp_network_packets_received
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTotalPacketsReceived,
    crate::syskmp_network_total_packets_received
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkPacketsTransmitted,
    crate::syskmp_network_packets_transmitted
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTotalPacketsTransmitted,
    crate::syskmp_network_total_packets_transmitted
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkErrorsOnReceived,
    crate::syskmp_network_errors_on_received
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTotalErrorsOnReceived,
    crate::syskmp_network_total_errors_on_received
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkErrorsOnTransmitted,
    crate::syskmp_network_errors_on_transmitted
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkTotalErrorsOnTransmitted,
    crate::syskmp_network_total_errors_on_transmitted
);
network_long_export!(
    Java_cn_enaium_sysinfo_Native_networkMtu,
    crate::syskmp_network_mtu
);

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networkMacAddress(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_network_mac_address(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networkIpCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jlong {
    unsafe { crate::syskmp_network_ip_count(handle as *mut _, index) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networkIpAt(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
    ipIndex: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_network_ip_at(handle as *mut _, index, ipIndex)
    })
}

// ===========================================================================
// Components (temperature sensors etc.)
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentsNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_components_new() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentsFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { crate::syskmp_components_free(handle as *mut _) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentsRefresh(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    removeNotListed: jboolean,
) {
    unsafe { crate::syskmp_components_refresh(handle as *mut _, removeNotListed != 0) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_component_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentLabel(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_component_label(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_component_id(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentTemperature(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jfloat {
    unsafe { crate::syskmp_component_temperature(handle as *mut _, index) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentMax(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jfloat {
    unsafe { crate::syskmp_component_max(handle as *mut _, index) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_componentCritical(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jfloat {
    unsafe { crate::syskmp_component_critical(handle as *mut _, index) }
}

// ===========================================================================
// Users
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_usersNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_users_new() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_usersFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { crate::syskmp_users_free(handle as *mut _) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_usersRefresh(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { crate::syskmp_users_refresh(handle as *mut _) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_userCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_user_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_userId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_user_id(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_userGroupId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_user_group_id(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_userName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_user_name(handle as *mut _, index)
    })
}

// ===========================================================================
// Groups
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_groupsNew(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_groups_new() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_groupsFree(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe { crate::syskmp_groups_free(handle as *mut _) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_groupsRefresh(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    unsafe { crate::syskmp_groups_refresh(handle as *mut _) };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_groupCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    unsafe { crate::syskmp_group_count(handle as *mut _) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_groupId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_group_id(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_groupName(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_group_name(handle as *mut _, index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_userGroupsCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    userIndex: jlong,
) -> jlong {
    unsafe { crate::syskmp_user_groups_count(handle as *mut _, userIndex) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_userGroupAt(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    userIndex: jlong,
    groupIndex: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_user_group_at(handle as *mut _, userIndex, groupIndex)
    })
}

// ===========================================================================
// Motherboard
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_motherboardName(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_motherboard_name() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_motherboardVendorName(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_motherboard_vendor_name() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_motherboardVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_motherboard_version() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_motherboardSerialNumber(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_motherboard_serial_number()
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_motherboardAssetTag(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_motherboard_asset_tag() })
}

// ===========================================================================
// Product
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productName(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_product_name() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productFamily(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_product_family() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productSerialNumber(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_product_serial_number() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productStockKeepingUnit(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_product_stock_keeping_unit()
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productUuid(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_product_uuid() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_product_version() })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_productVendorName(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe { crate::syskmp_product_vendor_name() })
}

// ===========================================================================
// System extra
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemKernelLongVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_system_kernel_long_version()
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemDistributionIdLikeCount(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_system_distribution_id_like_count() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemDistributionIdLikeAt(
    mut env: JNIEnv,
    _class: JClass,
    index: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_system_distribution_id_like_at(index)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemOpenFilesLimit(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_system_open_files_limit() as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_systemCgroupLimits(
    mut env: JNIEnv,
    _class: JClass,
) -> jni::sys::jobject {
    let mut total: u64 = 0;
    let mut free: u64 = 0;
    let mut swap: u64 = 0;
    let mut rss: u64 = 0;
    let ok = unsafe {
        crate::syskmp_system_cgroup_limits(
            &mut total as *mut u64,
            &mut free as *mut u64,
            &mut swap as *mut u64,
            &mut rss as *mut u64,
        )
    };
    if !ok {
        return std::ptr::null_mut();
    }
    let buf = [total as i64, free as i64, swap as i64, rss as i64];
    let array = match env.new_long_array(4) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let raw = array.as_raw();
    if env.set_long_array_region(array, 0, &buf).is_err() {
        return std::ptr::null_mut();
    }
    raw
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_isSupportedSystem(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    unsafe { crate::syskmp_is_supported_system() as u8 as jboolean }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_minimumCpuUpdateIntervalMs(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    unsafe { crate::syskmp_minimum_cpu_update_interval_ms() as jlong }
}

// ===========================================================================
// Process extra
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processEnviron(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_environ(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processRoot(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_root(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processAccumulatedCpuTime(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_accumulated_cpu_time(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processDiskUsage(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jni::sys::jobject {
    let mut total_written: u64 = 0;
    let mut written: u64 = 0;
    let mut total_read: u64 = 0;
    let mut read: u64 = 0;
    let ok = unsafe {
        crate::syskmp_process_disk_usage(
            handle as *mut _,
            pid,
            &mut total_written as *mut u64,
            &mut written as *mut u64,
            &mut total_read as *mut u64,
            &mut read as *mut u64,
        )
    };
    if !ok {
        return std::ptr::null_mut();
    }
    let buf = [
        total_written as i64,
        written as i64,
        total_read as i64,
        read as i64,
    ];
    let array = match env.new_long_array(4) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let raw = array.as_raw();
    if env.set_long_array_region(array, 0, &buf).is_err() {
        return std::ptr::null_mut();
    }
    raw
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processEffectiveUserId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_effective_user_id(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processEffectiveGroupId(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jstring {
    to_jstring(&mut env, unsafe {
        crate::syskmp_process_effective_group_id(handle as *mut _, pid)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processSessionId(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_session_id(handle as *mut _, pid) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processExists(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jboolean {
    unsafe { crate::syskmp_process_exists(handle as *mut _, pid) as u8 as jboolean }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processOpenFiles(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_open_files(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processOpenFilesLimit(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_open_files_limit(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processCgroupLimits(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jni::sys::jobject {
    let mut total: u64 = 0;
    let mut free: u64 = 0;
    let mut swap: u64 = 0;
    let mut rss: u64 = 0;
    let ok = unsafe {
        crate::syskmp_process_cgroup_limits(
            handle as *mut _,
            pid,
            &mut total as *mut u64,
            &mut free as *mut u64,
            &mut swap as *mut u64,
            &mut rss as *mut u64,
        )
    };
    if !ok {
        return std::ptr::null_mut();
    }
    let buf = [total as i64, free as i64, swap as i64, rss as i64];
    let array = match env.new_long_array(4) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let raw = array.as_raw();
    if env.set_long_array_region(array, 0, &buf).is_err() {
        return std::ptr::null_mut();
    }
    raw
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processTasksCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_tasks_count(handle as *mut _, pid) as jlong }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processTasksPidAt(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
    index: jlong,
) -> jlong {
    unsafe { crate::syskmp_process_tasks_pid_at(handle as *mut _, pid, index) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processThreadKind(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jint {
    unsafe { crate::syskmp_process_thread_kind(handle as *mut _, pid) }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processKill(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
) -> jboolean {
    unsafe { crate::syskmp_process_kill(handle as *mut _, pid) as u8 as jboolean }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_processKillWith(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    pid: jlong,
    signal: jint,
) -> jint {
    unsafe { crate::syskmp_process_kill_with(handle as *mut _, pid, signal) }
}

// ===========================================================================
// Disk extra
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_diskUsage(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jni::sys::jobject {
    let mut total_written: u64 = 0;
    let mut written: u64 = 0;
    let mut total_read: u64 = 0;
    let mut read: u64 = 0;
    let ok = unsafe {
        crate::syskmp_disk_usage(
            handle as *mut _,
            index,
            &mut total_written as *mut u64,
            &mut written as *mut u64,
            &mut total_read as *mut u64,
            &mut read as *mut u64,
        )
    };
    if !ok {
        return std::ptr::null_mut();
    }
    let buf = [
        total_written as i64,
        written as i64,
        total_read as i64,
        read as i64,
    ];
    let array = match env.new_long_array(4) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    let raw = array.as_raw();
    if env.set_long_array_region(array, 0, &buf).is_err() {
        return std::ptr::null_mut();
    }
    raw
}

// ===========================================================================
// Network extra
// ===========================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_cn_enaium_sysinfo_Native_networkOperationalState(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jlong,
) -> jint {
    unsafe { crate::syskmp_network_operational_state(handle as *mut _, index) }
}
