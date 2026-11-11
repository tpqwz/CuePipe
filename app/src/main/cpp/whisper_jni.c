#include <stdlib.h>
#include <stdio.h>
#include <string.h>

static int grow(char **out, size_t *len, size_t *cap, size_t need) {
    if (*out != NULL && *len + need + 1 <= *cap) {
        return 0;
    }
    size_t next = *cap < 4096 ? 4096 : *cap;
    while (*len + need + 1 > next) {
        if (next > ((size_t) -1) / 2) {
            return -1;
        }
        next *= 2;
    }
    char *grown = realloc(*out, next);
    if (grown == NULL) {
        return -1;
    }
    *out = grown;
    *cap = next;
    return 0;
}

/* One segment is one line. A long line is cut so the copy stays inside the buffer. */
static int append_segment(char **out, size_t *len, size_t *cap,
                          long long t0, long long t1, const char *text) {
    char line[2048];
    char flat[1800];
    size_t n = 0;
    if (text == NULL) {
        text = "";
    }
    for (size_t i = 0; text[i] != '\0' && n + 1 < sizeof flat; i++) {
        char c = text[i];
        if (c == '\t' || c == '\n' || c == '\r') {
            c = ' ';
        }
        flat[n++] = c;
    }
    flat[n] = '\0';
    int wrote = snprintf(line, sizeof line, "%lld\t%lld\t%s\n", t0, t1, flat);
    if (wrote < 0) {
        return 0;
    }
    size_t ncopy = (size_t) wrote;
    if (ncopy >= sizeof line) {
        ncopy = sizeof line - 1;
        line[ncopy - 1] = '\n';
    }
    if (grow(out, len, cap, ncopy) != 0) {
        return -1;
    }
    memcpy(*out + *len, line, ncopy);
    *len += ncopy;
    (*out)[*len] = '\0';
    return 0;
}

static int append_lang(char **out, size_t *len, size_t *cap, const char *code) {
    char line[64];
    if (code == NULL || code[0] == '\0') {
        code = "und";
    }
    int wrote = snprintf(line, sizeof line, "lang\t%.16s\n", code);
    if (wrote < 0) {
        return 0;
    }
    size_t ncopy = (size_t) wrote;
    if (ncopy >= sizeof line) {
        ncopy = sizeof line - 1;
    }
    if (grow(out, len, cap, ncopy) != 0) {
        return -1;
    }
    memcpy(*out + *len, line, ncopy);
    *len += ncopy;
    (*out)[*len] = '\0';
    return 0;
}

#ifndef CUEPIPE_FORMAT_TEST
#include <jni.h>
#include "whisper.h"

JNIEXPORT jlong JNICALL
Java_app_cuepipe_Speech_init(JNIEnv *env, jclass cls, jstring path) {
    (void) cls;
    const char *file = (*env)->GetStringUTFChars(env, path, NULL);
    if (file == NULL) {
        return 0;
    }
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(file, cparams);
    (*env)->ReleaseStringUTFChars(env, path, file);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_app_cuepipe_Speech_freeContext(JNIEnv *env, jclass cls, jlong ptr) {
    (void) env;
    (void) cls;
    if (ptr != 0) {
        whisper_free((struct whisper_context *) ptr);
    }
}

JNIEXPORT jstring JNICALL
Java_app_cuepipe_Speech_transcribe(JNIEnv *env, jclass cls, jlong ptr, jfloatArray samples) {
    (void) cls;
    struct whisper_context *ctx = (struct whisper_context *) ptr;
    if (ctx == NULL || samples == NULL) {
        return (*env)->NewStringUTF(env, "");
    }
    jsize n = (*env)->GetArrayLength(env, samples);
    jfloat *data = (*env)->GetFloatArrayElements(env, samples, NULL);
    if (data == NULL || n <= 0) {
        if (data != NULL) {
            (*env)->ReleaseFloatArrayElements(env, samples, data, JNI_ABORT);
        }
        return (*env)->NewStringUTF(env, "");
    }
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.n_threads = 4;
    params.language = "auto";
    params.detect_language = true;
    int failed = whisper_full(ctx, params, data, (int) n) != 0;
    (*env)->ReleaseFloatArrayElements(env, samples, data, JNI_ABORT);
    if (failed) {
        return (*env)->NewStringUTF(env, "");
    }
    char *out = NULL;
    size_t len = 0;
    size_t cap = 0;
    const char *code = whisper_lang_str(whisper_full_lang_id(ctx));
    if (append_lang(&out, &len, &cap, code) != 0) {
        free(out);
        return (*env)->NewStringUTF(env, "");
    }
    int count = whisper_full_n_segments(ctx);
    for (int i = 0; i < count; i++) {
        long long t0 = whisper_full_get_segment_t0(ctx, i) * 10LL;
        long long t1 = whisper_full_get_segment_t1(ctx, i) * 10LL;
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (append_segment(&out, &len, &cap, t0, t1, text) != 0) {
            break;
        }
    }
    jstring result = (*env)->NewStringUTF(env, out == NULL ? "" : out);
    free(out);
    return result;
}
#endif

#ifdef CUEPIPE_FORMAT_TEST
int main(void) {
    char *out = NULL;
    size_t len = 0;
    size_t cap = 0;
    char big[5000];
    memset(big, 'A', sizeof big - 1);
    big[sizeof big - 1] = '\0';
    big[100] = '\n';
    big[101] = '\t';
    if (append_lang(&out, &len, &cap, "en") != 0) {
        return 1;
    }
    if (append_segment(&out, &len, &cap, 0, 1500, big) != 0) {
        return 2;
    }
    if (append_segment(&out, &len, &cap, 1500, 2000, "hello there") != 0) {
        return 3;
    }
    if (strstr(out, "lang\ten\n") != out) {
        return 4;
    }
    if (strstr(out, "hello there") == NULL) {
        return 5;
    }
    free(out);
    out = NULL;
    len = 0;
    cap = 0;
    if (append_lang(&out, &len, &cap, NULL) != 0) {
        return 6;
    }
    if (strcmp(out, "lang\tund\n") != 0) {
        return 7;
    }
    free(out);
    return 0;
}
#endif
