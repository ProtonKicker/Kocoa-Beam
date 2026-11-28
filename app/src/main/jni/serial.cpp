#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <malloc.h>
#include <asm-generic/fcntl.h>
#include <atomic>
#include <errno.h>
#include "pty/pty.h"

#define BUFFER_SIZE 4096
#define TAG "beam_serial"

jclass mProxyClass;
jmethodID mProxyOnDataReceivedMethod;

static JavaVM *staticVM = nullptr;

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

struct SerialPort {
    int master, slave;
    pthread_t worker_thread;
    std::atomic<bool> running;
    std::atomic<bool> worker_started;
    std::atomic<bool> thread_created;
    jweak proxy;
};

struct WorkerArgs {
    SerialPort* port;
    struct termios tio;
    int master, slave;
};

static void* worker(void* arguments) {
    WorkerArgs* args = (WorkerArgs*) arguments;
    SerialPort* port = args->port;

    fd_set read_fds, exception_fds;
    int pkt_mode = 1;

    char* buffer = (char*) malloc(BUFFER_SIZE);
    if (buffer == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to allocate read buffer");
        port->worker_started.store(true);
        delete args;
        return nullptr;
    }

    JavaVMAttachArgs vmArgs;
    vmArgs.name = (char*) "beam-serial-reader";
    vmArgs.group = nullptr;
    vmArgs.version = JNI_VERSION_1_6;

    JNIEnv* env = nullptr;
    bool attached = false;
    if (staticVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (staticVM->AttachCurrentThreadAsDaemon(&env, &vmArgs) == JNI_OK) {
            attached = true;
        } else {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to attach serial worker thread");
            free(buffer);
            port->worker_started.store(true);
            delete args;
            return nullptr;
        }
    }

    ioctl(args->master, TIOCPKT, &pkt_mode);

    tcgetattr(args->master, &args->tio);
    args->tio.c_lflag = 0;
    args->tio.c_cflag = 176;
    args->tio.c_iflag = 0;
    args->tio.c_oflag = 0;
    tcsetattr(args->master, TCSANOW, &args->tio);

    tcgetattr(args->slave, &args->tio);
    args->tio.c_lflag = 0;
    args->tio.c_cflag = 176;
    args->tio.c_iflag = 0;
    args->tio.c_oflag = 0;
    tcsetattr(args->slave, TCSANOW, &args->tio);

    port->worker_started.store(true);

    while (port->running.load()) {
        FD_ZERO(&read_fds);
        FD_SET(args->master, &read_fds);
        FD_ZERO(&exception_fds);
        FD_SET(args->master, &exception_fds);

        int code = select(args->master + 1, &read_fds, nullptr, &exception_fds, nullptr);
        if (!port->running.load()) {
            break;
        }
        if (code == -1) {
            if (errno == EINTR) {
                continue;
            }
            __android_log_print(ANDROID_LOG_ERROR, TAG, "select failed, errno=%d", errno);
            break;
        }

        int count = read(args->master, buffer, BUFFER_SIZE - 1);
        if (!port->running.load()) {
            break;
        }
        if (count < 0) {
            if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) {
                continue;
            }
            __android_log_print(ANDROID_LOG_ERROR, TAG, "read failed, errno=%d", errno);
            break;
        } else if (count > 1) {
            jobject proxy = env->NewLocalRef(port->proxy);
            if (proxy != nullptr) {
                jbyteArray arr = env->NewByteArray(count - 1);
                if (arr != nullptr) {
                    env->SetByteArrayRegion(arr, 0, count - 1, reinterpret_cast<const jbyte *>(buffer + 1));
                    env->CallVoidMethod(proxy, mProxyOnDataReceivedMethod, arr);
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                    }
                    env->DeleteLocalRef(arr);
                }
                env->DeleteLocalRef(proxy);
            }
        }
    }

    if (attached) {
        staticVM->DetachCurrentThread();
    }
    free(buffer);
    delete args;
    return nullptr;
}

extern "C" {
    int JNI_OnLoad(JavaVM *vm, void*) {
        JNIEnv *env;
        if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
            return JNI_ERR;
        }

        staticVM = vm;

        mProxyClass = env->FindClass("ru/ytkab0bp/beamklipper/serial/SerialProxy");
        mProxyOnDataReceivedMethod = env->GetMethodID(mProxyClass, "onDataReceived", "([B)V");

        return JNI_VERSION_1_6;
    }

    JNIEXPORT jlong JNICALL Java_ru_ytkab0bp_beamklipper_serial_SerialNative_create(JNIEnv *env, jclass, jstring file, jobject proxy) {
        SerialPort* port = new SerialPort();
        port->master = -1;
        port->slave = -1;
        port->running.store(false);
        port->worker_started.store(false);
        port->thread_created.store(false);
        port->proxy = nullptr;

        const char* chars = env->GetStringUTFChars(file, JNI_FALSE);
        if (chars == nullptr) {
            delete port;
            return 0;
        }

        WorkerArgs* args = new WorkerArgs();
        args->port = port;

        char name[256];
        unlink(chars);
        if (openpty(&args->master, &args->slave, name, &args->tio, nullptr) < 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "openpty failed for %s, errno=%d", chars, errno);
            env->ReleaseStringUTFChars(file, chars);
            delete args;
            delete port;
            return 0;
        }

        int flags = fcntl(args->master, F_GETFL);
        if (flags != -1) {
            fcntl(args->master, F_SETFL, flags | O_NONBLOCK);
        }

        fcntl(args->master, F_SETFD, FD_CLOEXEC);
        fcntl(args->slave, F_SETFD, FD_CLOEXEC);

        if (symlink(name, chars) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "symlink failed for %s -> %s, errno=%d", chars, name, errno);
            close(args->master);
            close(args->slave);
            env->ReleaseStringUTFChars(file, chars);
            delete args;
            delete port;
            return 0;
        }

        port->master = args->master;
        port->slave = args->slave;
        port->proxy = env->NewWeakGlobalRef(proxy);
        port->running.store(true);

        if (pthread_create(&port->worker_thread, nullptr, worker, args) != 0) {
            port->thread_created.store(false);
            __android_log_print(ANDROID_LOG_ERROR, TAG, "pthread_create failed for %s", chars);
            env->DeleteWeakGlobalRef(port->proxy);
            port->proxy = nullptr;
            unlink(chars);
            close(args->master);
            close(args->slave);
            env->ReleaseStringUTFChars(file, chars);
            delete args;
            delete port;
            return 0;
        }

        port->thread_created.store(true);
        env->ReleaseStringUTFChars(file, chars);
        return (jlong) (intptr_t) port;
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_beamklipper_serial_SerialNative_write(JNIEnv *env, jclass, jlong pointer, jbyteArray arr, jint len) {
        if (pointer == 0 || arr == nullptr || len <= 0) return;
        SerialPort* port = (SerialPort*) (intptr_t) pointer;
        if (!port->running.load()) return;
        jbyte* elements = env->GetByteArrayElements(arr, JNI_FALSE);
        if (elements == nullptr) return;
        pthread_mutex_lock(&mutex);
        if (port->master >= 0 && port->running.load()) {
            ssize_t written = ::write(port->master, elements, len);
            if (written < 0) {
                __android_log_print(ANDROID_LOG_ERROR, TAG, "write failed, errno=%d", errno);
            }
        }
        pthread_mutex_unlock(&mutex);
        env->ReleaseByteArrayElements(arr, elements, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_ru_ytkab0bp_beamklipper_serial_SerialNative_release(JNIEnv *env, jclass, jlong pointer) {
        if (pointer == 0) return;
        SerialPort* port = (SerialPort*) (intptr_t) pointer;
        port->running.store(false);

        int master = port->master;
        int slave = port->slave;
        port->master = -1;
        port->slave = -1;

        if (master >= 0) {
            close(master);
        }
        if (slave >= 0) {
            close(slave);
        }

        if (port->thread_created.load()) {
            pthread_join(port->worker_thread, nullptr);
        }

        if (port->proxy != nullptr) {
            env->DeleteWeakGlobalRef(port->proxy);
            port->proxy = nullptr;
        }

        delete port;
    }
}
