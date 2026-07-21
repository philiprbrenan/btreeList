#include <stdio.h>
#include <time.h>
#include <vpi_user.h>
/*
 * This is the function called when Verilog executes $wall_time
 */
PLI_INT32 wall_time_calltf(PLI_BYTE8 *user_data)
 {struct timespec ts;

  clock_gettime(CLOCK_REALTIME, &ts);

  vpi_printf("Time: %ld.%09ld", ts.tv_sec, ts.tv_nsec);

  return 0;
 }
/*
 * Register the new Verilog system task
 */
void register_wall_time()
 {s_vpi_systf_data tf_data;

  tf_data.type      = vpiSysTask;
  tf_data.tfname    = "$wall_time";
  tf_data.calltf    = wall_time_calltf;
  tf_data.compiletf = 0;
  tf_data.sizetf    = 0;
  tf_data.user_data = 0;

  vpi_register_systf(&tf_data);
 }
/*
 * Icarus calls functions in this array when loading the VPI module
 */
void (*vlog_startup_routines[])() =
 {register_wall_time,
  0
 };
