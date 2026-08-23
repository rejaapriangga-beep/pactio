package com.keluargakendali.data

/** Kesalahan yang berasal dari pemanggilan API Pactio. Pesannya aman ditampilkan ke pengguna. */
sealed class ApiException(message: String) : Exception(message) {

    /** Token tidak valid/kedaluwarsa atau peran tidak diizinkan (HTTP 401). Wajib memicu logout. */
    class Unauthorized(message: String) : ApiException(message)

    /** Kesalahan HTTP lain yang dikembalikan backend beserta pesan dari field "error". */
    class Http(val code: Int, message: String) : ApiException(message)

    /** Tidak dapat terhubung ke server sama sekali (DNS, timeout, TLS, dll). */
    class Network(message: String) : ApiException(message)

    /** Respons server tidak berupa JSON yang valid/sesuai ekspektasi. */
    class InvalidResponse(message: String) : ApiException(message)
}
