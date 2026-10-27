package de.dahunni.myrsvp.book

/** Why a file could not be imported; the UI turns these into messages. */
enum class ImportError { Drm, NotAnEpub, Empty, Unsupported, Unreadable }

class ImportException(val error: ImportError, cause: Throwable? = null) : Exception(error.name, cause)
