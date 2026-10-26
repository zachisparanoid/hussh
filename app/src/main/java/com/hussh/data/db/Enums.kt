package com.hussh.data.db

/**
 * Connection protocols. Hussh is text-protocols-only by design — there is deliberately no
 * RDP/VNC/graphical option here or anywhere else in the codebase.
 */
enum class Protocol(val displayName: String, val defaultPort: Int) {
    SSH("SSH", 22),
    SFTP("SFTP", 22),
    TELNET("Telnet", 23),
    RAW("Raw TCP", 23);

    val isSsh: Boolean get() = this == SSH || this == SFTP
}

/** What the on-screen / hardware Backspace key sends. */
enum class BackspaceMode(val byte: Byte) {
    DEL(0x7f),  // ASCII DEL — the modern default
    BS(0x08);   // ASCII BS  — some legacy hosts expect this
}

/** Private-key algorithms Hussh can generate and import. */
enum class KeyType(val displayName: String) {
    ED25519("Ed25519"),
    RSA("RSA"),
    ECDSA("ECDSA");
}

/** A key-bar layout is either the app-wide default, or an override owned by one host. */
enum class KeyBarScope { GLOBAL, HOST }

/** SSH port-forwarding kinds. */
enum class ForwardType(val displayName: String) {
    LOCAL("Local (-L)"),
    REMOTE("Remote (-R)"),
    DYNAMIC("Dynamic SOCKS (-D)");
}

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }
