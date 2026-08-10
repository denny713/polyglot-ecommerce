package com.ecommerce.auth.exception;

/**
 * Kode error domain untuk kegagalan autentikasi.
 *
 * <p>
 * Layer domain sengaja tidak tahu apa-apa soal HTTP status. Pemetaan kode ini
 * ke status code dilakukan di
 * {@code com.mycompany.auth.exception.handler.AuthenticationExceptionMapper},
 * jadi menambah jenis kegagalan baru cukup dengan menambah satu konstanta di
 * sini plus satu baris di mapper — tanpa menyentuh controller atau service.
 */
public enum AuthErrorCode {

    /** Username atau password salah. */
    INVALID_CREDENTIALS,

    /** Akun terkunci sementara oleh brute force detection Keycloak. */
    ACCOUNT_LOCKED,

    /** Akun ada tapi dinonaktifkan, atau belum menyelesaikan required action. */
    ACCOUNT_DISABLED
}
