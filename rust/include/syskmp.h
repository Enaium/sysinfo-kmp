/*
 * syskmp C ABI — consumed by the Kotlin/Native cinterop of sysinfo-kmp.
 *
 * Conventions:
 *  - Handles (`syskmp_*_t`) are opaque; create with `*_new`, release with the
 *    matching `*_free`.
 *  - Every returned `char*` is heap-allocated; release it with
 *    `syskmp_free_string`. NULL means "not available".
 *  - Missing numeric values are reported as 0 / -1 / NaN per function.
 */
#ifndef SYSKMP_H
#define SYSKMP_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* syskmp_system_t;
typedef void* syskmp_disks_t;
typedef void* syskmp_networks_t;
typedef void* syskmp_components_t;
typedef void* syskmp_users_t;
typedef void* syskmp_groups_t;

/* ---- memory management -------------------------------------------------- */

void syskmp_free_string(char* s);

/* ---- static system information ------------------------------------------ */

char* syskmp_system_name(void);
char* syskmp_system_kernel_version(void);
char* syskmp_system_os_version(void);
char* syskmp_system_long_os_version(void);
char* syskmp_system_distribution_id(void);
char* syskmp_system_host_name(void);
char* syskmp_system_cpu_arch(void);
uint64_t syskmp_uptime(void);
uint64_t syskmp_boot_time(void);
int32_t syskmp_load_average(double* one, double* five, double* fifteen);
int64_t syskmp_physical_core_count(void);

/* ---- system handle lifecycle and refresh -------------------------------- */

syskmp_system_t syskmp_system_new(int32_t new_all);
void syskmp_system_free(syskmp_system_t system);
void syskmp_system_refresh_all(syskmp_system_t system);
void syskmp_system_refresh_memory(syskmp_system_t system);
void syskmp_system_refresh_cpu(syskmp_system_t system);
int64_t syskmp_system_refresh_processes(syskmp_system_t system);

/* ---- cpu / memory / swap ------------------------------------------------ */

float syskmp_global_cpu_usage(syskmp_system_t system);
int64_t syskmp_cpu_count(syskmp_system_t system);
char* syskmp_cpu_name(syskmp_system_t system, int64_t index);
char* syskmp_cpu_vendor_id(syskmp_system_t system, int64_t index);
char* syskmp_cpu_brand(syskmp_system_t system, int64_t index);
uint64_t syskmp_cpu_frequency(syskmp_system_t system, int64_t index);
float syskmp_cpu_usage(syskmp_system_t system, int64_t index);
uint64_t syskmp_total_memory(syskmp_system_t system);
uint64_t syskmp_free_memory(syskmp_system_t system);
uint64_t syskmp_available_memory(syskmp_system_t system);
uint64_t syskmp_used_memory(syskmp_system_t system);
uint64_t syskmp_total_swap(syskmp_system_t system);
uint64_t syskmp_free_swap(syskmp_system_t system);
uint64_t syskmp_used_swap(syskmp_system_t system);

/* ---- processes ------------------------------------------------------------ */

int64_t syskmp_process_count(syskmp_system_t system);
int64_t syskmp_pid_at(syskmp_system_t system, int64_t index);
float syskmp_process_cpu_usage(syskmp_system_t system, int64_t pid);
uint64_t syskmp_process_memory(syskmp_system_t system, int64_t pid);
uint64_t syskmp_process_virtual_memory(syskmp_system_t system, int64_t pid);
int64_t syskmp_process_parent(syskmp_system_t system, int64_t pid);
/* Status codes: 0 idle, 1 run, 2 sleep, 3 stop, 4 zombie, 5 tracing,
 * 6 dead, 7 wakekill, 8 waking, 9 parked, 10 lock-blocked,
 * 11 uninterruptible-disk-sleep, 12 suspended, 13 unknown; -1 = missing. */
