package com.ecommerce.auth.dao;

import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.UserCredentials;

/**
 * Kontrak akses ke penyimpanan identitas — padanan {@code Repository} di Spring
 * Data JPA, hanya saja "database"-nya di sini adalah Keycloak.
 *
 * <p>
 * <strong>Kenapa bukan JPA langsung ke database keycloak?</strong> Password
 * di tabel {@code CREDENTIAL} disimpan sebagai hash PBKDF2 dengan salt per user
 * dan jumlah iterasi yang disimpan di kolom JSON; skema itu internal dan bisa
 * berubah antar versi Keycloak. Membacanya sendiri juga melewatkan password
 * policy, brute force detection, dan — yang paling penting — tidak menghasilkan
 * access token yang bisa diverifikasi service lain. Karena itu implementasi
 * konkretnya memanggil endpoint token Keycloak.
 *
 * <p>
 * Antarmuka ini yang membuat {@code AuthenticationService} bergantung pada
 * abstraksi, bukan pada Keycloak (Dependency Inversion Principle). Method-nya
 * sengaja cuma satu supaya kelas yang hanya butuh login tidak ikut terseret
 * operasi lain (Interface Segregation Principle) — kalau nanti perlu refresh
 * token atau logout, buat interface terpisah.
 *
 * @see com.mycompany.auth.dao.keycloak.KeycloakIdentityProviderDao
 */
public interface IdentityProviderDao {

    /**
     * Memverifikasi kredensial dan menerbitkan token.
     *
     * @param credentials username dan password milik pengguna
     * @return token yang diterbitkan identity provider
     * @throws AuthenticationException              kredensial ditolak
     * @throws IdentityProviderUnavailableException provider tidak bisa dihubungi
     */
    AuthToken authenticate(UserCredentials credentials);
}
