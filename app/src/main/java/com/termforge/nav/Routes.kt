package com.termforge.nav

/**
 * Type-safe-ish route table for the single-activity NavHost. Parameterized routes expose a
 * [build] helper so call sites never hand-concatenate paths.
 */
object Routes {
    const val HOSTS = "hosts"
    const val IDENTITIES = "identities"
    const val SNIPPETS = "snippets"
    const val SETTINGS = "settings"
    const val ACTIVE_SESSIONS = "active_sessions"

    object HostEdit {
        const val ARG_ID = "id"
        const val PATTERN = "host_edit?$ARG_ID={$ARG_ID}"

        /** @param id existing host id, or null to create a new host. */
        fun build(id: Long? = null): String =
            if (id == null) "host_edit" else "host_edit?$ARG_ID=$id"
    }

    object Terminal {
        const val ARG_SESSION = "sessionId"
        const val ARG_HOST = "hostId"
        const val PATTERN = "terminal/{$ARG_SESSION}?$ARG_HOST={$ARG_HOST}"

        /** @param hostId set for a NEW connection; -1 to bind to an already-running session. */
        fun build(sessionId: String, hostId: Long = -1L): String = "terminal/$sessionId?$ARG_HOST=$hostId"
    }

    object Sftp {
        const val ARG_HOST = "hostId"
        const val PATTERN = "sftp/{$ARG_HOST}"
        fun build(hostId: Long): String = "sftp/$hostId"
    }

    object KeyBarEditor {
        const val ARG_SCOPE = "scope"      // GLOBAL or HOST
        const val ARG_HOST_ID = "hostId"
        const val PATTERN = "keybar_editor?$ARG_SCOPE={$ARG_SCOPE}&$ARG_HOST_ID={$ARG_HOST_ID}"
        fun build(scope: String, hostId: Long? = null): String =
            "keybar_editor?$ARG_SCOPE=$scope&$ARG_HOST_ID=${hostId ?: -1L}"
    }
}
