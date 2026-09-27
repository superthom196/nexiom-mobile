package io.github.superthom196.nexiom

/** "photo.jpg" as the `n`th of its name: "photo (2).jpg". A name without an extension gets it last. */
fun numbered(name: String, n: Int): String {
    if (n <= 1) return name
    val dot = name.lastIndexOf('.')
    return if (dot <= 0) "$name ($n)" else "${name.substring(0, dot)} ($n)${name.substring(dot)}"
}

/** `name`, or the first numbered name not in `taken`, so a file of the same name is kept too. */
fun freeName(name: String, taken: Set<String>): String =
    generateSequence(1) { it + 1 }.map { numbered(name, it) }.first { it !in taken }

private const val BAD_CHARS = "/\\:*?\"<>|"
private const val MAX_NAME_CHARS = 120

/**
 * A shared file's name as the box takes it: no / \ : * ? " < > | or control characters, not
 * starting or ending with a dot, and short enough for any filesystem.
 */
fun safeName(raw: String, fallback: String = "Shared file"): String {
    val cleaned = raw.map { if (it in BAD_CHARS || it.code < 32 || it.code == 127) '_' else it }
        .joinToString("")
        .trim()
        .trim('.')
        .trim()
    if (cleaned.isEmpty()) return fallback
    if (cleaned.length <= MAX_NAME_CHARS) return cleaned
    val dot = cleaned.lastIndexOf('.')
    val ext = if (dot > 0 && cleaned.length - dot <= 10) cleaned.substring(dot) else ""
    return cleaned.take(MAX_NAME_CHARS - ext.length).trimEnd('.', ' ') + ext
}
