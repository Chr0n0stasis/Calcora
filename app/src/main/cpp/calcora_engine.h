#ifndef CALCORA_ENGINE_H
#define CALCORA_ENGINE_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Platform-neutral Calcora engine API.
 *
 * Returned strings are UTF-8 and remain valid until the next string-returning
 * call on the same thread. Callers must copy them before making another call.
 */
void calcora_engine_init(void);
const char *calcora_engine_evaluate(const char *expr, const char *mode);
const char *calcora_engine_plot_sample(
    const char *expr,
    const char *variable,
    double xmin,
    double xmax,
    int samples
);
const char *calcora_engine_help(const char *command);
void calcora_engine_reset(void);
void calcora_engine_interrupt(void);
void calcora_engine_set_language(int code);
void calcora_engine_set_help_dir(const char *path);
const char *calcora_engine_version(void);

#ifdef __cplusplus
}
#endif

#endif