int32_t syskmp_process_status(syskmp_system_t system, int64_t pid);
uint64_t syskmp_process_start_time(syskmp_system_t system, int64_t pid);
uint64_t syskmp_process_run_time(syskmp_system_t system, int64_t pid);
char* syskmp_process_name(syskmp_system_t system, int64_t pid);
char* syskmp_process_exe(syskmp_system_t system, int64_t pid);
char* syskmp_process_cwd(syskmp_system_t system, int64_t pid);
char* syskmp_process_cmd(syskmp_system_t system, int64_t pid);
char* syskmp_process_user_id(syskmp_system_t system, int64_t pid);
char* syskmp_process_group_id(syskmp_system_t system, int64_t pid);

/* ---- disks ----------------------------------------------------------------- */

syskmp_disks_t syskmp_disks_new(void);
void syskmp_disks_free(syskmp_disks_t disks);
void syskmp_disks_refresh(syskmp_disks_t disks, int32_t remove_not_listed);
int64_t syskmp_disk_count(syskmp_disks_t disks);
char* syskmp_disk_name(syskmp_disks_t disks, int64_t index);
char* syskmp_disk_mount_point(syskmp_disks_t disks, int64_t index);
char* syskmp_disk_file_system(syskmp_disks_t disks, int64_t index);
uint64_t syskmp_disk_total_space(syskmp_disks_t disks, int64_t index);
uint64_t syskmp_disk_available_space(syskmp_disks_t disks, int64_t index);
int32_t syskmp_disk_is_removable(syskmp_disks_t disks, int64_t index);
int32_t syskmp_disk_is_read_only(syskmp_disks_t disks, int64_t index);
/* Kind codes: 0 = HDD, 1 = SSD, 2 = unknown. */
int32_t syskmp_disk_kind(syskmp_disks_t disks, int64_t index);

/* ---- networks ---------------------------------------------------------------- */

