package com.maswadkar.nback

/** High-priority Android coverage run on every PR; all other tests remain in the full suite. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CriticalCi