syskmp_networks_t syskmp_networks_new(void);
void syskmp_networks_free(syskmp_networks_t networks);
void syskmp_networks_refresh(syskmp_networks_t networks, int32_t remove_not_listed);
int64_t syskmp_network_count(syskmp_networks_t networks);
char* syskmp_network_name(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_received(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_total_received(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_transmitted(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_total_transmitted(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_packets_received(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_total_packets_received(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_packets_transmitted(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_total_packets_transmitted(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_errors_on_received(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_total_errors_on_received(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_errors_on_transmitted(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_total_errors_on_transmitted(syskmp_networks_t networks, int64_t index);
uint64_t syskmp_network_mtu(syskmp_networks_t networks, int64_t index);
char* syskmp_network_mac_address(syskmp_networks_t networks, int64_t index);
int64_t syskmp_network_ip_count(syskmp_networks_t networks, int64_t index);
char* syskmp_network_ip_at(syskmp_networks_t networks, int64_t index, int64_t ip_index);

/* ---- components (temperature sensors etc.) ------------------------------- */

syskmp_components_t syskmp_components_new(void);
void syskmp_components_free(syskmp_components_t components);
void syskmp_components_refresh(syskmp_components_t components, int32_t remove_not_listed);
int64_t syskmp_component_count(syskmp_components_t components);
char* syskmp_component_label(syskmp_components_t components, int64_t index);
char* syskmp_component_id(syskmp_components_t components, int64_t index);
/* Temperatures are NaN when not reported. */
float syskmp_component_temperature(syskmp_components_t components, int64_t index);
float syskmp_component_max(syskmp_components_t components, int64_t index);
float syskmp_component_critical(syskmp_components_t components, int64_t index);

/* ---- users ------------------------------------------------------------------- */

syskmp_users_t syskmp_users_new(void);
void syskmp_users_free(syskmp_users_t users);
void syskmp_users_refresh(syskmp_users_t users);
int64_t syskmp_user_count(syskmp_users_t users);
char* syskmp_user_id(syskmp_users_t users, int64_t index);
char* syskmp_user_group_id(syskmp_users_t users, int64_t index);
char* syskmp_user_name(syskmp_users_t users, int64_t index);

/* ---- groups ------------------------------------------------------------------ */

syskmp_groups_t syskmp_groups_new(void);
void syskmp_groups_free(syskmp_groups_t groups);
void syskmp_groups_refresh(syskmp_groups_t groups);
int64_t syskmp_group_count(syskmp_groups_t groups);
char* syskmp_group_id(syskmp_groups_t groups, int64_t index);
char* syskmp_group_name(syskmp_groups_t groups, int64_t index);
int64_t syskmp_user_groups_count(syskmp_users_t users, int64_t user_index);
char* syskmp_user_group_at(syskmp_users_t users, int64_t user_index, int64_t group_index);

/* ---- motherboard ------------------------------------------------------------ */

char* syskmp_motherboard_name(void);
char* syskmp_motherboard_vendor_name(void);
char* syskmp_motherboard_version(void);
char* syskmp_motherboard_serial_number(void);
char* syskmp_motherboard_asset_tag(void);

/* ---- product ---------------------------------------------------------------- */

char* syskmp_product_name(void);
char* syskmp_product_family(void);
char* syskmp_product_serial_number(void);
char* syskmp_product_stock_keeping_unit(void);
char* syskmp_product_uuid(void);
char* syskmp_product_version(void);
char* syskmp_product_vendor_name(void);

/* ---- system extra ----------------------------------------------------------- */

char* syskmp_system_kernel_long_version(void);
int64_t syskmp_system_distribution_id_like_count(void);
char* syskmp_system_distribution_id_like_at(int64_t index);
int64_t syskmp_system_open_files_limit(void);
int32_t syskmp_system_cgroup_limits(uint64_t* total_memory, uint64_t* free_memory, uint64_t* free_swap, uint64_t* rss);
int32_t syskmp_is_supported_system(void);
uint64_t syskmp_minimum_cpu_update_interval_ms(void);

/* ---- process extra ---------------------------------------------------------- */

char* syskmp_process_environ(syskmp_system_t system, int64_t pid);
char* syskmp_process_root(syskmp_system_t system, int64_t pid);
uint64_t syskmp_process_accumulated_cpu_time(syskmp_system_t system, int64_t pid);
int32_t syskmp_process_disk_usage(syskmp_system_t system, int64_t pid, uint64_t* total_written, uint64_t* written, uint64_t* total_read, uint64_t* read);
char* syskmp_process_effective_user_id(syskmp_system_t system, int64_t pid);
char* syskmp_process_effective_group_id(syskmp_system_t system, int64_t pid);
int64_t syskmp_process_session_id(syskmp_system_t system, int64_t pid);
int32_t syskmp_process_exists(syskmp_system_t system, int64_t pid);
int64_t syskmp_process_open_files(syskmp_system_t system, int64_t pid);
int64_t syskmp_process_open_files_limit(syskmp_system_t system, int64_t pid);
int32_t syskmp_process_cgroup_limits(syskmp_system_t system, int64_t pid, uint64_t* total_memory, uint64_t* free_memory, uint64_t* free_swap, uint64_t* rss);
int64_t syskmp_process_tasks_count(syskmp_system_t system, int64_t pid);
int64_t syskmp_process_tasks_pid_at(syskmp_system_t system, int64_t pid, int64_t index);
int32_t syskmp_process_thread_kind(syskmp_system_t system, int64_t pid);
int32_t syskmp_process_kill(syskmp_system_t system, int64_t pid);
int32_t syskmp_process_kill_with(syskmp_system_t system, int64_t pid, int32_t signal);

/* ---- disk extra ------------------------------------------------------------- */

int32_t syskmp_disk_usage(syskmp_disks_t disks, int64_t index, uint64_t* total_written, uint64_t* written, uint64_t* total_read, uint64_t* read);

/* ---- network extra ---------------------------------------------------------- */

int32_t syskmp_network_operational_state(syskmp_networks_t networks, int64_t index);

#ifdef __cplusplus
}
#endif

#endif /* SYSKMP_H */
